package com.willie.ratelimit.order.common;

public abstract class OrderAppException extends RuntimeException {

    protected OrderAppException(String message) {
        super(message);
    }
    public abstract String errorCode();     
}