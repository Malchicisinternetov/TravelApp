package com.example.userservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private UserService userService;

    private UserEntity user;
    private UserSettingsEntity settings;

    @BeforeEach
    void setUp() {
        user = new UserEntity(1L, ZoneId.of("UTC"), Currency.getInstance("USD"), BigDecimal.TEN, "John");
        settings = new UserSettingsEntity(1L, user, 123L, "WHITE", "en", "EMAIL");
    }

    @Test
    void getUserById_WhenUserExists_ShouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserEntity result = userService.getUserById(1L);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void getUserById_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(1L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found: 1");
    }

    @Test
    void createUser_ShouldSaveUserAndSettings() {
        UserEntity newUser = new UserEntity(null, ZoneId.of("UTC"), Currency.getInstance("USD"), BigDecimal.TEN, "John");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(userSettingsRepository.save(any(UserSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = userService.createUser(newUser);

        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository).save(newUser);
        verify(userSettingsRepository).save(argThat(s ->
                s.getUser().equals(newUser)
                        && s.getTelegramId() == null
                        && "WHITE".equals(s.getTheme())
                        && "ru".equals(s.getLanguage())
                        && s.getNotificationMethod() == null
        ));
    }

    @Test
    void updateUser_WhenUserExists_ShouldUpdateUser() {
        UserEntity existingUser = new UserEntity(1L, ZoneId.of("UTC"), Currency.getInstance("USD"), BigDecimal.TEN, "John");
        UserEntity updatedUser = new UserEntity(1L, ZoneId.of("Europe/Moscow"), Currency.getInstance("EUR"), BigDecimal.valueOf(20), "Jane");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = userService.updateUser(1L, updatedUser);

        assertThat(result.getTimezone()).isEqualTo(ZoneId.of("Europe/Moscow"));
        assertThat(result.getCurrency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(20));
        assertThat(result.getName()).isEqualTo("Jane");
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void updateUser_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(1L, user))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUser_ShouldDeleteUserAndSettings() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userSettingsRepository).deleteByUserId(1L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_WhenUserNotFound_ShouldThrowException() {
        // FIX: новый тест — проверяем ветку "пользователь не найден"
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found: 999");

        verify(userSettingsRepository, never()).deleteByUserId(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void updateUserSettings_WhenSettingsExist_ShouldUpdateSettings() {
        UserSettingsEntity existingSettings = new UserSettingsEntity(1L, user, 123L, "WHITE", "en", "EMAIL");
        UserSettingsEntity newSettings = new UserSettingsEntity(null, null, 456L, "BLACK", "fr", "SMS");
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existingSettings));
        when(userSettingsRepository.save(any(UserSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSettingsEntity result = userService.updateUserSettings(1L, newSettings);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getTelegramId()).isEqualTo(456L);
        assertThat(result.getTheme()).isEqualTo("BLACK");
        assertThat(result.getLanguage()).isEqualTo("fr");
        assertThat(result.getNotificationMethod()).isEqualTo("SMS");
    }

    @Test
    void updateUserSettings_WhenSettingsNotFound_ShouldThrowException() {
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserSettings(1L, settings))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void bindTelegram_ShouldUpdateTelegramId() {
        UserSettingsEntity existingSettings = new UserSettingsEntity(1L, user, 123L, "WHITE", "en", "EMAIL");
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existingSettings));
        when(userSettingsRepository.save(any(UserSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSettingsEntity result = userService.bindTelegram(1L, 456L);

        assertThat(result.getTelegramId()).isEqualTo(456L);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getTheme()).isEqualTo("WHITE");
        assertThat(result.getLanguage()).isEqualTo("en");
        assertThat(result.getNotificationMethod()).isEqualTo("EMAIL");
    }

    @Test
    void getUserByTelegramId_WhenSettingsExist_ShouldReturnUser() {
        when(userSettingsRepository.findByTelegramId(123L)).thenReturn(Optional.of(settings));

        UserEntity result = userService.getUserByTelegramId(123L);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void getUserByTelegramId_WhenSettingsNotFound_ShouldThrowException() {
        when(userSettingsRepository.findByTelegramId(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByTelegramId(123L))
                .isInstanceOf(UserSettingsNotFoundException.class)
                .hasMessageContaining("User Settings not found by telegramId: 123");
    }

    @Test
    void getTelegramIdByUserId_WhenSettingsExist_ShouldReturnTelegramId() {
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        Long result = userService.getTelegramIdByUserId(1L);

        assertThat(result).isEqualTo(123L);
    }

    @Test
    void getTelegramIdByUserId_WhenSettingsNotFound_ShouldThrowException() {
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        // FIX: сервис теперь кидает UserSettingsNotFoundException, а не UserNotFoundException
        assertThatThrownBy(() -> userService.getTelegramIdByUserId(1L))
                .isInstanceOf(UserSettingsNotFoundException.class);
    }

    @Test
    void getUserSettingsByUserId_WhenSettingsExist_ShouldReturnSettings() {
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        UserSettingsEntity result = userService.getUserSettingsByUserId(1L);

        assertThat(result).isEqualTo(settings);
    }

    @Test
    void getUserSettingsByUserId_WhenSettingsNotFound_ShouldThrowException() {
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        // FIX: сервис теперь кидает UserSettingsNotFoundException, а не UserNotFoundException
        assertThatThrownBy(() -> userService.getUserSettingsByUserId(1L))
                .isInstanceOf(UserSettingsNotFoundException.class);
    }
}