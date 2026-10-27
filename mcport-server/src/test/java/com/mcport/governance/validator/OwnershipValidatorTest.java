package com.mcport.governance.validator;

import com.mcport.entity.Order;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipValidatorTest {

    private final OwnershipValidator validator = new OwnershipValidator();

    @AfterEach
    void tearDown() {
        ApiKeyContext.clear();
    }

    @Test
    void throwsWhenOrderMissing() {
        ApiKeyContext.set(principal("user0", false));

        assertThatThrownBy(() -> validator.checkOrderOwnership(null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void allowsOwnOrder() {
        ApiKeyContext.set(principal("user0", false));

        assertThat(validator.checkOrderOwnership(order("user0")).getUserId()).isEqualTo("user0");
    }

    @Test
    void rejectsOtherUsersOrder() {
        ApiKeyContext.set(principal("user0", false));

        assertThatThrownBy(() -> validator.checkOrderOwnership(order("user1")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("其他用户");
    }

    @Test
    void allowsAdmin() {
        ApiKeyContext.set(principal("admin", true));

        assertThat(validator.checkOrderOwnership(order("user1")).getUserId()).isEqualTo("user1");
    }

    private ApiKeyPrincipal principal(String userId, boolean admin) {
        return new ApiKeyPrincipal("k", userId,
                admin ? List.of("READ", "WRITE", "ADMIN") : List.of("READ"), 60);
    }

    private Order order(String userId) {
        Order order = new Order();
        order.setOrderId("ORD_1");
        order.setUserId(userId);
        return order;
    }
}
