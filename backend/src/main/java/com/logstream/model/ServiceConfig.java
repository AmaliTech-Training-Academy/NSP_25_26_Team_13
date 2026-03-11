package com.logstream.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "service_configs", indexes = {
        @Index(name = "idx_service_configs_service_name", columnList = "serviceName", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String serviceName;

    @Column(nullable = false)
    private int retentionDays;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

