package com.example.PayRouteBackend.merchant;


import com.example.PayRouteBackend.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Merchant extends BaseEntity{

    @Column(nullable = false , length = 100)
    public String name;

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
