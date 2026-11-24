package com.example.tripservice;

import com.example.sharedlib.messaging.*;
import jakarta.validation.Validator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class TripKafkaEndpoint implements RpcEndpoint {
    public record Window(Instant from, Instant to) {}
    private final TripController trips;
    private final DepartureController departures;
    private final RpcSupport auth;
    private final com.example.sharedlib.security.ServiceSecret notificationSecret;
    public TripKafkaEndpoint(TripController trips, DepartureController departures, JwtDecoder decoder, Validator validator,
            @org.springframework.beans.factory.annotation.Value("${security.notifications.secret:}") String secret) {
        this.trips = trips; this.departures = departures; this.auth = new RpcSupport(decoder, validator);
        this.notificationSecret = new com.example.sharedlib.security.ServiceSecret(secret);
    }
    @Override public void authorize(RpcRequest r) {
        if ("GET".equals(r.method()) && "/internal/trips/departures".equals(r.path())) notificationSecret.verify(r.serviceSecret());
        else auth.jwt(r);
    }
    @Override public Object handle(RpcRequest r) {
        try { return dispatch(r); }
        catch (TripException ex) { throw new RpcException(ex.getStatus().value(), ex.getMessage()); }
    }
    private Object dispatch(RpcRequest r) {
        if ("GET".equals(r.method()) && "/internal/trips/departures".equals(r.path())) {
            Window window = auth.body(r, Window.class);
            if (window.from() == null || window.to() == null) throw new RpcException(400, "Invalid time window");
            return departures.departures(r.serviceSecret(), window.from(), window.to());
        }
        var principal = auth.principal(r);
        if ("/trips".equals(r.path())) return switch (r.method()) {
            case "GET" -> trips.list(principal);
            case "POST" -> trips.create(principal, auth.body(r, TripRequest.class)).getBody();
            default -> throw new RpcException(404, "Unknown operation");
        };
        String[] path = r.path().split("/");
        if (path.length < 3 || !"trips".equals(path[1])) throw new RpcException(404, "Unknown operation");
        Long tripId = RpcSupport.id(path[2]);
        if (path.length == 3) return switch (r.method()) {
            case "GET" -> trips.get(tripId, principal);
            case "PUT" -> trips.update(tripId, principal, auth.body(r, TripRequest.class));
            case "DELETE" -> { trips.delete(tripId, principal); yield null; }
            default -> throw new RpcException(404, "Unknown operation");
        };
        if (!"participants".equals(path[3])) throw new RpcException(404, "Unknown operation");
        if (path.length == 4) return switch (r.method()) {
            case "GET" -> trips.participants(tripId, principal);
            case "POST" -> trips.addParticipant(tripId, principal, auth.body(r, AddParticipantRequest.class), r.authorization()).getBody();
            default -> throw new RpcException(404, "Unknown operation");
        };
        if (path.length == 5 && "DELETE".equals(r.method())) {
            trips.removeParticipant(tripId, RpcSupport.id(path[4]), principal); return null;
        }
        if (path.length == 6 && "role".equals(path[5]) && "PUT".equals(r.method()))
            return trips.updateRole(tripId, RpcSupport.id(path[4]), principal, auth.body(r, UpdateParticipantRoleRequest.class));
        throw new RpcException(404, "Unknown operation");
    }
}
