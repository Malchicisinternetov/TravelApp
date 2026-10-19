package com.example.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserControllerTest.JacksonConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private final UserEntity user = new UserEntity(
            1L, ZoneId.of("UTC"), Currency.getInstance("USD"), BigDecimal.TEN, "John");

    private final UserSettingsEntity settings = new UserSettingsEntity(
            10L, user, 123L, "WHITE", "ru", "EMAIL");
    
    @Test
    void getUserById_ShouldReturnUser() throws Exception {
        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("John"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getUserById_WhenNotFound_ShouldReturn404() throws Exception {
        when(userService.getUserById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found: 99"));
    }

    @Test
    void createUser_ShouldReturnCreatedUser() throws Exception {
        UserEntity newUser = new UserEntity(
                null, ZoneId.of("UTC"), Currency.getInstance("USD"), BigDecimal.TEN, "John");
        UserEntity savedUser = new UserEntity(
                1L, ZoneId.of("UTC"), Currency.getInstance("USD"), BigDecimal.TEN, "John");
        when(userService.createUser(any(UserEntity.class))).thenReturn(savedUser);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("John"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void updateUser_ShouldReturnUpdatedUser() throws Exception {
        UserEntity updatedUser = new UserEntity(
                1L, ZoneId.of("Europe/Moscow"), Currency.getInstance("EUR"),
                BigDecimal.valueOf(20), "Jane");
        when(userService.updateUser(eq(1L), any(UserEntity.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Jane"))
                .andExpect(jsonPath("$.timezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void deleteUser_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }

    @Test
    void deleteUser_WhenNotFound_ShouldReturn404() throws Exception {
        org.mockito.Mockito.doThrow(new UserNotFoundException(99L))
                .when(userService).deleteUser(99L);

        mockMvc.perform(delete("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
    
    @Test
    void getUserSettings_ShouldReturnSettingsResponse() throws Exception {
        when(userService.getUserSettingsByUserId(1L)).thenReturn(settings);

        mockMvc.perform(get("/users/1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.telegramId").value(123L))
                .andExpect(jsonPath("$.theme").value("WHITE"))
                .andExpect(jsonPath("$.language").value("ru"))
                .andExpect(jsonPath("$.notificationMethod").value("EMAIL"))
                // DTO не должно утекать вложенный user
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void getUserSettings_WhenNotFound_ShouldReturn404() throws Exception {
        when(userService.getUserSettingsByUserId(99L))
                .thenThrow(new UserSettingsNotFoundException(99L));

        mockMvc.perform(get("/users/99/settings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateUserSettings_ShouldReturnUpdatedSettings() throws Exception {
        UserSettingsEntity updated = new UserSettingsEntity(
                10L, user, 456L, "BLACK", "en", "SMS");
        when(userService.updateUserSettings(eq(1L), any(UserSettingsEntity.class)))
                .thenReturn(updated);

        UpdateUserSettingsRequest request =
                new UpdateUserSettingsRequest(456L, "BLACK", "en", "SMS");

        mockMvc.perform(put("/users/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.telegramId").value(456L))
                .andExpect(jsonPath("$.theme").value("BLACK"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.notificationMethod").value("SMS"));
    }
    
    @Test
    void bindTelegram_ShouldReturnUpdatedSettings() throws Exception {
        UserSettingsEntity bound = new UserSettingsEntity(
                10L, user, 999L, "WHITE", "ru", "EMAIL");
        when(userService.bindTelegram(1L, 999L)).thenReturn(bound);

        BindTelegramRequest request = new BindTelegramRequest(999L);

        mockMvc.perform(post("/users/1/settings/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramId").value(999L))
                .andExpect(jsonPath("$.theme").value("WHITE"));
    }

    @Test
    void getTelegramIdByUserId_ShouldReturnTelegramId() throws Exception {
        when(userService.getTelegramIdByUserId(1L)).thenReturn(123L);

        mockMvc.perform(get("/users/1/settings/telegram"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramId").value(123L));
    }

    @Test
    void getUserByTelegramId_ShouldReturnUser() throws Exception {
        when(userService.getUserByTelegramId(123L)).thenReturn(user);

        mockMvc.perform(get("/users/by-telegram/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("John"));
    }

    @Test
    void getUserByTelegramId_WhenNotFound_ShouldReturn404() throws Exception {
        when(userService.getUserByTelegramId(999L))
                .thenThrow(new UserSettingsNotFoundException(999L));

        mockMvc.perform(get("/users/by-telegram/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @TestConfiguration
    static class JacksonConfig {

        @Bean
        JsonMapperBuilderCustomizer currencyCustomizer() {
            return builder -> {
                SimpleModule module = new SimpleModule();
                module.addSerializer(Currency.class, ToStringSerializer.instance);
                module.addDeserializer(Currency.class, new ValueDeserializer<Currency>() {
                    @Override
                    public Currency deserialize(JsonParser p, DeserializationContext ctxt) {
                        return Currency.getInstance(p.getValueAsString());
                    }
                });
                builder.addModule(module);
            };
        }
    }
}