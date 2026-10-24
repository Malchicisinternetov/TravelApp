package com.example.userservice;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;

@Setter
@Getter
@Table(name="users")
@Entity
public class UserEntity {
    @Id
    @Column(name="id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="timezone")
    private ZoneId timezone;

    @Column(name="currency")
    private Currency currency;

    @Column(name="amount")
    private BigDecimal amount;

    @Column(name="name")
    @Size(max = 255)
    private String name;

    // Nullable for existing profiles created before registration was introduced.
    @JsonIgnore
    @Column(name = "username", unique = true, length = 50)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash", length = 60)
    private String passwordHash;
    
    public UserEntity(Long id, ZoneId timezone, Currency currency, BigDecimal amount, String name) {
        this.id = id;
        this.timezone = timezone;
        this.currency = currency;
        this.amount = amount;
        this.name = name;
    }

    public UserEntity() {}

}
