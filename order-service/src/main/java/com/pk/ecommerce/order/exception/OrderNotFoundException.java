package com.pk.ecommerce.order.exception;

import com.pk.ecommerce.common.exception.EcommerceException;

public class OrderNotFoundException extends EcommerceException {
    public OrderNotFoundException(String msg) { super(msg, "ORDER_NOT_FOUND", 404); }
}
