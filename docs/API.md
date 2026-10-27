# API 文档

> 阶段 2 快照：6 个业务工具已可用，鉴权 / 限流 / 审计在阶段 3 接入。

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
curl -X POST http://localhost:8082/mcp   -H 'Content-Type: application/json'   -H 'Accept: application/json, text/event-stream'   -H "Mcp-Session-Id: <session>"   -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
        "name":"check_inventory","arguments":{"skuId":"SKU00001"}}}'
```

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [{"type": "text", "text": "{\"skuId\":\"SKU00001\",\"warehouseId\":\"WH-BJ\",\"availableQty\":662,\"reservedQty\":4}"}],
    "isError": false
  }
}
```

工具返回值统一序列化为 JSON 文本放入 `content[0].text`；业务校验失败时 `isError` 为 `true`，`text` 为中文错误说明。

## 工具清单

| 工具 | 权限 | 输入 | 输出 |
|------|------|------|------|
| `query_orders` | READ | `userId` / `status?` / `limit?` | `OrderView[]` |
| `get_order_detail` | READ | `orderId` | `{order, items}` |
| `check_inventory` | READ | `skuId` / `warehouseId?` | `InventoryInfo` |
| `search_knowledge` | READ | `query` / `category?` | `KnowledgeDocument[]` |
| `update_order_status` | WRITE | `orderId` / `newStatus` / `remark?` | `UpdateResult` |
| `lock_inventory` | WRITE | `skuId` / `quantity` / `orderId` | `LockResult` |

参数名由 MCP 注解扫描器从**方法形参名**生成（camelCase），调用时按 schema 里的名字传参；`required = false` 的参数可省略。

### 参数校验与错误

| 场景 | 返回 |
|------|------|
| 缺失必填参数 / 空字符串 | `isError: true`，如 `userId 不能为空` |
| `limit` 超出 1-100 | `limit 取值范围 1-100` |
| 非法订单状态 | `非法订单状态：X，可选值：PENDING/PAID/SHIPPED/COMPLETED` |
| 订单 / 库存不存在 | `订单不存在：X` / `库存记录不存在：sku=X` |
| 库存不足 | `库存不足：sku=X 可用 N，请求 M` |
| 重复锁定同一订单 + SKU | 幂等成功，返回 `idempotent: true` 且不重复扣减 |

### 订单状态

`PENDING` → `PAID` → `SHIPPED` → `COMPLETED`。阶段 2 不限制流转方向（阶段 3 起由治理层补齐业务规则），非法状态名直接拒绝。

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
