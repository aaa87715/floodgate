package com.willie.ratelimit.order.application.usecase;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willie.ratelimit.order.application.port.in.command.OrderQuery;
import com.willie.ratelimit.order.application.port.out.LoadOrderPort;
import com.willie.ratelimit.order.domain.Order;

@Service 
public class QueryOrderUseCase {

    private final LoadOrderPort loadOrderPort;

    public QueryOrderUseCase(LoadOrderPort loadOrderPort) {
        this.loadOrderPort = loadOrderPort;
    }
    @Transactional(readOnly = true)
    public Optional<Order> getOrder(OrderQuery orderQuery) {
        return loadOrderPort.findById(orderQuery.getOrderId());
        
    }
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return loadOrderPort.findAll();
    }
}
