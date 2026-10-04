package com.willie.ratelimit.order.common;

public abstract class BusinessRuleException extends OrderAppException {

    protected BusinessRuleException(String msg){
        super(msg);
    }
    
}
