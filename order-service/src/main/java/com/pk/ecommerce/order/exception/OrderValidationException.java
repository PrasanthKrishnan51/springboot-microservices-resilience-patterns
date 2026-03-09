package com.pk.ecommerce.order.exception;

import com.pk.ecommerce.common.exception.EcommerceException;

public class OrderValidationException extends EcommerceException {
    public OrderValidationException(String msg) { super(msg, "ORDER_VALIDATION_ERROR", 400); }
}
