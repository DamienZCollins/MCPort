package com.mcport.governance.auth;

import java.util.List;

/**
 * 一次请求的调用方身份：API Key 及其携带的权限与限流配置。
 */
public record ApiKeyPrincipal(
        String apiKey,
        String userId,
        List<String> permissions,
        int rateLimitPerMinute) {

    public static final String READ = "READ";
    public static final String WRITE = "WRITE";

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean isAdmin() {
        return "admin".equals(userId);
    }
}
