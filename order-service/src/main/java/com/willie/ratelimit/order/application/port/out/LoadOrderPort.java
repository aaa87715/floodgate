package com.willie.ratelimit.order.application.port.out;

import java.util.List;
import java.util.Optional;

import com.willie.ratelimit.order.domain.Order;

public interface LoadOrderPort {
    public Optional<Order> findById(Long orderId);
    public List<Order> findAll();
}
