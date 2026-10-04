package com.willie.ratelimit.order.application.exception;

import com.willie.ratelimit.order.common.NotFoundException;

public class ProductNotFoundException extends NotFoundException {
    
    public ProductNotFoundException(String message) {
        super(message);
    }

	@Override
	public String errorCode() {
		
		return "ProductNotFound";
	}
}
