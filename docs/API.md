# API 文档

> 阶段 3 快照：6 个业务工具 + 治理层（鉴权 / 权限 / 限流 / 审计 / 越权）已生效。

## MCP Server 端点

**基础路径**：`POST /mcp`（Streamable HTTP，端口由 `MCPORT_SERVER_PORT` 决定，本机默认 `8082`）

**请求头**：

```text
Content-Type: application/json
Accept: application/json, text/event-stream
Authorization: Bearer <api_key>              # 除 /actuator/* 外必须携带
Mcp-Session-Id: <initialize 返回的会话 ID>   # initialize 之后的所有请求
```

响应为 SSE 帧（`event: message` + `data: {...}`），`initialize` 的响应直接是 JSON。

### API Key

本地测试数据见 `docker/init/03-api-keys.sql`：

| Key | 用户 | 权限 | 限流 | 说明 |
|-----|------|------|------|------|
| `test-key-read-only` | `user0` | READ | 60/min | 只读，只能查询 `user0` 自己的订单 |
| `test-key-admin` | `admin` | READ + WRITE | 100/min | 可调用写入工具，可访问任意用户数据 |
| `test-key-disabled` | `user1` | READ | 60/min | `enabled = 0`，鉴权直接 403 |

鉴权流程：`Authorization` 取 Bearer → Redis `api_key:{key}` 命中则用缓存（TTL 1 小时）→ 未命中查 `t_api_key` 并回填 → 注入 `ApiKeyContext`（ThreadLocal，请求结束清理）。

### 握手

```bash
curl -i -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer test-key-read-only' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{
        "protocolVersion":"2025-03-26","capabilities":{},
        "clientInfo":{"name":"curl","version":"1.0"}}}'
```

响应头带 `Mcp-Session-Id`，后续请求必须回传。之后发一条 `notifications/initialized` 通知：

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer test-key-read-only' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

### tools/list

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer test-key-read-only' \
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
        "name": "query_orders",
        "title": "query_orders",
        "description": "按用户查询订单列表，可按状态过滤",
        "inputSchema": {
          "type": "object",
          "properties": {
            "userId": {"type": "string", "description": "用户 ID"},
            "status": {"type": "string", "description": "订单状态，可选：PENDING/PAID/SHIPPED/COMPLETED"},
            "limit": {"type": "integer", "description": "返回条数，1-100，默认 20"}
          },
          "required": ["userId"]
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
  -H 'Authorization: Bearer test-key-read-only' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
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

| 工具 | 权限 | 输入 | 输出 | 越权检查 |
|------|------|------|------|----------|
| `query_orders` | READ | `userId` / `status?` / `limit?` | `OrderView[]` | 非 admin 只能查自己 |
| `get_order_detail` | READ | `orderId` | `{order, items}` | 非 admin 只能查自己的订单 |
| `check_inventory` | READ | `skuId` / `warehouseId?` | `InventoryInfo` | — |
| `search_knowledge` | READ | `query` / `category?` | `KnowledgeDocument[]` | — |
| `update_order_status` | WRITE | `orderId` / `newStatus` / `remark?` | `UpdateResult` | 非 admin 只能改自己的订单 |
| `lock_inventory` | WRITE | `skuId` / `quantity` / `orderId` | `LockResult` | — |

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
| 越权（查 / 改他人订单） | `isError: true`，`无权访问其他用户的订单` |

### 订单状态

`PENDING` → `PAID` → `SHIPPED` → `COMPLETED`。当前不限制流转方向，非法状态名直接拒绝。

## HTTP 错误码

鉴权、权限、限流在 HTTP 层拦截（`ApiKeyAuthFilter` → `McpToolGuardFilter`），返回真实状态码；越权校验发生在工具方法内，由 MCP SDK 包成 `isError: true` 的工具结果，HTTP 仍为 200。

| 状态码 | 场景 | 响应体 | 拦截位置 |
|--------|------|--------|----------|
| 401 | 缺少或无效 API Key | `{"error":"Unauthorized","message":"..."}` | `ApiKeyAuthFilter` |
| 403 | API Key 已禁用 / 权限不足（READ 调 WRITE 工具） | `{"error":"Forbidden","message":"..."}` | `ApiKeyAuthFilter` / `McpToolGuardFilter` |
| 429 | 超过该 Key 每分钟调用上限 | `{"error":"Rate Limit Exceeded","message":"..."}` | `McpToolGuardFilter` |

限流按 `limit:{apiKey}:{分钟}` 计数（Redis，窗口 70 秒），Redis 不可用时 fail-open 放行。

## 审计与指标

每次工具调用异步写 `t_audit_log`：`SUCCESS` / `ERROR` / `BLOCKED`，含 `request_id`、`tool_name`、`api_key`、`user_id`、`input_params`、`output_summary`、`error_message`、`block_reason`、`latency_ms`。

```sql
SELECT tool_name, status, block_reason, latency_ms, created_at
  FROM mcport_db.t_audit_log ORDER BY created_at DESC LIMIT 20;
```

Prometheus 指标（`GET /actuator/prometheus`）：

| 指标 | 标签 |
|------|------|
| `mcp_tool_calls_total` | `tool`、`status`（success / error / blocked） |
| `mcp_blocks_total` | `reason`（rate_limit / forbidden / unauthorized_access） |
| `mcp_tool_latency_seconds` | `tool` |

## 运维端点

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 健康检查，正常返回 `{"status":"UP"}` |
| `GET /actuator/prometheus` | Prometheus 指标，含 `mcp_*` 自定义指标 |
