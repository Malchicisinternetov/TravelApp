package com.example.travelapp;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;
import java.util.TimeZone;

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

    public Long getId() {
        return id;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public UserEntity() {}

}
