package com.willie.ratelimit.order.application.port.in.command;

public class OrderQuery {

    private Long orderId;

    public Long getOrderId() {
        return orderId;
    }
    public OrderQuery(Long orderId) {
        if(orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        this.orderId = orderId;
    }
}