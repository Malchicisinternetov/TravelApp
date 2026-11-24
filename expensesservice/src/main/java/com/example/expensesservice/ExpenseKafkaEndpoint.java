package com.example.expensesservice;

import com.example.sharedlib.messaging.*;
import jakarta.validation.Validator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class ExpenseKafkaEndpoint implements RpcEndpoint {
    private final ExpenseController expenses;
    private final RpcSupport auth;
    public ExpenseKafkaEndpoint(ExpenseController expenses, JwtDecoder decoder, Validator validator) {
        this.expenses = expenses; this.auth = new RpcSupport(decoder, validator);
    }
    @Override public void authorize(RpcRequest r) { auth.jwt(r); }
    @Override public Object handle(RpcRequest r) {
        try { return dispatch(r); }
        catch (ExpenseException ex) { throw new RpcException(ex.getStatus().value(), ex.getMessage()); }
    }
    private Object dispatch(RpcRequest r) {
        var jwt = auth.jwt(r);
        String[] path = r.path().split("/");
        if (path.length < 4 || !"trips".equals(path[1]) || !"expenses".equals(path[3]))
            throw new RpcException(404, "Unknown operation");
        Long id = RpcSupport.id(path[2]);
        if (path.length == 4) return switch (r.method()) {
            case "GET" -> expenses.history(id, jwt);
            case "POST" -> expenses.create(id, jwt, auth.body(r, ExpenseRequest.class)).getBody();
            default -> throw new RpcException(404, "Unknown operation");
        };
        if ("GET".equals(r.method())) {
            if (path.length == 5 && "me".equals(path[4])) return expenses.myHistory(id, jwt);
            if (path.length == 5 && "summary".equals(path[4])) return expenses.summary(id, jwt);
            if (path.length == 6 && "summary".equals(path[4]) && "me".equals(path[5])) return expenses.mySummary(id, jwt);
        }
        if (path.length == 5 && "DELETE".equals(r.method())) { expenses.delete(id, RpcSupport.id(path[4]), jwt); return null; }
        throw new RpcException(404, "Unknown operation");
    }
}
