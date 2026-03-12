package com.logstream.repository;

import com.logstream.model.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {
    Optional<RetentionPolicy> findByServiceName(String serviceName);

    List<RetentionPolicy> findByServiceNameIn(Set<String> serviceNames);

    boolean existsByServiceNameIgnoreCase(String serviceName);

    @Modifying
    @Query(value = "INSERT INTO retention_policies (service_name, retention_days, archive_enabled) VALUES (:name, 30, false) ON CONFLICT (service_name) DO NOTHING", nativeQuery = true)
    void insertIgnore(@Param("name") String serviceName);
}