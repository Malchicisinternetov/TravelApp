package com.example.userservice;

import jakarta.persistence.*;
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
    private String name;
    
    public UserEntity(Long id, ZoneId timezone, Currency currency, BigDecimal amount, String name) {
        this.id = id;
        this.timezone = timezone;
        this.currency = currency;
        this.amount = amount;
        this.name = name;
    }

    public UserEntity() {}

}
