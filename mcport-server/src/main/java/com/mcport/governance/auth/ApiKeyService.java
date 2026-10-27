package com.mcport.governance.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.entity.ApiKey;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.UnauthorizedException;
import com.mcport.mapper.ApiKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * API Key 鉴权：先查 Redis 缓存，未命中再查 MySQL 并回填。
 *
 * <p>缓存里包含 {@code enabled} 与权限，因此 TTL 就是「禁用 / 改权限」生效的最大延迟，
 * 取 60 秒而不是小时级：既保留分钟内的复用，也把撤销窗口收敛到可接受范围。
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String CACHE_PREFIX = "api_key:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final TypeReference<List<String>> PERMISSIONS_TYPE = new TypeReference<>() {
    };

    private final ApiKeyMapper apiKeyMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ApiKeyPrincipal authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new UnauthorizedException("缺少 API Key");
        }
        CachedApiKey cached = readCache(rawApiKey);
        if (cached == null) {
            cached = loadFromDatabase(rawApiKey);
            writeCache(rawApiKey, cached);
        }
        if (!cached.enabled()) {
            throw new ForbiddenException("API Key 已禁用");
        }
        return new ApiKeyPrincipal(rawApiKey, cached.userId(), cached.permissions(), cached.rateLimit());
    }

    /**
     * 立即失效某个 Key 的缓存。目前没有管理接口调用它，保留作为运维 / 后续管理端点的撤销通道。
     */
    public void evict(String rawApiKey) {
        try {
            redisTemplate.delete(CACHE_PREFIX + rawApiKey);
        } catch (RuntimeException e) {
            log.warn("清除 API Key 缓存失败：{}", e.getMessage());
        }
    }

    private CachedApiKey loadFromDatabase(String rawApiKey) {
        ApiKey entity = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getApiKey, rawApiKey));
        if (entity == null) {
            throw new UnauthorizedException("无效的 API Key");
        }
        return new CachedApiKey(entity.getUserId(), parsePermissions(entity.getPermissions()),
                entity.getRateLimitPerMinute() == null ? 60 : entity.getRateLimitPerMinute(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }

    private List<String> parsePermissions(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, PERMISSIONS_TYPE);
        } catch (Exception e) {
            log.warn("解析 permissions 失败，按无权限处理：{}", e.getMessage());
            return List.of();
        }
    }

    private CachedApiKey readCache(String rawApiKey) {
        try {
            String value = redisTemplate.opsForValue().get(CACHE_PREFIX + rawApiKey);
            return value == null ? null : objectMapper.readValue(value, CachedApiKey.class);
        } catch (Exception e) {
            // 缓存不可用或内容损坏时降级查库，不阻断请求
            log.warn("读取 API Key 缓存失败，回退数据库：{}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String rawApiKey, CachedApiKey cached) {
        try {
            redisTemplate.opsForValue()
                    .set(CACHE_PREFIX + rawApiKey, objectMapper.writeValueAsString(cached), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入 API Key 缓存失败：{}", e.getMessage());
        }
    }

    public record CachedApiKey(String userId, List<String> permissions, int rateLimit, boolean enabled) {
    }
}
