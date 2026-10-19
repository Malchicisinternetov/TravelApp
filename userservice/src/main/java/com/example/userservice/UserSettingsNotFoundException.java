package com.example.userservice;

public class UserSettingsNotFoundException extends RuntimeException {
    public UserSettingsNotFoundException(Long telegramId) {
        super("User Settings not found by telegramId: " + telegramId);
    }
}
