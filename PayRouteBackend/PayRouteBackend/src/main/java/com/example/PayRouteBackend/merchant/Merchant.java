package com.example.PayRouteBackend.merchant;


import com.example.PayRouteBackend.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class Merchant extends BaseEntity{

    @Column(nullable = false , length = 100)
    String name;

    @Column(nullable = false , length = 160)
    public String email;

    @JsonIgnore
    @Column(nullable = false , length = 64)
    public String apiKeyHash;

    @JsonIgnore
    @Column(nullable = false , length = 64)
    public String webhookSecret;


    @Column(length = 64)
    public String webhookUrl;

    @Column(nullable = false)
    public long balance;






}
