package com.willie.ratelimit.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository 
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, Long>{

}
