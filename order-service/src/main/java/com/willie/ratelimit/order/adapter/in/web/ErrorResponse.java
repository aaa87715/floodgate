package com.willie.ratelimit.order.adapter.in.web;

/** 錯誤回應的統一格式。errorCode 給程式判斷，message 給人看 */
public record ErrorResponse(String errorCode, String message) {
}
