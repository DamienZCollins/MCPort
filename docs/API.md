# API 文档

> 阶段 1 快照：仅含 MCP Server 集成验证工具 `hello`，业务工具在阶段 2 补齐。

## MCP Server 端点

**基础路径**：`POST /mcp`（Streamable HTTP，端口由 `MCPORT_SERVER_PORT` 决定，本机默认 `8082`）

**请求头**：

```text
Content-Type: application/json
Accept: application/json, text/event-stream
Authorization: Bearer <api_key>      # 阶段 3 起强制
Mcp-Session-Id: <initialize 返回的会话 ID>   # initialize 之后的所有请求
```

响应为 SSE 帧（`event: message` + `data: {...}`），`initialize` 的响应直接是 JSON。

### 握手

```bash
curl -i -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{
        "protocolVersion":"2025-03-26","capabilities":{},
        "clientInfo":{"name":"curl","version":"1.0"}}}'
```

响应头带 `Mcp-Session-Id`，后续请求必须回传。之后发一条 `notifications/initialized` 通知：

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

### tools/list

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "hello",
        "title": "hello",
        "description": "测试工具：返回问候语",
        "inputSchema": {
          "type": "object",
          "properties": {"name": {"type": "string", "description": "名字"}},
          "required": ["name"]
        }
      }
    ]
  }
}
```

### tools/call

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
        "name":"hello","arguments":{"name":"World"}}}'
```

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [{"type": "text", "text": "Hello, World"}],
    "isError": false
  }
}
```

## 工具清单

| 工具 | 权限 | 状态 |
|------|------|------|
| `hello` | — | 阶段 1 集成验证用，阶段 2 移除 |
| `query_orders` / `check_inventory` / `get_order_detail` / `search_knowledge` | READ | 阶段 2 |
| `update_order_status` / `lock_inventory` | WRITE | 阶段 2 |

## HTTP 错误码（阶段 3 起生效）

| 状态码 | 场景 | 响应体 |
|--------|------|--------|
| 401 | 缺少或无效 API Key | `{"error":"Unauthorized","message":"Invalid API Key"}` |
| 403 | 权限不足 / 越权访问 | `{"error":"Forbidden","message":"..."}` |
| 429 | 超过每分钟调用限制 | `{"error":"Rate Limit Exceeded","message":"..."}` |

## 运维端点

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 健康检查，正常返回 `{"status":"UP"}` |
| `GET /actuator/prometheus` | Prometheus 指标（阶段 3 起含 `mcp_*` 自定义指标） |
