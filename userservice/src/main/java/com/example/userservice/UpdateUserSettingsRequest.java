package com.example.userservice;

public record UpdateUserSettingsRequest(
        Long telegramId,
        String theme,
        String language,
        String notificationMethod
) {
    public UserSettingsEntity toEntity() {
        return new UserSettingsEntity(null, null, telegramId, theme, language, notificationMethod);
    }
}