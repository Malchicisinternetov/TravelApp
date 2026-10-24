package com.example.userservice;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService {
    private final UserRepository repository;
    private final UserService users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;

    public AuthService(UserRepository repository, UserService users,
                       PasswordEncoder passwords, TokenService tokens) {
        this.repository = repository;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode("not-a-real-account-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validatePasswordLength(request.password());
        String username = request.username().toLowerCase(Locale.ROOT);
        if (repository.existsByUsername(username)) {
            throw new AuthException(CONFLICT, "Username is already registered");
        }
        UserEntity user = new UserEntity();
        user.setName(request.name());
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(request.password()));
        users.createUser(user);
        // Force unique-username violations to occur inside the request transaction.
        repository.flush();
        return tokens.issue(user.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        validatePasswordLength(request.password());
        UserEntity user = repository.findByUsername(request.username().toLowerCase(Locale.ROOT)).orElse(null);
        String hash = user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash();
        boolean matches = passwords.matches(request.password(), hash);
        if (!matches || user == null || user.getPasswordHash() == null) {
            throw new AuthException(UNAUTHORIZED, "Invalid username or password");
        }
        return tokens.issue(user.getId());
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthException(BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
    }
}
