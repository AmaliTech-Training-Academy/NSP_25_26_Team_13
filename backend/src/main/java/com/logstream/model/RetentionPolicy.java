package com.logstream.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "retention_policies")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private int retentionDays;

    @Column(nullable = false)
    private boolean archiveEnabled;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
