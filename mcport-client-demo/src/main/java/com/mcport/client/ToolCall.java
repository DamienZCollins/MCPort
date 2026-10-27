package com.mcport.client;

/**
 * 一次工具调用的结果。
 *
 * @param httpStatus HTTP 层被拦截时的状态码，未被拦截为 0
 */
record ToolCall(String tool, long latencyMs, boolean isError, String text, int httpStatus) {

    boolean rejectedByHttp() {
        return httpStatus != 0;
    }
}
