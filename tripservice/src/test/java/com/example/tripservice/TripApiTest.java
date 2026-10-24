package com.example.tripservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trips;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=test-only-signing-key-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class TripApiTest {
    private static final String TRIP = """
            {
              "name": "Connecting flight",
              "description": "Manual route",
              "legs": [
                {
                  "departurePoint": "Moscow SVO", "destinationPoint": "Istanbul IST",
                  "departureTime": "2026-11-01T10:00:00", "arrivalTime": "2026-11-01T14:00:00",
                  "departureZone": "Europe/Moscow", "arrivalZone": "Europe/Istanbul"
                },
                {
                  "departurePoint": "Istanbul IST", "destinationPoint": "London LHR",
                  "departureTime": "2026-11-01T16:00:00", "arrivalTime": "2026-11-01T17:00:00",
                  "departureZone": "Europe/Istanbul", "arrivalZone": "Europe/London"
                }
              ]
            }
            """;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TripRepository repository;
    @Autowired SecretKey jwtSigningKey;

    private String bearer(long userId) {
        return bearerToken(jwtSigningKey, Long.toString(userId), "travelapp", "travelapp-api",
                Instant.now().plusSeconds(300));
    }

    private String bearerToken(SecretKey key, String subject, String issuer, String audience, Instant expires) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(subject).issuer(issuer)
                .audience(List.of(audience)).issuedAt(expires.minusSeconds(300)).expiresAt(expires).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void headerCannotAuthenticateOrOverrideTokenIdentity() throws Exception {
        long id = createTrip();
        mvc.perform(get("/trips/" + id).header("X-User-Id", 1))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(9)).header("X-User-Id", 1))
                .andExpect(status().isForbidden());
        mvc.perform(post("/trips").header("Authorization", bearer(9)).header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(TRIP))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.participants[0].userId").value(9));
    }

    @Test
    void invalidExpiredOrForgedTokensAreRejected() throws Exception {
        Instant validUntil = Instant.now().plusSeconds(300);
        SecretKey wrongKey = new SecretKeySpec(
                "different-test-signing-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        for (String authorization : List.of(
                "Bearer invalid-token",
                bearerToken(wrongKey, "1", "travelapp", "travelapp-api", validUntil),
                bearerToken(jwtSigningKey, "1", "travelapp", "travelapp-api", Instant.now().minusSeconds(120)),
                bearerToken(jwtSigningKey, "1", "another-issuer", "travelapp-api", validUntil),
                bearerToken(jwtSigningKey, "1", "travelapp", "another-audience", validUntil),
                bearerToken(jwtSigningKey, "not-a-user-id", "travelapp", "travelapp-api", validUntil))) {
            mvc.perform(get("/trips").header("Authorization", authorization))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void requiredClaimsAndNotBeforeAreChecked() throws Exception {
        Instant now = Instant.now();
        for (JwtClaimsSet claims : List.of(
                JwtClaimsSet.builder().subject("1").issuer("travelapp")
                        .expiresAt(now.plusSeconds(300)).build(),
                JwtClaimsSet.builder().subject("1").issuer("travelapp")
                        .audience(List.of("travelapp-api")).build(),
                JwtClaimsSet.builder().subject("1").issuer("travelapp")
                        .audience(List.of("travelapp-api")).notBefore(now.plusSeconds(300))
                        .expiresAt(now.plusSeconds(600)).build())) {
            String token = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey))
                    .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
            mvc.perform(get("/trips").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createAndReadPersistOrderedLegsAndOwner() throws Exception {
        long id = createTrip();
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].userId").value(1))
                .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.legs.length()").value(2))
                .andExpect(jsonPath("$.legs[0].departurePoint").value("Moscow SVO"))
                .andExpect(jsonPath("$.legs[1].destinationPoint").value("London LHR"))
                .andExpect(jsonPath("$.legs[1].arrivalTime").value("2026-11-01T17:00:00"))
                .andExpect(jsonPath("$.legs[1].arrivalZone").value("Europe/London"));
    }

    @Test
    void memberCanEditButCannotManageParticipantsOrDeleteTrip() throws Exception {
        long id = createTrip();
        addParticipant(id, 2, "MEMBER").andExpect(status().isCreated());
        mvc.perform(put("/trips/" + id).header("Authorization", bearer(2))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIP.replace("Manual route", "Updated")))
                .andExpect(status().isOk());
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(1)))
                .andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.participants.length()").value(2));
        mvc.perform(post("/trips/" + id + "/participants").header("Authorization", bearer(2))
                        .contentType(MediaType.APPLICATION_JSON).content(participant(3, "OWNER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/trips/" + id + "/participants/2/role").header("Authorization", bearer(2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/trips/" + id + "/participants/1").header("Authorization", bearer(2)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/trips/" + id).header("Authorization", bearer(2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void readOnlyCanReadButCannotEdit() throws Exception {
        long id = createTrip();
        addParticipant(id, 2, "READ_ONLY").andExpect(status().isCreated());
        mvc.perform(get("/trips/" + id + "/participants").header("Authorization", bearer(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put("/trips/" + id).header("Authorization", bearer(2))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIP))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/trips/" + id).header("Authorization", bearer(2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsiderCannotReadAndListOnlyContainsOwnTrips() throws Exception {
        long id = createTrip();
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(9))).andExpect(status().isForbidden());
        mvc.perform(get("/trips").header("Authorization", bearer(9)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        addParticipant(id, 9, "READ_ONLY").andExpect(status().isCreated());
        mvc.perform(get("/trips").header("Authorization", bearer(9)))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(delete("/trips/" + id + "/participants/9").header("Authorization", bearer(1)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(9))).andExpect(status().isForbidden());
    }

    @Test
    void lastOwnerCannotBeRemovedOrDemotedButOwnershipCanBeTransferred() throws Exception {
        long id = createTrip();
        mvc.perform(delete("/trips/" + id + "/participants/1").header("Authorization", bearer(1)))
                .andExpect(status().isConflict());
        changeRole(id, 1, 1, "MEMBER").andExpect(status().isConflict());
        addParticipant(id, 2, "MEMBER").andExpect(status().isCreated());
        changeRole(id, 1, 2, "OWNER").andExpect(status().isOk());
        changeRole(id, 1, 1, "READ_ONLY").andExpect(status().isOk());
        mvc.perform(delete("/trips/" + id + "/participants/1").header("Authorization", bearer(2)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/trips/" + id).header("Authorization", bearer(2)))
                .andExpect(status().isNoContent());
        assertThat(repository.count()).isZero();
    }

    @Test
    void duplicateParticipantIsConflictAndUnknownResourcesAreNotFound() throws Exception {
        long id = createTrip();
        addParticipant(id, 1, "MEMBER").andExpect(status().isConflict());
        mvc.perform(get("/trips/999999").header("Authorization", bearer(1))).andExpect(status().isNotFound());
        changeRole(id, 1, 999, "MEMBER").andExpect(status().isNotFound());
        mvc.perform(delete("/trips/" + id + "/participants/999").header("Authorization", bearer(1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPayloadsAndIdentityAreBadRequests() throws Exception {
        for (String body : new String[] {
                "{}", TRIP.replace("Connecting flight", " "),
                TRIP.replace("Moscow SVO", " "),
                TRIP.replace("Europe/London", "Invalid/Zone"),
                TRIP.replace("2026-11-01T14:00:00", "2026-11-01T09:00:00"),
                TRIP.replace("2026-11-01T16:00:00", "2026-11-01T13:00:00"),
                TRIP.replace("2026-11-01T10:00:00", "invalid")
        }) {
            mvc.perform(post("/trips").header("Authorization", bearer(1))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(post("/trips").contentType(MediaType.APPLICATION_JSON).content(TRIP))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/trips").header("Authorization", bearer(-1))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIP))
                .andExpect(status().isUnauthorized());
        long id = createTrip();
        addParticipant(id, 0, "MEMBER").andExpect(status().isBadRequest());
        addParticipant(id, 2, "INVALID").andExpect(status().isBadRequest());
        changeRole(id, 1, 1, null).andExpect(status().isBadRequest());
    }

    @Test
    void chronologyUsesActualInstantsAcrossTimeZones() throws Exception {
        // London arrival is earlier on the clock but later in UTC than Istanbul departure.
        String route = TRIP.replace("2026-11-01T17:00:00", "2026-11-01T15:00:00");
        mvc.perform(post("/trips").header("Authorization", bearer(1))
                        .contentType(MediaType.APPLICATION_JSON).content(route))
                .andExpect(status().isCreated());
    }

    @Test
    void daylightSavingGapAndOverlapAreRejected() throws Exception {
        for (String time : new String[]{"2026-03-29T01:30:00", "2026-10-25T01:30:00"}) {
            mvc.perform(post("/trips").header("Authorization", bearer(1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TRIP.replace("2026-11-01T17:00:00", time)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Local time is nonexistent or ambiguous in zone Europe/London"));
        }
    }

    @Test
    void updateReplacesRouteAndDeleteCleansChildCollections() throws Exception {
        long id = createTrip();
        String replacement = """
                {"name":"New route","legs":[{
                  "departurePoint":"Paris","destinationPoint":"Rome",
                  "departureTime":"2026-12-01T10:00:00","arrivalTime":"2026-12-01T12:00:00",
                  "departureZone":"Europe/Paris","arrivalZone":"Europe/Rome"
                }]}
                """;
        mvc.perform(put("/trips/" + id).header("Authorization", bearer(1))
                        .contentType(MediaType.APPLICATION_JSON).content(replacement))
                .andExpect(status().isOk());
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(1)))
                .andExpect(jsonPath("$.legs.length()").value(1))
                .andExpect(jsonPath("$.legs[0].departurePoint").value("Paris"));
        mvc.perform(delete("/trips/" + id).header("Authorization", bearer(1))).andExpect(status().isNoContent());
        mvc.perform(get("/trips/" + id).header("Authorization", bearer(1))).andExpect(status().isNotFound());
    }

    private long createTrip() throws Exception {
        String json = mvc.perform(post("/trips").header("Authorization", bearer(1))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIP))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    private ResultActions addParticipant(long id, long userId, String role) throws Exception {
        return mvc.perform(post("/trips/" + id + "/participants").header("Authorization", bearer(1))
                .contentType(MediaType.APPLICATION_JSON).content(participant(userId, role)));
    }

    private String participant(long userId, String role) {
        return "{\"userId\":" + userId + ",\"role\":\"" + role + "\"}";
    }

    private ResultActions changeRole(long id, long actorId, long userId, String role) throws Exception {
        return mvc.perform(put("/trips/" + id + "/participants/" + userId + "/role")
                .header("Authorization", bearer(actorId)).contentType(MediaType.APPLICATION_JSON)
                .content(role == null ? "{}" : "{\"role\":\"" + role + "\"}"));
    }
}
