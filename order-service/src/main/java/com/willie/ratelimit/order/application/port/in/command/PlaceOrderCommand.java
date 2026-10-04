package com.willie.ratelimit.order.application.port.in.command;

import com.willie.ratelimit.order.common.InvalidOrderException;

public record PlaceOrderCommand(String item, int quantity) {

    public PlaceOrderCommand {
        if(item == null || item.isEmpty()) {
            throw new InvalidOrderException("Item cannot be null or empty");
        }
        if(quantity <= 0) {
            throw new InvalidOrderException("Quantity must be a positive integer");
        }
    }
}
