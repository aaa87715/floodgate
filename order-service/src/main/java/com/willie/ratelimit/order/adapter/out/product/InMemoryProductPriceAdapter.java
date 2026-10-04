package com.willie.ratelimit.order.adapter.out.product;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.willie.ratelimit.order.application.port.out.LoadProductPort;

@Component
public class InMemoryProductPriceAdapter implements LoadProductPort {
   private static final Map<String, BigDecimal> PRICES = Map.of(
            "keyboard", new BigDecimal("1500"),
            "mouse", new BigDecimal("800")
    );

    @Override
    public Optional<BigDecimal> findPrice(String item) {
        return Optional.ofNullable(PRICES.get(item));
    }
}
