package com.mcport.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    void allowsForwardTransitions() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void allowsSameStatusAsIdempotent() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }

    @Test
    void rejectsBackwardTransitions() {
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.PENDING)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void rejectsSkippingSteps() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void completedHasNoTargets() {
        assertThat(OrderStatus.COMPLETED.allowedTargets()).isEmpty();
    }

    @Test
    void parseIsCaseInsensitive() {
        assertThat(OrderStatus.parse("paid")).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void parseRejectsUnknownStatus() {
        assertThatThrownBy(() -> OrderStatus.parse("CANCELLED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法订单状态");
    }
}
