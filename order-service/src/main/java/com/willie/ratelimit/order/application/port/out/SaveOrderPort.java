package com.willie.ratelimit.order.application.port.out;

import com.willie.ratelimit.order.domain.Order;

public interface SaveOrderPort {
    public Order save(Order order);
}
