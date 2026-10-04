package com.willie.ratelimit.order.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.willie.ratelimit.order.common.BusinessRuleException;
import com.willie.ratelimit.order.common.NotFoundException;

/**
 * 接的是兩個抽象父類別，不是個別子類別。
 * 以後新增 NotFoundException / BusinessRuleException 的子類別，不用回來改這裡。
 *
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.errorCode(), e.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.errorCode(), e.getMessage()));
    }
}
