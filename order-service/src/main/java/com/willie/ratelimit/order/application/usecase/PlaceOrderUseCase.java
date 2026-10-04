package com.willie.ratelimit.order.application.usecase;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willie.ratelimit.order.application.exception.ProductNotFoundException;
import com.willie.ratelimit.order.application.port.in.command.PlaceOrderCommand;
import com.willie.ratelimit.order.application.port.out.LoadProductPort;
import com.willie.ratelimit.order.application.port.out.SaveOrderPort;
import com.willie.ratelimit.order.domain.Order;

@Service 
public class PlaceOrderUseCase {
    
    private final LoadProductPort productPricePort;
    private final SaveOrderPort saveOrderPort;

	public PlaceOrderUseCase(LoadProductPort productPricePort, SaveOrderPort saveOrderPort) {
        this.productPricePort = productPricePort;
        this.saveOrderPort = saveOrderPort;
    }
    @Transactional
    public Order placeOrder( PlaceOrderCommand command) {
        BigDecimal price = productPricePort.findPrice(command.item())
                        .orElseThrow(() -> new ProductNotFoundException("Product not found: " + command.item()));
        return saveOrderPort.save(Order.place(command.item(), command.quantity(), price));
    }
}
