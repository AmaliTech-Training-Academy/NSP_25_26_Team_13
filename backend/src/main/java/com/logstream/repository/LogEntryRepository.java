package com.logstream.repository;

import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {

    Page<LogEntry> findByServiceName(String serviceName, Pageable pageable);

    Page<LogEntry> findByLevel(LogLevel level, Pageable pageable);

    Page<LogEntry> findByServiceNameAndLevel(String serviceName, LogLevel level, Pageable pageable);

    Page<LogEntry> findByTimestampBetween(Instant start, Instant end, Pageable pageable);

    Page<LogEntry> findByMessageContainingIgnoreCase(String keyword, Pageable pageable);

    long countByServiceNameAndLevel(String serviceName, LogLevel level);

    long countByServiceName(String serviceName);

    @Query("SELECT l.serviceName, l.level, COUNT(l) FROM LogEntry l GROUP BY l.serviceName, l.level")
    List<Object[]> countGroupByServiceAndLevel();
    @Query("SELECT l.serviceName, COUNT(l) FROM LogEntry l WHERE l.level = 'ERROR' AND l.createdAt >= :since GROUP BY l.serviceName")
    List<Object[]> countErrorsByServiceAndCreatedAtAfter(@Param("since") Instant since);

    @Query("SELECT DISTINCT l.serviceName FROM LogEntry l")
    List<String> findDistinctServiceNames();


    @Query("SELECT l.serviceName, COUNT(l) FROM LogEntry l WHERE l.createdAt >= :since GROUP BY l.serviceName")
    List<Object[]> countByServiceAndCreatedAtAfter(@Param("since") Instant since);

    @Query("SELECT l FROM LogEntry l WHERE " +
           "(:serviceName IS NULL OR l.serviceName = :serviceName) AND " +
           "(:level IS NULL OR l.level = :level) AND " +
           "(:start IS NULL OR l.timestamp >= :start) AND " +
           "(:end IS NULL OR l.timestamp <= :end) AND " +
           "(:keyword IS NULL OR LOWER(l.message) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<LogEntry> searchWithFilters(@Param("serviceName") String serviceName,
                                     @Param("level") LogLevel level,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM LogEntry l WHERE l.serviceName = :serviceName AND l.createdAt < :cutoff")
    int deleteByServiceNameAndCreatedAtBefore(@Param("serviceName") String serviceName, @Param("cutoff") Instant cutoff);

    @Modifying
    @Transactional
    @Query("DELETE FROM LogEntry l WHERE l.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
