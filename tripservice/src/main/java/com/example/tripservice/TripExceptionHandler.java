package com.example.tripservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class TripExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(TripExceptionHandler.class);
    @ExceptionHandler(TripException.class)
    public ResponseEntity<ErrorResponse> handleTrip(TripException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getMessage(), ex.getStatus().value()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message, 400));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request body, header or parameter", 400));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse("Internal error", 500));
    }
}
