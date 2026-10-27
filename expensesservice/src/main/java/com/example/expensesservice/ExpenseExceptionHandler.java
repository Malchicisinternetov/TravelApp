package com.example.expensesservice;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ExpenseExceptionHandler {
    public record ErrorResponse(String message, int status) {}

    @ExceptionHandler(ExpenseException.class)
    public ResponseEntity<ErrorResponse> handleExpense(ExpenseException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getMessage(), ex.getStatus().value()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ServletRequestBindingException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid expense request", 400));
    }
}
