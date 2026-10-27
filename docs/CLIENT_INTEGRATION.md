# 客户端接入指南

本文档说明如何用自研 Java Client、MCP Inspector 和 curl 接入 MCPort，并给出阶段 4 的实测结果。

## 准备工作

1. 启动中间件与服务（见 [README](../README.md)）
2. 确认健康检查：`curl -s http://localhost:8082/actuator/health`
3. 准备 API Key（种子数据见 `docker/init/03-api-keys.sql`）

| Key | 用户 | 权限 | 限流 | 用途 |
|-----|------|------|------|------|
| `test-key-read-only` | `user0` | READ | 60/min | 只读工具、越权验证 |
| `test-key-admin` | `admin` | READ + WRITE | 100/min | 全部工具 |
| `test-key-disabled` | `user1` | — | 60/min | 验证 403（`enabled = 0`） |
| `test-key-tight` | `user0` | READ | 3/min | 快速验证 429 |

## 自研 Java Client

模块 `mcport-client-demo`，基于官方 SDK `io.modelcontextprotocol.sdk:mcp:0.18.3`（与 `spring-ai-mcp 1.1.8` 的传递依赖一致）。

```text
mcport-client-demo/src/main/java/com/mcport/client/
├── McpGatewayClient.java   # 入口：握手 → 6 个工具 → 治理拦截
├── Session.java            # 一条 API Key 会话：传输层注入 Authorization
├── ToolCall.java           # 一次调用的结果：延迟 / isError / HTTP 状态码
└── Report.java             # 控制台报告，有失败项时退出码 1
```

运行：

```bash
./mvnw -pl mcport-client-demo exec:java
```

缺省连 `http://localhost:8082/mcp`，也可传参：

```bash
./mvnw -pl mcport-client-demo exec:java -Dexec.args="http://localhost:8082/mcp"
```

### 关键实现点

**鉴权头注入**：MCP SDK 不会把自定义请求头带进协议方法，必须在传输层注入。

```java
HttpClientStreamableHttpTransport.builder(endpoint)
        .customizeRequest(request -> request.header("Authorization", "Bearer " + apiKey))
        .connectTimeout(Duration.ofSeconds(5))
        .build();
McpSyncClient client = McpClient.sync(transport)
        .clientInfo(new McpSchema.Implementation("mcport-java-client", "1.0.0"))
        .requestTimeout(Duration.ofSeconds(15))
        .build();
client.initialize();
```

**两类失败要分开处理**：

| 失败类型 | 表现 | 客户端处理 |
|----------|------|-----------|
| 工具内业务错误（参数、越权、库存不足） | `result.isError() == true`，`content[0].text` 为错误说明 | 正常返回，读文本即可 |
| HTTP 层拦截（401 / 403 / 429） | SDK 抛 `RuntimeException`，`message` 里带服务端错误体 | 解析 `error` 字段判定状态码 |

```java
try {
    McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    return Boolean.TRUE.equals(result.isError()) ? "工具错误" : result.content();
} catch (RuntimeException e) {
    // message 形如 ...data={"error":"Forbidden","message":"..."}
    return statusOf(e.getMessage());
}
```

SDK 把状态码本身吞掉了，异常信息里只有响应体；因此客户端按错误体中的 `error` 取值（`Unauthorized` / `Forbidden` / `Rate Limit Exceeded`）判定，不要按状态码字符串匹配。

### 实测输出

```text
MCPort client demo → http://localhost:8082/mcp

== 握手与 tools/list =========================================
  PASS  initialize 返回服务端信息      mcport-server 0.1.0 protocol=2025-11-25
  PASS  tools/list 返回 6 个工具       [check_inventory, get_order_detail, lock_inventory, query_orders, search_knowledge, update_order_status]
  PASS  工具名与文档一致               [check_inventory, get_order_detail, lock_inventory, query_orders, search_knowledge, update_order_status]

== 6 个工具调用 ==============================================
  PASS  query_orders 返回列表          ok 35ms | [{"orderId":"ORD_user0_003",...
  PASS  get_order_detail 含明细        ok 11ms | {"order":{"orderId":"ORD_user0_001",...
  PASS  check_inventory 返回可用库存   ok 8ms  | {"skuId":"SKU00001","warehouseId":"WH-BJ","availableQty":657,"reservedQty":9}
  PASS  search_knowledge 命中文档      ok 5ms  | [{"id":"doc-003","title":"退款与售后时效",...
  PASS  lock_inventory 重复调用不重复扣减 ok 11ms | {"success":true,...,"idempotent":true}
  PASS  update_order_status 成功       ok 11ms | {"success":true,"orderId":"ORD_user0_002",...}

== 治理层拦截 ================================================
  PASS  无 API Key → 401               HTTP 401 0ms
  PASS  已禁用 Key → 403               API Key 已禁用
  PASS  READ Key 调 WRITE 工具 → 403    HTTP 403 3ms
  PASS  READ Key 查他人订单 → 工具错误    isError 5ms | 无权查询其他用户的订单
  PASS  READ Key 查自己订单 → 成功        ok 9ms
  PASS  超出限流阈值 → 429              第 4 次调用 | HTTP 429 4ms

Summary: 16 passed, 0 failed
```

延迟为本机 Docker + 单实例服务端的一次调用耗时（含 SDK 序列化），不代表吞吐能力。

## MCP Inspector（可选，阶段 4 未执行）

MCP Inspector 是官方 Web 调试工具，可直接观察请求/响应 JSON。

> 本阶段已明确跳过浏览器手工验证，配置步骤保留备查；协议正确性由 Java Client 与 curl 覆盖。

```bash
npx @modelcontextprotocol/inspector
```

