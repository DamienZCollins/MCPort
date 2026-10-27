package com.mcport.governance.ratelimit;

import com.mcport.governance.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 按分钟分桶的计数限流：每个 API Key 每分钟独立计数。
 *
 * <p>分桶边界处可能出现最多两倍的瞬时请求，对当前场景可接受。
 * Redis 不可用时放行请求，避免限流组件拖垮整个服务。
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "limit:";
    private static final Duration BUCKET_TTL = Duration.ofSeconds(70);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkLimit(String apiKey, int limitPerMinute) {
        String bucket = String.valueOf(System.currentTimeMillis() / 60_000);
        String key = KEY_PREFIX + apiKey + ":" + bucket;
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, BUCKET_TTL);
            }
        } catch (RuntimeException e) {
            log.warn("限流计数失败，放行请求：{}", e.getMessage());
            return;
        }
        if (count != null && count > limitPerMinute) {
            throw new RateLimitException("限流：每分钟最多 " + limitPerMinute + " 次调用");
        }
    }
}
