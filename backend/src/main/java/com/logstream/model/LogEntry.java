package com.logstream.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "log_entries", indexes = {
        @Index(name = "idx_service_name", columnList = "serviceName"),
        @Index(name = "idx_level", columnList = "level"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(LogEntryId.class)
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Id
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    private String source;
    private String traceId;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
    }
}