package app.fen.mcp.config

enum class ClientKind(val label: String) {
    CLAUDE_CODE("Claude Code"),
    CURSOR("Cursor"),
    INSPECTOR("Inspector"),
    GENERIC_JSON("通用 JSON"),
    CLAUDE_DESKTOP("Claude Desktop"),
}

fun configText(kind: ClientKind, address: String, token: String): String {
    val url = "http://$address/mcp"
    return when (kind) {
        ClientKind.CLAUDE_CODE ->
            "claude mcp add --transport http fen $url --header \"Authorization: Bearer $token\""
        ClientKind.CURSOR, ClientKind.GENERIC_JSON -> """
            {
              "mcpServers": {
                "fen": {
                  "type": "http",
                  "url": "$url",
                  "headers": { "Authorization": "Bearer $token" }
                }
              }
            }
        """.trimIndent()
        ClientKind.INSPECTOR ->
            "npx @modelcontextprotocol/inspector --cli $url --transport http " +
                "--header \"Authorization: Bearer $token\" --method tools/list"
        ClientKind.CLAUDE_DESKTOP -> """
            {
              "mcpServers": {
                "fen": {
                  "command": "npx",
                  "args": [
                    "mcp-remote",
                    "$url",
                    "--header",
                    "Authorization: Bearer $token",
                    "--allow-http"
                  ]
                }
              }
            }
        """.trimIndent()
    }
}

fun aiSetupPrompt(kind: ClientKind, address: String, token: String): String =
    "请帮我把 Fen 本地 MCP 服务加入你的 MCP 配置。\n" +
        "地址：http://$address/mcp\n" +
        "认证：Authorization: Bearer $token\n" +
        "客户端配置（${kind.label}）：\n" +
        configText(kind, address, token) +
        "\n配置完成后调用 get_overview，把返回的数据范围和当前月收支贴给我。"
