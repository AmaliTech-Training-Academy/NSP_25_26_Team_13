package com.logstream.repository;

import com.logstream.model.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {
    Optional<RetentionPolicy> findByServiceName(String serviceName);

    List<RetentionPolicy> findByServiceNameIn(Set<String> serviceNames);
}