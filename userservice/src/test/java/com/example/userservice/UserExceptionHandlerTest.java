package com.example.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserExceptionHandlerTest {

    private final UserExceptionHandler handler = new UserExceptionHandler();

    @Test
    void handleNotFound_ShouldReturn404() {
        UserNotFoundException ex = new UserNotFoundException(1L);
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("User not found: 1");
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void handleValidation_ShouldReturn400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("user", "name", "Name is required");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("name: Name is required");
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    void handleOther_ShouldReturn500() {
        Exception ex = new RuntimeException("Some error");
        ResponseEntity<ErrorResponse> response = handler.handleOther(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Internal error");
        assertThat(response.getBody().status()).isEqualTo(500);
    }
}