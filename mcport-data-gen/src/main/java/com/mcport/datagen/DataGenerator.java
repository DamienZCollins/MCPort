package com.mcport.datagen;

import com.github.javafaker.Faker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 生成 docker/init/02-data.sql：1000 个用户、1 万条订单、500 个 SKU 的库存。
 *
 * <p>订单号固定形如 {@code ORD_user123_001}，便于按用户构造越权测试用例。
 * 生成的数据是确定性的（固定随机种子），重复执行输出一致。
 */
public final class DataGenerator {

    private static final int USER_COUNT = 1000;
    private static final int ORDERS_PER_USER = 10;
    private static final int SKU_COUNT = 500;
    private static final int ROWS_PER_INSERT = 500;

    private static final String[] STATUSES = {"PENDING", "PAID", "SHIPPED", "COMPLETED"};
    private static final String[] WAREHOUSES = {"WH-SH", "WH-BJ", "WH-GZ"};

    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DataGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "docker/init/02-data.sql");
        Faker faker = new Faker(Locale.CHINA);
        Random random = new Random(20251027L);
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 0, 0);

        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("-- 合成数据：由 mcport-data-gen 生成，请勿手工编辑\n");
            writer.write("-- 重新生成：mvn -pl mcport-data-gen exec:java\n\n");
            writer.write("USE `mcport_db`;\n\n");

            List<String> inventoryRows = new ArrayList<>();
            for (int sku = 1; sku <= SKU_COUNT; sku++) {
                String skuId = String.format("SKU%05d", sku);
                String warehouse = WAREHOUSES[sku % WAREHOUSES.length];
                int available = random.nextInt(1000);
                int reserved = random.nextInt(50);
                inventoryRows.add(String.format("('%s', '%s', %d, %d)", skuId, warehouse, available, reserved));
            }
            writeInserts(writer, "t_inventory", "sku_id, warehouse_id, available_qty, reserved_qty", inventoryRows);
            writer.write('\n');

            List<String> orderRows = new ArrayList<>();
            List<String> itemRows = new ArrayList<>();
            for (int user = 0; user < USER_COUNT; user++) {
                String userId = "user" + user;
                for (int seq = 1; seq <= ORDERS_PER_USER; seq++) {
                    String orderId = String.format("ORD_%s_%03d", userId, seq);
                    String status = STATUSES[random.nextInt(STATUSES.length)];
                    LocalDateTime createdAt = now.minusDays(random.nextInt(365))
                            .minusMinutes(random.nextInt(1440));

                    int itemCount = 1 + random.nextInt(3);
                    double total = 0;
                    List<String> lines = new ArrayList<>(itemCount);
                    for (int item = 0; item < itemCount; item++) {
                        String skuId = String.format("SKU%05d", 1 + random.nextInt(SKU_COUNT));
                        int quantity = 1 + random.nextInt(5);
                        double price = 10 + random.nextInt(49000) / 100.0;
                        total += quantity * price;
                        lines.add(String.format("('%s', '%s', %d, %.2f)", orderId, skuId, quantity, price));
                    }
                    itemRows.addAll(lines);
                    String remark = random.nextInt(5) == 0
                            ? escape(faker.commerce().productName() + " 订单备注")
                            : null;
                    orderRows.add(String.format("('%s', '%s', '%s', %.2f, %s, '%s')",
                            orderId, userId, status, total,
                            remark == null ? "NULL" : "'" + remark + "'",
                            SQL_TIME.format(createdAt)));
                }
            }
            writer.write("-- 订单：" + orderRows.size() + " 条，明细：" + itemRows.size() + " 条\n");
            writeInserts(writer, "t_order",
                    "order_id, user_id, status, total_amount, remark, created_at", orderRows);
            writer.write('\n');
            writeInserts(writer, "t_order_item", "order_id, sku_id, quantity, price", itemRows);
        }

        System.out.println("已生成 " + output.toAbsolutePath());
    }

    private static void writeInserts(BufferedWriter writer, String table, String columns, List<String> rows)
            throws IOException {
        for (int start = 0; start < rows.size(); start += ROWS_PER_INSERT) {
            int end = Math.min(start + ROWS_PER_INSERT, rows.size());
            writer.write("INSERT INTO `" + table + "` (" + columns + ") VALUES\n");
            for (int i = start; i < end; i++) {
                writer.write(rows.get(i));
                writer.write(i == end - 1 ? ";\n" : ",\n");
            }
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }
}
