package com.willie.ratelimit.order.adapter.out.persistence;

import com.willie.ratelimit.order.domain.Order;
import com.willie.ratelimit.order.domain.OrderStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String item;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt; 

    protected OrderJpaEntity() {
    }

    static OrderJpaEntity from(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.id = order.getId();
        e.item = order.getItem();
        e.quantity = order.getQuantity();
        e.unitPrice = order.getUnitPrice();
        e.status = order.getStatus();
        e.createdAt = order.getCreatedAt();
        return e;
    }

    
    Order toDomain() {
        return Order.restore(id, item, quantity, unitPrice, status, createdAt);
    }
}