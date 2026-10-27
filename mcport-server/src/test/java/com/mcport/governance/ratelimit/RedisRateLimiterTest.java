package com.mcport.governance.ratelimit;

import com.mcport.governance.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedisRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
    }

    @Test
    void allowsRequestUnderLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(60L);

        assertThatCode(() -> limiter.checkLimit("key", 60)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestOverLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(61L);

        assertThatThrownBy(() -> limiter.checkLimit("key", 60))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("60");
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> limiter.checkLimit("key", 1)).doesNotThrowAnyException();
    }
}
