package com.example.userservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class UserDirectoryController {
    public record Request(@NotEmpty @Size(max = 500) List<@NotNull @Positive Long> userIds) {}
    public record UserSummary(Long userId, String name) {}
    private final UserRepository users;

    public UserDirectoryController(UserRepository users) { this.users = users; }

    // Public display names only. Profile fields and login credentials remain private.
    @PostMapping("/users/directory")
    public List<UserSummary> lookup(@Valid @RequestBody Request request) {
        return users.findAllById(request.userIds()).stream()
                .map(user -> new UserSummary(user.getId(), user.getName())).toList();
    }
}
