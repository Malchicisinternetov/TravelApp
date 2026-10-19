package com.example.userservice;

public record UserSettingsResponse(
        Long id,
        Long userId,
        Long telegramId,
        String theme,
        String language,
        String notificationMethod
) {
    public static UserSettingsResponse from(UserSettingsEntity s) {
        return new UserSettingsResponse(
                s.getId(),
                s.getUser() != null ? s.getUser().getId() : null,
                s.getTelegramId(),
                s.getTheme(),
                s.getLanguage(),
                s.getNotificationMethod()
        );
    }
}