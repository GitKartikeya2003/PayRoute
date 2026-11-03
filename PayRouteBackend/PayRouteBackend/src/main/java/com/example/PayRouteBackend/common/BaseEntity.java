package com.example.PayRouteBackend.common;


import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BaseEntity {


    @Id
    @Column
    public String id;

    @Column(nullable = false , updatable = false)
    public Instant createdAt = Instant.now();

}
