package com.example.userservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=test-only-signing-key-at-least-32-bytes-long",
        "security.jwt.ttl-seconds=900",
        "security.bot.secret=test-bot-service-secret-at-least-32-bytes"
})
@AutoConfigureMockMvc
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository repository;
    @Autowired UserSettingsRepository settings;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired TelegramLinkCodeRepository codes;

    @BeforeEach
    void clean() {
        codes.deleteAll();
        settings.deleteAll();
        repository.deleteAll();
    }

    @Test
    void registrationCreatesProfileSettingsAndHashedPasswordAndReturnsUsableToken() throws Exception {
        AuthResponse auth = register("Alice");
        UserEntity stored = repository.findByUsername("alice").orElseThrow();
        assertThat(stored.getId()).isEqualTo(auth.userId());
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret-password");
        assertThat(passwords.matches("secret-password", stored.getPasswordHash())).isTrue();
        assertThat(settings.findByUserId(stored.getId())).isPresent();
        var jwt = decoder.decode(auth.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(stored.getId().toString());
        assertThat(jwt.getAudience()).containsExactly("travelapp-api");
        assertThat(auth.tokenType()).isEqualTo("Bearer");
        assertThat(auth.expiresIn()).isEqualTo(900);
        mvc.perform(get("/users/" + auth.userId()).header("Authorization", bearer(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/users/" + auth.userId() + "/settings").header("Authorization", bearer(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(auth.userId()));
    }

    @Test
    void loginIsCaseInsensitiveAndWrongCredentialsUseSameError() throws Exception {
        AuthResponse auth = register("Alice");
        String json = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "ALICE", "password", "secret-password"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(auth.userId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(decoder.decode(mapper.readTree(json).get("accessToken").asText()).getSubject())
                .isEqualTo(auth.userId().toString());
        for (String username : new String[]{"alice", "unknown"}) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", username, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
    }

    @Test
    void duplicateRegistrationDoesNotCreateAnotherUserOrSettings() throws Exception {
        register("Alice");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("ALICE", "secret-password")))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(settings.count()).isEqualTo(1);
    }

    @Test
    void invalidRegistrationAndMalformedLoginAreBadRequests() throws Exception {
        for (String body : new String[]{
                "{}", "{invalid-json", registration("a", "secret-password"),
                registration("valid-user", "short"), registration("valid-user", "я".repeat(40)),
                registration("bad username", "secret-password")
        }) {
            mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void anonymousRequestsAndLegacyRegistrationAreBlocked() throws Exception {
        AuthResponse auth = register("Alice");
        mvc.perform(get("/users/" + auth.userId()).header("X-User-Id", auth.userId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(post("/users").header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Anonymous account\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenCannotReadUpdateDeleteOrBindTelegramForAnotherUser() throws Exception {
        AuthResponse alice = register("Alice");
        AuthResponse bob = register("Bobby");
        String other = "/users/" + bob.userId();
        mvc.perform(get(other).header("Authorization", bearer(alice))).andExpect(status().isForbidden());
        mvc.perform(put(other).header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(other).header("Authorization", bearer(alice))).andExpect(status().isForbidden());
        mvc.perform(get(other + "/settings").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(put(other + "/settings").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(other + "/settings/telegram").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telegramId\":123}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(other + "/settings/telegram").header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telegramId\":123}"))
                .andExpect(status().isForbidden());
        UserSettingsEntity bobSettings = settings.findByUserId(bob.userId()).orElseThrow();
        bobSettings.setTelegramId(123L);
        settings.saveAndFlush(bobSettings);
        mvc.perform(get("/users/by-telegram/123").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/users/by-telegram/123").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(bob.userId()));
        assertThat(repository.findById(bob.userId()).orElseThrow().getName()).isEqualTo("Bobby");
    }

    @Test
    void updatingProfileCannotReplaceCredentialsAndLoginStillWorks() throws Exception {
        AuthResponse auth = register("Alice");
        String hash = repository.findById(auth.userId()).orElseThrow().getPasswordHash();
        mvc.perform(put("/users/" + auth.userId()).header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999,"name":"Updated","username":"hijacked","passwordHash":"forged"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        UserEntity user = repository.findByUsername("alice").orElseThrow();
        assertThat(user.getPasswordHash()).isEqualTo(hash);
        assertThat(repository.existsByUsername("hijacked")).isFalse();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", "secret-password"))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingOwnAccountRemovesCredentialsAndSettings() throws Exception {
        AuthResponse auth = register("Alice");
        mvc.perform(delete("/users/" + auth.userId()).header("Authorization", bearer(auth)))
                .andExpect(status().isNoContent());
        assertThat(repository.count()).isZero();
        assertThat(settings.count()).isZero();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", "secret-password"))))
                .andExpect(status().isUnauthorized());
    }

    private AuthResponse register(String username) throws Exception {
        String response = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username, "secret-password")))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readValue(response, AuthResponse.class);
    }

    @Test
    void trustedBotCreatesTelegramAccountAndGetsTheSameIdentityOnNextLogin() throws Exception {
        String body = json(Map.of("telegramId", 777, "name", "Telegram user", "create", true));
        mvc.perform(post("/internal/auth/telegram").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/auth/telegram").header("X-Bot-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
        AuthResponse auth = telegramLogin(777, true);
        AuthResponse again = telegramLogin(777, false);
        assertThat(again.userId()).isEqualTo(auth.userId());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(decoder.decode(auth.accessToken()).getSubject()).isEqualTo(auth.userId().toString());
        assertThat(settings.findByTelegramId(777L).orElseThrow().isTelegramVerified()).isTrue();
    }

    @Test
    void oneTimeCodeLinksExistingAccountAndCannotBeReused() throws Exception {
        AuthResponse website = register("Alice");
        String code = linkCode(website);
        assertThat(codes.findAll().get(0).getTokenHash()).isNotEqualTo(code);
        String request = json(Map.of("telegramId", 777, "code", code));
        mvc.perform(post("/internal/auth/telegram/link").header("X-Bot-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/auth/telegram/link")
                        .header("X-Bot-Secret", "test-bot-service-secret-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(website.userId()));
        assertThat(telegramLogin(777, false).userId()).isEqualTo(website.userId());
        mvc.perform(post("/internal/auth/telegram/link")
                        .header("X-Bot-Secret", "test-bot-service-secret-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void expiredCodeAndConflictingIdentityAreRejected() throws Exception {
        AuthResponse website = register("Alice");
        String code = linkCode(website);
        TelegramLinkCode stored = codes.findAll().get(0);
        stored.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        codes.saveAndFlush(stored);
        mvc.perform(post("/internal/auth/telegram/link")
                        .header("X-Bot-Secret", "test-bot-service-secret-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("telegramId", 777, "code", code))))
                .andExpect(status().isBadRequest());
        AuthResponse botAccount = telegramLogin(777, true);
        String fresh = linkCode(website);
        mvc.perform(post("/internal/auth/telegram/link")
                        .header("X-Bot-Secret", "test-bot-service-secret-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("telegramId", 777, "code", fresh))))
                .andExpect(status().isConflict());
        assertThat(telegramLogin(777, false).userId()).isEqualTo(botAccount.userId());
    }

    @Test
    void settingsCannotForgeTelegramIdentityAndUnlinkDisablesBotLogin() throws Exception {
        AuthResponse auth = telegramLogin(777, true);
        mvc.perform(put("/users/" + auth.userId() + "/settings").header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telegramId\":888,\"theme\":\"BLACK\",\"language\":\"ru\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telegramId").value(777));
        assertThat(settings.findByTelegramId(777L).orElseThrow().isTelegramVerified()).isTrue();
        mvc.perform(put("/users/" + auth.userId()).header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"timezone\":\"Europe/Moscow\",\"currency\":\"RUB\",\"amount\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("RUB"));
        mvc.perform(delete("/auth/telegram").header("Authorization", bearer(auth))).andExpect(status().isNoContent());
        mvc.perform(post("/internal/auth/telegram")
                        .header("X-Bot-Secret", "test-bot-service-secret-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("telegramId", 777, "name", "User", "create", false))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unverifiedLegacyBindingDoesNotAuthenticateAsItsOwner() throws Exception {
        AuthResponse website = register("Alice");
        UserSettingsEntity legacy = settings.findByUserId(website.userId()).orElseThrow();
        legacy.setTelegramId(777L);
        settings.saveAndFlush(legacy);
        AuthResponse telegram = telegramLogin(777, true);
        assertThat(telegram.userId()).isNotEqualTo(website.userId());
        assertThat(settings.findByUserId(website.userId()).orElseThrow().getTelegramId()).isNull();
    }

    private AuthResponse telegramLogin(long telegramId, boolean create) throws Exception {
        String response = mvc.perform(post("/internal/auth/telegram")
                        .header("X-Bot-Secret", "test-bot-service-secret-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("telegramId", telegramId, "name", "Telegram user", "create", create))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readValue(response, AuthResponse.class);
    }

    private String linkCode(AuthResponse auth) throws Exception {
        String response = mvc.perform(post("/auth/telegram/code").header("Authorization", bearer(auth)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("code").asText();
    }

    private String registration(String username, String password) throws Exception {
        return json(Map.of("username", username, "password", password, "name", username));
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }
}
