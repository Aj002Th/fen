package app.fen.mcp

import app.fen.mcp.auth.TokenAuth
import app.fen.mcp.net.RateLimiter
import app.fen.mcp.net.Rejection
import app.fen.mcp.net.validateHeaders
import app.fen.mcp.net.validateModernHeaders
import app.fen.mcp.protocol.MODERN_PROTOCOL_VERSION
import app.fen.mcp.protocol.ProtocolEra
import app.fen.mcp.protocol.RequestHeaders
import app.fen.mcp.protocol.RpcCall
import app.fen.mcp.protocol.RpcCode
import app.fen.mcp.protocol.RpcParse
import app.fen.mcp.protocol.SUPPORTED_PROTOCOL_VERSIONS
import app.fen.mcp.protocol.detectEra
import app.fen.mcp.protocol.parseModernMeta
import app.fen.mcp.protocol.parseRpc
import app.fen.mcp.protocol.rpcError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class McpServer(
    private val port: Int,
    private val token: String,
    private val handler: RpcHandler,
    private val allowedHosts: () -> Set<String>,
    private val host: String = "0.0.0.0",
    private val deleteSession: (String) -> Boolean = { false },
) {

    private val rateLimiter = RateLimiter(System::currentTimeMillis)
    private var stopServer: (() -> Unit)? = null

    fun start() {
        val server = embeddedServer(CIO, host = host, port = port) {
            routing {
                post("/mcp") { handlePost(call) }
                get("/mcp") { call.respond(HttpStatusCode.MethodNotAllowed) }
                delete("/mcp") { handleDelete(call) }
            }
        }
        stopServer = { server.stop(0, 1_000) }
        server.start(wait = false)
    }

    fun stop() {
        stopServer?.invoke()
        stopServer = null
    }

    private suspend fun handlePost(call: ApplicationCall) {
        val headers = call.toRequestHeaders()
        val clientIp = call.request.local.remoteHost

        validateHeaders(headers, allowedHosts())?.let { rejection ->
            respondRejection(call, rejection)
            return
        }
        if (!rateLimiter.allow(clientIp)) {
            call.response.header(HttpHeaders.RetryAfter, "60")
            call.respondText(
                rpcError(null, RpcCode.INVALID_REQUEST, "Rate limit exceeded").toString(),
                ContentType.Application.Json,
                HttpStatusCode.TooManyRequests,
            )
            return
        }

        val contentType = call.request.contentType().withoutParameters()
        if (contentType != ContentType.Application.Json) {
            call.respondText(
                rpcError(null, RpcCode.INVALID_REQUEST, "Content-Type must be application/json").toString(),
                ContentType.Application.Json,
                HttpStatusCode.UnsupportedMediaType,
            )
            return
        }

        val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            call.respondText(
                rpcError(null, RpcCode.INVALID_REQUEST, "Request body too large").toString(),
                ContentType.Application.Json,
                HttpStatusCode.PayloadTooLarge,
            )
            return
        }
        val body = call.receiveText()
        if (body.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
            call.respondText(
                rpcError(null, RpcCode.INVALID_REQUEST, "Request body too large").toString(),
                ContentType.Application.Json,
                HttpStatusCode.PayloadTooLarge,
            )
            return
        }

        val presented = TokenAuth.parseBearer(headers.authorization)
        if (!TokenAuth.matches(token, presented)) {
            rateLimiter.recordAuthFailure(clientIp)
            call.response.header(HttpHeaders.WWWAuthenticate, """Bearer realm="fen"""")
            call.respondText(
                rpcError(null, RpcCode.INVALID_REQUEST, "Unauthorized").toString(),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized,
            )
            return
        }
        rateLimiter.recordSuccess(clientIp)

        val parsed = parseRpc(body)
        if (parsed is RpcParse.Error) {
            call.respondText(
                rpcError(null, parsed.code, parsed.message).toString(),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return
        }
        val rpc = (parsed as RpcParse.Ok).call
        val era = detectEra(rpc, headers)
        if (era == null) {
            call.respondText(
                rpcError(rpc.id, RpcCode.INVALID_REQUEST, "Missing protocol version").toString(),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return
        }

        if (era == ProtocolEra.MODERN_2026_07_28) {
            validateModernHeaders(headers, rpc)?.let { rejection ->
                respondRejection(call, rejection, rpc.id)
                return
            }
            val meta = parseModernMeta(rpc.params)
            if (meta != null && meta.protocolVersion != MODERN_PROTOCOL_VERSION) {
                val data = buildJsonObject {
                    put(
                        "supported",
                        buildJsonArray { SUPPORTED_PROTOCOL_VERSIONS.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
                    )
                    put("requested", meta.protocolVersion)
                }
                call.respondText(
                    rpcError(rpc.id, RpcCode.UNSUPPORTED_VERSION, "Unsupported protocol version", data).toString(),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return
            }
        }

        val context = RequestContext(
            headers = headers,
            clientIp = clientIp,
            era = era,
            meta = parseModernMeta(rpc.params),
            sessionId = headers.sessionId,
        )
        val outcome = try {
            handler.handle(rpc, context)
        } catch (e: Exception) {
            RpcOutcome.HttpError(
                HttpStatusCode.InternalServerError.value,
                rpcError(rpc.id, RpcCode.INTERNAL, "Internal error").toString(),
            )
        }
        respondOutcome(call, outcome, headers, rpc.isNotification)
    }

    private suspend fun handleDelete(call: ApplicationCall) {
        val sessionId = call.request.header("Mcp-Session-Id")
        if (sessionId.isNullOrBlank() || !deleteSession(sessionId)) {
            call.respond(HttpStatusCode.MethodNotAllowed)
            return
        }
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun respondRejection(
        call: ApplicationCall,
        rejection: Rejection,
        id: kotlinx.serialization.json.JsonElement? = null,
    ) {
        val body = rpcError(id, rejection.code ?: RpcCode.INVALID_REQUEST, rejection.message).toString()
        call.respondText(
            body,
            ContentType.Application.Json,
            HttpStatusCode.fromValue(rejection.status),
        )
    }

    private suspend fun respondOutcome(
        call: ApplicationCall,
        outcome: RpcOutcome,
        headers: RequestHeaders,
        notification: Boolean,
    ) {
        if (notification) {
            call.respond(HttpStatusCode.Accepted)
            return
        }
        when (outcome) {
            is RpcOutcome.Json -> {
                outcome.headers.forEach { (name, value) -> call.response.header(name, value) }
                val accept = headers.accept.orEmpty()
                val wantsSse = accept.contains("text/event-stream", ignoreCase = true)
                val wantsJson = accept.contains("application/json", ignoreCase = true)
                if (wantsSse && !wantsJson) {
                    call.respondText(
                        "data: ${outcome.body}\n\n",
                        ContentType.Text.EventStream,
                        HttpStatusCode.fromValue(outcome.status),
                    )
                } else {
                    call.respondText(
                        outcome.body,
                        ContentType.Application.Json,
                        HttpStatusCode.fromValue(outcome.status),
                    )
                }
            }

            RpcOutcome.Accepted -> call.respond(HttpStatusCode.Accepted)
            is RpcOutcome.HttpError -> call.respondText(
                outcome.body,
                ContentType.Application.Json,
                HttpStatusCode.fromValue(outcome.status),
            )
        }
    }

    private fun ApplicationCall.toRequestHeaders(): RequestHeaders = RequestHeaders(
        host = request.header(HttpHeaders.Host),
        origin = request.header(HttpHeaders.Origin),
        authorization = request.header(HttpHeaders.Authorization),
        contentType = request.header(HttpHeaders.ContentType),
        accept = request.header(HttpHeaders.Accept),
        protocolVersion = request.header("MCP-Protocol-Version"),
        mcpMethod = request.header("Mcp-Method"),
        mcpName = request.header("Mcp-Name"),
        sessionId = request.header("Mcp-Session-Id"),
    )

    private companion object {
        const val MAX_BODY_BYTES = 256 * 1024
    }
}
