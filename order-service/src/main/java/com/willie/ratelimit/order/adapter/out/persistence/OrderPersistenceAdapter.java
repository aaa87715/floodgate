package com.willie.ratelimit.order.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.willie.ratelimit.order.application.port.out.LoadOrderPort;
import com.willie.ratelimit.order.application.port.out.SaveOrderPort;
import com.willie.ratelimit.order.domain.Order;

@Component 
public class OrderPersistenceAdapter implements LoadOrderPort , SaveOrderPort {

    private final SpringDataOrderRepository repo; 

    public OrderPersistenceAdapter (SpringDataOrderRepository repo ){
        this.repo = repo;
    }

	@Override
	public Order save(Order order) {
		return repo.save(OrderJpaEntity.from(order)).toDomain();
	}

	@Override
	public Optional<Order> findById(Long orderId) {
        return repo.findById(orderId).map(OrderJpaEntity::toDomain);
	}

	@Override
	public List<Order> findAll() {
		return repo.findAll()
				.stream()
				.map(OrderJpaEntity::toDomain)
				.toList();
	}


}
