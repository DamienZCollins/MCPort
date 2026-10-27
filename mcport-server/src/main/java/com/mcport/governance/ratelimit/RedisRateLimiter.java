package com.mcport.governance.ratelimit;

import com.mcport.governance.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按分钟分桶的计数限流：每个 API Key 每分钟独立计数。
 *
 * <p>分桶边界处可能出现最多两倍的瞬时请求，对当前场景可接受。
 * Redis 不可用时放行请求，避免限流组件拖垮整个服务。
 *
 * <p>increment 与 expire 在同一个 Lua 脚本里执行：若分两步，进程在两者之间退出会留下
 * 没有 TTL 的 key，该 Key 的计数只增不减，之后永久 429 且 Redis 中永不回收。
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "limit:";
    private static final int BUCKET_TTL_SECONDS = 70;

    /**
     * 返回自增后的计数；key 没有 TTL 时补上，保证任何路径下都能过期。
     */
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkLimit(String apiKey, int limitPerMinute) {
        String bucket = String.valueOf(System.currentTimeMillis() / 60_000);
        String key = KEY_PREFIX + apiKey + ":" + bucket;
        Long count;
        try {
            count = redisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(BUCKET_TTL_SECONDS));
        } catch (RuntimeException e) {
            log.warn("限流计数失败，放行请求：{}", e.getMessage());
            return;
        }
        if (count != null && count > limitPerMinute) {
            throw new RateLimitException("限流：每分钟最多 " + limitPerMinute + " 次调用");
        }
    }
}