浏览器打开终端提示的地址（默认 `http://localhost:6274`），按下表配置：

| 字段 | 值 |
|------|-----|
| Transport Type | `Streamable HTTP` |
| URL | `http://localhost:8082/mcp` |
| Authentication → Custom Headers | `Authorization: Bearer test-key-admin` |

连接后依次验证：

1. `Tools` 标签能看到 6 个工具，点开可见 `inputSchema` 与 `required` 列表
2. `query_orders` 传 `{"userId":"user0","limit":3}`，查看 `content[0].text`
3. `check_inventory` 传 `{"skuId":"SKU00001"}`
4. `update_order_status` 传 `{"orderId":"ORD_user0_002","newStatus":"PAID"}`（需 admin Key）

注意：Inspector 连接时若不带 Authorization 头，会在握手阶段直接收到 401。

## curl

不依赖 SDK 的最小验证，覆盖握手、工具列表、未知方法与错误会话。

```bash
BASE=http://localhost:8082/mcp
KEY=test-key-admin

# 1. 握手，从响应头取 Mcp-Session-Id
curl -s -D - -o /dev/null -X POST "$BASE" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Authorization: Bearer $KEY" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{
        "protocolVersion":"2025-03-26","capabilities":{},
        "clientInfo":{"name":"curl","version":"1.0"}}}'
```

```bash
SID=<上一步响应头里的 Mcp-Session-Id>

# 2. 完成初始化通知（返回 202）
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Authorization: Bearer $KEY" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

```bash
# 3. 列出工具
curl -s -X POST "$BASE" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Authorization: Bearer $KEY" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

含中文参数时用文件避免编码问题：

```bash
printf '%s' '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_knowledge","arguments":{"query":"退货"}}}' > /tmp/call.json
curl -s -X POST "$BASE" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Authorization: Bearer $KEY" -H "Mcp-Session-Id: $SID" \
  --data-binary @/tmp/call.json
```

### 协议行为实测

| 场景 | 结果 |
|------|------|
| `initialize` | 200，响应头带 `Mcp-Session-Id` |
| `notifications/initialized` | 202 |
| `tools/list` | SSE 帧，`data` 内含 6 个工具 |
| 未知方法 `tools/nope` | SSE 帧内 JSON-RPC 错误 `-32601 Method not found` |
| 无效 `Mcp-Session-Id` | 404 |
| 缺少 `Authorization` | 401 JSON |
| 超过 Key 的每分钟额度 | 429 JSON |
| 工具执行 | SSE 帧正常返回；工具内错误为 `isError:true`，HTTP 仍 200 |

服务端会把协议版本协商到 `2025-11-25`（SDK 0.18.3 支持的版本），客户端传 `2025-03-26` 亦可，无需写死。

## 实测数据

以下数据采自本机（Windows 11 / MySQL 与 Redis 在 Docker / 服务端单实例）。

### 调用延迟

服务端审计记录（`t_audit_log`，SUCCESS + ERROR 共 46 条），延迟在切面内计时，不含网络与序列化：

| 工具 | 调用次数 | 平均耗时 | 最大耗时 |
|------|---------:|---------:|---------:|
| `check_inventory` | 15 | 3.3 ms | 5 ms |
| `get_order_detail` | 10 | 5.7 ms | 10 ms |
| `query_orders` | 9 | 3.8 ms | 10 ms |
| `lock_inventory` | 6 | 10.3 ms | 30 ms |
| `search_knowledge` | 3 | 0.3 ms | 1 ms |
| `update_order_status` | 3 | 11.0 ms | 21 ms |

全量 SUCCESS 记录 41 条，平均 5.66 ms，最大 30 ms。客户端侧（`mcport-client-demo` 打印的延迟）比上表高 3-10 ms，差值为 HTTP、SSE 解析与 SDK 反序列化开销。

### 拦截与审计

| 状态 | block_reason | 条数 | 平均耗时 |
|------|--------------|-----:|---------:|
| SUCCESS | — | 41 | 5.66 ms |
| BLOCKED | RATE_LIMIT | 9 | 0.11 ms |
| BLOCKED | FORBIDDEN | 4 | 0 ms |
| ERROR | — | 5 | 1.0 ms |

被拦截的调用在进入工具前就返回，延迟远低于正常调用，符合预期。

Prometheus 快照：

```text
mcp_tool_calls_total{status="success",tool="check_inventory"} 14.0
mcp_tool_calls_total{status="blocked",tool="check_inventory"} 5.0
mcp_tool_calls_total{status="error",tool="query_orders"} 4.0
mcp_blocks_total{reason="rate_limit"} 5.0
mcp_blocks_total{reason="forbidden"} 3.0
mcp_tool_latency_seconds_max{tool="lock_inventory"} 0.006
```

指标与审计表的计数差异来自进程重启与采样时刻不同，不要求逐条对齐。

### 复现方式

```bash
./mvnw -pl mcport-client-demo exec:java
curl -s http://localhost:8082/actuator/prometheus | grep mcp_
docker exec -e MYSQL_PWD=$MYSQL_ROOT_PASSWORD mcport-mysql mysql -uroot -e \
  "SELECT status, block_reason, COUNT(*) FROM mcport_db.t_audit_log GROUP BY 1,2"
```

## 已知限制

- 限流为每分钟固定窗口，跨窗口边界可能出现双倍瞬时流量
- Redis 不可用时限流 fail-open，只记录日志
- 越权校验发生在工具内，客户端看到的是 `isError:true`，不是 HTTP 403
- 审计异步落库，调用返回时可能尚未写入
- MCP Inspector 需在浏览器中手工操作，本文档只给出配置步骤；自动化验证以 Java Client 与 curl 为准
