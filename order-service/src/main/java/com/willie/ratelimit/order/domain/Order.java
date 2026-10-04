package com.willie.ratelimit.order.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.willie.ratelimit.order.common.InvalidOrderException;

public class Order {

    private final Long id ;
    private final String item;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final Instant createdAt;
    private OrderStatus status;        

    private Order(Long id, String item, int quantity, BigDecimal unitPrice, OrderStatus status, Instant createdAt) {
        if (item == null || item.isBlank()) {
            throw new InvalidOrderException("Item must not be blank");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new InvalidOrderException("Unit price must be positive");
        }
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException("Status and createdAt are required");
        }
        this.id = id;
        this.item = item;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Order restore(Long id, String item, int quantity, BigDecimal unitPrice, OrderStatus status, Instant createdAt) {
        return new Order(id, item, quantity, unitPrice, status, createdAt);
    }
    public static Order place( String item, int quantity, BigDecimal unitPrice) {
        return new Order(null, item, quantity, unitPrice, OrderStatus.CREATED, Instant.now());
    }

    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }
    public BigDecimal totalPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    public Long getId() { return id; }
    public String getItem() { return item; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
