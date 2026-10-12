package com.codearena.codearena.exception;

/** A request that is syntactically valid but cannot be completed by the business rules. */
public class BusinessException extends IllegalArgumentException {
    public BusinessException(String message) {
        super(message);
    }
}
