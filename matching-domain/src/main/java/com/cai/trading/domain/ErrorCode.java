package com.cai.trading.domain;

/** 对外稳定的业务错误码；异常文本可以调整，错误码不应随意改变。 */
public enum ErrorCode {
    INVALID_ORDER,
    INSUFFICIENT_BALANCE,
    INSUFFICIENT_POSITION,
    ORDER_NOT_FOUND,
    ORDER_NOT_CANCELABLE,
    DUPLICATE_ORDER,
    DUPLICATE_REQUEST_CONFLICT,
    CURRENCY_MISMATCH,
    INTERNAL_ERROR
}

