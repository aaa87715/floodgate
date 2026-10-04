package com.willie.ratelimit.order.common;

public abstract class NotFoundException extends OrderAppException {

    protected NotFoundException(String message) {
        super(message);
    }

}
