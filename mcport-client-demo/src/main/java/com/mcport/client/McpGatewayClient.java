package com.mcport.client;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自研 MCP Client：验证 Streamable HTTP 协议、6 个工具与治理层行为。
 *
 * <p>用法：
 * <pre>
 * mvn -pl mcport-client-demo exec:java
 * mvn -pl mcport-client-demo exec:java -Dexec.args="http://localhost:8082/mcp"
 * </pre>
 * 服务端需先启动。出现失败项时以退出码 1 结束，便于脚本判断。
 */
public final class McpGatewayClient {

    private static final String DEFAULT_ENDPOINT = "http://localhost:8082/mcp";
    private static final String ADMIN_KEY = "test-key-admin";
    private static final String READ_KEY = "test-key-read-only";
    private static final String DISABLED_KEY = "test-key-disabled";
    private static final String TIGHT_KEY = "test-key-tight";

    private static final List<String> EXPECTED_TOOLS = List.of(
            "query_orders", "get_order_detail", "check_inventory",
            "search_knowledge", "update_order_status", "lock_inventory");

    public static void main(String[] args) {
        String endpoint = args.length > 0 ? args[0] : DEFAULT_ENDPOINT;
        Report report = new Report();
        System.out.println("MCPort client demo → " + endpoint);

        handshake(endpoint, report);
        toolsAndCalls(endpoint, report);
        governance(endpoint, report);

        System.exit(report.summary());
    }

    private static void handshake(String endpoint, Report report) {
        report.section("握手与 tools/list");
        try (Session session = Session.open(endpoint, ADMIN_KEY)) {
            McpSchema.Implementation server = session.serverInfo();
            report.check("initialize 返回服务端信息", server != null && server.name() != null,
                    server == null ? "无 serverInfo"
                            : server.name() + " " + server.version()
                                    + " protocol=" + session.protocolVersion());

            List<McpSchema.Tool> tools = session.listTools();
            List<String> names = tools.stream().map(McpSchema.Tool::name).sorted().toList();
            report.check("tools/list 返回 6 个工具", tools.size() == 6, names.toString());
            report.check("工具名与文档一致", names.equals(EXPECTED_TOOLS.stream().sorted().toList()), names.toString());
        } catch (RuntimeException e) {
            report.fail("initialize", e.getMessage());
        }
    }

    private static void toolsAndCalls(String endpoint, Report report) {
        report.section("6 个工具调用");
        try (Session session = Session.open(endpoint, ADMIN_KEY)) {
            ToolCall orders = session.call("query_orders", Map.of("userId", "user0", "limit", 3));
            report.check("query_orders 返回列表", !orders.isError() && orders.text().startsWith("["),
                    brief(orders));

            ToolCall detail = session.call("get_order_detail", Map.of("orderId", "ORD_user0_001"));
            report.check("get_order_detail 含明细", !detail.isError() && detail.text().contains("\"items\""),
                    brief(detail));

            ToolCall inventory = session.call("check_inventory", Map.of("skuId", "SKU00001"));
            report.check("check_inventory 返回可用库存",
                    !inventory.isError() && inventory.text().contains("availableQty"), brief(inventory));

            ToolCall knowledge = session.call("search_knowledge", Map.of("query", "退货"));
            report.check("search_knowledge 命中文档", !knowledge.isError() && knowledge.text().startsWith("["),
                    brief(knowledge));

            ToolCall lock = lockTwice(session, report);
            report.check("lock_inventory 幂等", lock != null && lock.text().contains("\"idempotent\":true"),
                    lock == null ? "首次锁定失败" : brief(lock));

            ToolCall update = session.call("update_order_status",
                    Map.of("orderId", "ORD_user0_002", "newStatus", "COMPLETED", "remark", "客户端联调"));
            report.check("update_order_status 成功",
                    !update.isError() && update.text().contains("\"success\":true"), brief(update));
        } catch (RuntimeException e) {
            report.fail("工具调用", e.getMessage());
        }
    }

