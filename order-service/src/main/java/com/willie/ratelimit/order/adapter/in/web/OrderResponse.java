package com.willie.ratelimit.order.adapter.in.web;

import com.willie.ratelimit.order.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        String item,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String status,
        Instant createdAt
) {
    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getItem(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.totalPrice(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}