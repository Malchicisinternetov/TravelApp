package com.example.sharedlib.messaging;

public class RpcException extends RuntimeException {
    private final int status;
    public RpcException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
