package com.mcport.governance.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.entity.ApiKey;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.UnauthorizedException;
import com.mcport.mapper.ApiKeyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private final ApiKeyMapper apiKeyMapper = mock(ApiKeyMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiKeyService service = new ApiKeyService(apiKeyMapper, redisTemplate, objectMapper);

    @SuppressWarnings("unchecked")
    private void cacheReturns(String value) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(value);
    }

    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> service.authenticate(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsUnknownKey() {
        cacheReturns(null);
        when(apiKeyMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate("nope"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("无效");
    }

    @Test
    void rejectsDisabledKey() {
        cacheReturns(null);
        when(apiKeyMapper.selectOne(any())).thenReturn(apiKey("[\"READ\"]", false, 60));

        assertThatThrownBy(() -> service.authenticate("disabled-key"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    void loadsFromCacheWithoutHittingDatabase() throws Exception {
        cacheReturns(objectMapper.writeValueAsString(
                new ApiKeyService.CachedApiKey("user0", java.util.List.of("READ"), 60, true)));

        ApiKeyPrincipal principal = service.authenticate("cached-key");

        assertThat(principal.userId()).isEqualTo("user0");
        assertThat(principal.hasPermission(ApiKeyPrincipal.READ)).isTrue();
        assertThat(principal.hasPermission(ApiKeyPrincipal.WRITE)).isFalse();
    }

    @Test
    void fallsBackToDatabaseAndReadsBothPermissions() {
        cacheReturns(null);
        when(apiKeyMapper.selectOne(any())).thenReturn(apiKey("[\"READ\",\"WRITE\"]", true, 100));

        ApiKeyPrincipal principal = service.authenticate("admin-key");

        assertThat(principal.permissions()).containsExactly("READ", "WRITE");
        assertThat(principal.rateLimitPerMinute()).isEqualTo(100);
    }

    private ApiKey apiKey(String permissions, boolean enabled, int rateLimit) {
        ApiKey entity = new ApiKey();
        entity.setApiKey("key");
        entity.setUserId("user0");
        entity.setPermissions(permissions);
        entity.setEnabled(enabled);
        entity.setRateLimitPerMinute(rateLimit);
        return entity;
    }
}
