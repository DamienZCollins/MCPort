package com.mcport.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制台报告：逐条检查结果 + 小结。任一项失败时进程以非 0 退出。
 */
final class Report {

    private final List<String> failures = new ArrayList<>();
    private int passed;

    void pass(String name, String detail) {
        passed++;
        System.out.printf("  PASS  %-42s %s%n", name, detail);
    }

    void fail(String name, String detail) {
        failures.add(name);
        System.out.printf("  FAIL  %-42s %s%n", name, detail);
    }

    void check(String name, boolean ok, String detail) {
        if (ok) {
            pass(name, detail);
        } else {
            fail(name, detail);
        }
    }

    void section(String title) {
        System.out.println();
        System.out.println("== " + title + " " + "=".repeat(Math.max(0, 60 - title.length())));
    }

    /**
     * @return 进程退出码：0 全部通过，1 有失败项
     */
    int summary() {
        System.out.println();
        System.out.printf("Summary: %d passed, %d failed%n", passed, failures.size());
        if (!failures.isEmpty()) {
            System.out.println("Failed checks: " + String.join(", ", failures));
            return 1;
        }
        return 0;
    }
}
