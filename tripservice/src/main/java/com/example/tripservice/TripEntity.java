package com.example.tripservice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
@Getter
@Setter
public class TripEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(length = 4000)
    private String description;
    @ElementCollection
    @CollectionTable(name = "trip_participants", joinColumns = @JoinColumn(name = "trip_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
    private List<TripParticipant> participants = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "trip_legs", joinColumns = @JoinColumn(name = "trip_id"))
    @OrderColumn(name = "leg_order")
    private List<TripLeg> legs = new ArrayList<>();
}
