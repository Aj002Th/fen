param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Token
)

$ErrorActionPreference = "Stop"
$endpoint = if ($BaseUrl.EndsWith("/mcp")) { $BaseUrl } else { "$BaseUrl/mcp" }
$auth = @{ Authorization = "Bearer $Token" }

function Invoke-Mcp($body, $headers = @{}) {
    $all = @{
        Authorization = "Bearer $Token"
        Accept = "application/json, text/event-stream"
        "Content-Type" = "application/json"
    }
    foreach ($key in $headers.Keys) { $all[$key] = $headers[$key] }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $endpoint -Method Post -Headers $all -Body $body
        $sw.Stop()
        return [pscustomobject]@{
            Status = $response.StatusCode
            Body = $response.Content
            Headers = $response.Headers
            Ms = $sw.ElapsedMilliseconds
        }
    } catch {
        $sw.Stop()
        $status = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        return [pscustomobject]@{ Status = $status; Body = $_.Exception.Message; Headers = @{}; Ms = $sw.ElapsedMilliseconds }
    }
}

Write-Host "Endpoint: $endpoint"

$init = Invoke-Mcp '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}'
$session = $init.Headers["Mcp-Session-Id"]
Write-Host ("legacy initialize: {0} ({1}ms) session={2}" -f $init.Status, $init.Ms, $session)

$legacyHeaders = @{}
if ($session) { $legacyHeaders["Mcp-Session-Id"] = $session }
$legacyList = Invoke-Mcp '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' $legacyHeaders
Write-Host ("legacy tools/list: {0} ({1}ms)" -f $legacyList.Status, $legacyList.Ms)

$modern = Invoke-Mcp '{"jsonrpc":"2.0","id":3,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}' @{
    "MCP-Protocol-Version" = "2026-07-28"
    "Mcp-Method" = "server/discover"
}
Write-Host ("modern server/discover: {0} ({1}ms)" -f $modern.Status, $modern.Ms)

$overview = Invoke-Mcp '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_overview","arguments":{},"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}' @{
    "MCP-Protocol-Version" = "2026-07-28"
    "Mcp-Method" = "tools/call"
    "Mcp-Name" = "get_overview"
}
Write-Host ("modern get_overview: {0} ({1}ms)" -f $overview.Status, $overview.Ms)
Write-Host $overview.Body

if ($overview.Status -ne 200 -or $modern.Status -ne 200) {
    exit 1
}
Write-Host "smoke ok"
