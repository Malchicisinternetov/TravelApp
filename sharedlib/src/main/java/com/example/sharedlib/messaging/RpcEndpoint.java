package com.example.sharedlib.messaging;

public interface RpcEndpoint {
    default void authorize(RpcRequest request) {}
    Object handle(RpcRequest request);
}
