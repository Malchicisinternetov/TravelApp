package com.example.expensesservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/trips/{tripId}/expenses")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@PathVariable @Positive Long tripId,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExpenseRequest request) {
        ExpenseResponse response = service.create(tripId, userId(jwt), bearer(jwt), request);
        return ResponseEntity.created(URI.create("/trips/" + tripId + "/expenses/" + response.id())).body(response);
    }

    @GetMapping
    public List<ExpenseResponse> history(@PathVariable @Positive Long tripId, @AuthenticationPrincipal Jwt jwt) {
        return service.history(tripId, userId(jwt), bearer(jwt), false);
    }

    @GetMapping("/me")
    public List<ExpenseResponse> myHistory(@PathVariable @Positive Long tripId, @AuthenticationPrincipal Jwt jwt) {
        return service.history(tripId, userId(jwt), bearer(jwt), true);
    }

    @GetMapping("/summary")
    public List<ExpenseSummary> summary(@PathVariable @Positive Long tripId, @AuthenticationPrincipal Jwt jwt) {
        return service.summary(tripId, userId(jwt), bearer(jwt), false);
    }

    @GetMapping("/summary/me")
    public List<ExpenseSummary> mySummary(@PathVariable @Positive Long tripId, @AuthenticationPrincipal Jwt jwt) {
        return service.summary(tripId, userId(jwt), bearer(jwt), true);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long tripId,
            @PathVariable @Positive Long expenseId, @AuthenticationPrincipal Jwt jwt) {
        service.delete(tripId, expenseId, userId(jwt), bearer(jwt));
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private String bearer(Jwt jwt) {
        return "Bearer " + jwt.getTokenValue();
    }
}
