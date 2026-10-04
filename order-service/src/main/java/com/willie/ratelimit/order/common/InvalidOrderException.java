package com.willie.ratelimit.order.common;

/** 輸入資料違反訂單的業務規則（品名空白、數量非正、單價非正）*/
public class InvalidOrderException extends BusinessRuleException {

    public InvalidOrderException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "InvalidOrder";
    }
}
