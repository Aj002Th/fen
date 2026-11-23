package app.fen.mcp.config

fun setupInstruction(address: String, token: String): String = """
    请在当前 AI 客户端添加 MCP 服务器（按该客户端的配置格式写入）：
    名称：fen
    类型：Streamable HTTP
    地址：http://$address/mcp
    请求头：Authorization: Bearer $token
    不支持 HTTP 类型时改用 stdio（需 Node.js）：
    {
      "mcpServers": {
        "fen": {
          "command": "npx",
          "args": [
            "-y",
            "mcp-remote",
            "http://$address/mcp",
            "--allow-http",
            "--header",
            "Authorization:Bearer $token"
          ]
        }
      }
    }
    完成后调用 get_overview 测试。
""".trimIndent()
