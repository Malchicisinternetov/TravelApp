package com.example.expensesservice;

import org.springframework.http.HttpStatus;

public class ExpenseException extends RuntimeException {
    private final HttpStatus status;
    public ExpenseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public HttpStatus getStatus() {
        return status;
    }
}
