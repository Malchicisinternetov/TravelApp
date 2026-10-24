package com.example.tripservice;

import org.springframework.http.HttpStatus;

public class TripException extends RuntimeException {
    private final HttpStatus status;
    public TripException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public HttpStatus getStatus() {
        return status;
    }
}
