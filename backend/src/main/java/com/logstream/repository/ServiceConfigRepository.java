package com.logstream.repository;

import com.logstream.model.ServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, Long> {
    Optional<ServiceConfig> findByServiceName(String serviceName);
}

