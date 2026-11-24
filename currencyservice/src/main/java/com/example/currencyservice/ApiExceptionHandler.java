package com.example.currencyservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("status", ex.getStatusCode().value(), "message", ex.getReason() == null ? "Ошибка запроса." : ex.getReason()));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", "Проверьте параметры запроса."));
    }
    @ExceptionHandler(RestClientException.class)
    ResponseEntity<?> provider(RestClientException ex) {
        return ResponseEntity.status(503).body(Map.of("status", 503, "message", "Не удалось получить данные от внешнего сервиса. Повторите позже."));
    }
}
