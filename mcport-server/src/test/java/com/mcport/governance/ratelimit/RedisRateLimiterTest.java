package com.mcport.governance.ratelimit;

import com.mcport.governance.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);
    }

    @Test
    void allowsRequestUnderLimit() {
        when(valueOperations.increment(anyString())).thenReturn(60L);

        assertThatCode(() -> limiter.checkLimit("key", 60)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestOverLimit() {
        when(valueOperations.increment(anyString())).thenReturn(61L);

        assertThatThrownBy(() -> limiter.checkLimit("key", 60))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("60");
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> limiter.checkLimit("key", 1)).doesNotThrowAnyException();
    }
}
