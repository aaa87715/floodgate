package com.willie.ratelimit.order.adapter.in.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.willie.ratelimit.order.application.port.in.command.OrderQuery;
import com.willie.ratelimit.order.application.port.in.command.PlaceOrderCommand;
import com.willie.ratelimit.order.application.usecase.PlaceOrderUseCase;
import com.willie.ratelimit.order.application.usecase.QueryOrderUseCase;
import com.willie.ratelimit.order.domain.Order;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final QueryOrderUseCase queryOrder;
    private final PlaceOrderUseCase placeOrder;
    
    public OrderController(QueryOrderUseCase queryOrder, PlaceOrderUseCase placeOrder) {
        this.queryOrder = queryOrder;
        this.placeOrder = placeOrder;
    }
    @PostMapping
    public ResponseEntity<OrderResponse> placeAnOrder(@Valid @RequestBody OrderRequest request) {
        Order order = placeOrder.placeOrder(new PlaceOrderCommand(request.item(), request.quantity()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(order.getId())
            .toUri();

    return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return queryOrder.getOrder(new OrderQuery(id))
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return queryOrder.getAllOrders()
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

}