    /**
     * 同一订单 + SKU 连续锁定两次，第二次应返回 {@code idempotent:true} 且不重复扣减。
     */
    private static ToolCall lockTwice(Session session, Report report) {
        String orderId = "ORD_user0_003";
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("skuId", "SKU00002");
        args.put("quantity", 1);
        args.put("orderId", orderId);

        ToolCall first = session.call("lock_inventory", args);
        if (first.isError()) {
            report.fail("lock_inventory 首次锁定", brief(first));
            return null;
        }
        ToolCall second = session.call("lock_inventory", args);
        report.check("lock_inventory 重复调用不重复扣减",
                !second.isError() && second.text().contains("\"idempotent\":true"), brief(second));
        return second;
    }

    private static void governance(String endpoint, Report report) {
        report.section("治理层拦截");

        ToolCall methodWithoutKey = callWithoutKey(endpoint);
        report.check("无 API Key → 401", methodWithoutKey.httpStatus() == 401, brief(methodWithoutKey));

        Session disabled = null;
        try {
            disabled = Session.open(endpoint, DISABLED_KEY);
            report.fail("已禁用 Key → 403", "握手却成功了");
        } catch (RuntimeException e) {
            report.check("已禁用 Key → 403", Session.httpStatusOf(e) == 403, rootMessage(e));
        } finally {
            closeQuietly(disabled);
        }

        Session readOnly = null;
        try {
            readOnly = Session.open(endpoint, READ_KEY);
            ToolCall write = readOnly.call("update_order_status",
                    Map.of("orderId", "ORD_user0_001", "newStatus", "SHIPPED"));
            report.check("READ Key 调 WRITE 工具 → 403", write.httpStatus() == 403, brief(write));

            ToolCall otherUser = readOnly.call("query_orders", Map.of("userId", "user1"));
            report.check("READ Key 查他人订单 → 工具错误",
                    otherUser.httpStatus() == 0 && otherUser.isError()
                            && otherUser.text().contains("无权"), brief(otherUser));

            ToolCall ownOrder = readOnly.call("get_order_detail", Map.of("orderId", "ORD_user0_001"));
            report.check("READ Key 查自己订单 → 成功", !ownOrder.isError(), brief(ownOrder));
        } catch (RuntimeException e) {
            report.fail("只读 Key 场景", e.getMessage());
        } finally {
            closeQuietly(readOnly);
        }

        checkRateLimit(endpoint, report);
    }

    /**
     * 3 次/分钟的 Key：连续调用直到出现 429。同一分钟内重复运行本程序时首次即 429，同样算通过。
     */
    private static void checkRateLimit(String endpoint, Report report) {
        try (Session session = Session.open(endpoint, TIGHT_KEY)) {
            for (int i = 1; i <= 6; i++) {
                ToolCall call = session.call("check_inventory", Map.of("skuId", "SKU00001"));
                if (call.httpStatus() == 429) {
                    report.pass("超出限流阈值 → 429", "第 " + i + " 次调用 | " + brief(call));
                    return;
                }
            }
            report.fail("超出限流阈值 → 429", "连续 6 次调用都没有被限流");
        } catch (RuntimeException e) {
            report.fail("限流场景", e.getMessage());
        }
    }

    private static ToolCall callWithoutKey(String endpoint) {
        try (Session session = Session.open(endpoint, null)) {
            return session.call("query_orders", Map.of("userId", "user0"));
        } catch (RuntimeException e) {
            return new ToolCall("query_orders", 0, true, Session.rootMessage(e), Session.httpStatusOf(e));
        }
    }

    private static void closeQuietly(Session session) {
        if (session != null) {
            session.close();
        }
    }

    private static String brief(ToolCall call) {
        String text = call.text() == null ? "" : call.text().replaceAll("\\s+", " ");
        if (text.length() > 90) {
            text = text.substring(0, 90) + "…";
        }
        String tag = call.rejectedByHttp() ? "HTTP " + call.httpStatus() : (call.isError() ? "isError" : "ok");
        return tag + " " + call.latencyMs() + "ms | " + text;
    }

    private static String rootMessage(Throwable error) {
        return Session.rootMessage(error);
    }

    private McpGatewayClient() {
    }
}
