package com.mcport.governance.auth;

/**
 * 当前请求的 API Key 上下文。由 {@link ApiKeyAuthFilter} 写入并清理。
 */
public final class ApiKeyContext {

    private static final ThreadLocal<ApiKeyPrincipal> HOLDER = new ThreadLocal<>();

    private ApiKeyContext() {
    }

    public static void set(ApiKeyPrincipal principal) {
        HOLDER.set(principal);
    }

    public static ApiKeyPrincipal get() {
        return HOLDER.get();
    }

    public static ApiKeyPrincipal require() {
        ApiKeyPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new IllegalStateException("缺少 API Key 上下文，请求未经鉴权");
        }
        return principal;
    }

    public static String currentUserId() {
        return require().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
