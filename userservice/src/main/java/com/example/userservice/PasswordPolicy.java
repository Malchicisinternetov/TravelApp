package com.example.userservice;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 10
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isLowerCase)
                || password.codePoints().noneMatch(Character::isUpperCase)
                || password.codePoints().noneMatch(Character::isDigit)
                || password.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            throw new AuthException(BAD_REQUEST,
                    "Пароль: минимум 10 символов, строчная и прописная буквы, цифра и спецсимвол; максимум 72 байта UTF-8.");
        }
        String letters = password.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-яё]", "");
        if (Set.of("password", "qwerty", "qwertyuiop", "admin", "administrator", "welcome", "letmein",
                "iloveyou", "travelapp", "пароль").contains(letters)) {
            throw new AuthException(BAD_REQUEST, "Это слишком распространённый пароль. Выберите менее очевидный.");
        }
    }
}
