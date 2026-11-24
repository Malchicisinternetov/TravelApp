package com.example.userservice;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:websessions;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=test-only-signing-key-at-least-32-bytes-long",
        "security.jwt.ttl-seconds=60", "security.web-session.secure=true"
})
@AutoConfigureMockMvc
class WebSessionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RefreshSessionRepository sessions;
    @Autowired UserRepository users;
    @Autowired UserSettingsRepository settings;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;

    @BeforeEach void clean() { sessions.deleteAll(); settings.deleteAll(); users.deleteAll(); }

    private Cookie registerWeb() throws Exception {
        var result = mvc.perform(post("/auth/register").header("X-TravelApp-Client", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "traveller", "password", "Compass!Trip42", "name", "Alice"))))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String header = result.getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/users/auth", "Max-Age=604800");
        return new Cookie(WebSessionService.COOKIE, header.split(";", 2)[0].split("=", 2)[1]);
    }

    @Test void cookieRefreshWorksWithoutAccessTokenAndLogoutRevokesIt() throws Exception {
        Cookie cookie = registerWeb();
        assertThat(sessions.findAll()).singleElement().satisfies(row -> assertThat(row.getTokenHash()).isNotEqualTo(cookie.getValue()));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/auth/refresh").header("X-TravelApp-Client", "web").cookie(cookie))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.expiresIn").value(60)).andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(post("/auth/logout").header("X-TravelApp-Client", "web").cookie(cookie))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/auth/refresh").header("X-TravelApp-Client", "web").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void refreshRejectsExpiredForgedMissingCredentialsAndRequestsWithoutClientHeader() throws Exception {
        Cookie cookie = registerWeb();
        mvc.perform(post("/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/refresh").header("X-TravelApp-Client", "web")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").header("X-TravelApp-Client", "web")
                .cookie(new Cookie(WebSessionService.COOKIE, "x".repeat(43)))).andExpect(status().isUnauthorized());
        var stored = sessions.findAll().get(0); stored.setExpiresAt(Instant.now().minusSeconds(1)); sessions.saveAndFlush(stored);
        mvc.perform(post("/auth/refresh").header("X-TravelApp-Client", "web").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void directoryReturnsOnlyIdsAndNamesAndRequiresAuthentication() throws Exception {
        Cookie cookie = registerWeb();
        var response = mvc.perform(post("/auth/refresh").header("X-TravelApp-Client", "web").cookie(cookie))
                .andReturn().getResponse().getContentAsString();
        var auth = json.readValue(response, AuthResponse.class);
        String body = json.writeValueAsString(Map.of("userIds", List.of(auth.userId(), 999999L)));
        mvc.perform(post("/users/directory").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/users/directory").header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(auth.userId())).andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].username").doesNotExist()).andExpect(jsonPath("$[0].currency").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test void registrationRejectsWeakPasswordsButLegacyPasswordLoginStillWorks() throws Exception {
        for (String password : List.of("12345678", "123456789012", "abcdefghijk", "OnlyLetters!", "Password123!", "Qwerty123456!")) {
            mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", "weak", "password", password, "name", "Test"))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.count()).isZero();
        registerWeb();
        var legacy = users.findByUsername("traveller").orElseThrow();
        legacy.setPasswordHash(passwords.encode("12345678")); users.saveAndFlush(legacy);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"traveller\",\"password\":\"12345678\"}"))
                .andExpect(status().isOk());
    }
}
