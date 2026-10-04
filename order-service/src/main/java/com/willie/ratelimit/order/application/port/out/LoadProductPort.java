package com.willie.ratelimit.order.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

public interface LoadProductPort {
    public Optional<BigDecimal> findPrice(String item);
}
