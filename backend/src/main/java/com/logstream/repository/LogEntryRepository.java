package com.logstream.repository;

import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = "SELECT date_trunc('hour', created_at) AS time_bucket, service_name, COUNT(*) AS cnt "
            + "FROM log_entries WHERE level = 'ERROR' AND created_at >= :start AND created_at <= :endTime "
            + "GROUP BY time_bucket, service_name ORDER BY time_bucket ASC", nativeQuery = true)
    List<Object[]> findAllServicesHourlyErrors(
            @Param("start") Instant start,
            @Param("endTime") Instant end);

    @Query(value = "SELECT date_trunc('day', created_at) AS time_bucket, service_name, COUNT(*) AS cnt "
            + "FROM log_entries WHERE level = 'ERROR' AND created_at >= :start AND created_at <= :endTime "
            + "GROUP BY time_bucket, service_name ORDER BY time_bucket ASC", nativeQuery = true)
    List<Object[]> findAllServicesDailyErrors(
            @Param("start") Instant start,
            @Param("endTime") Instant end);

    @Query("SELECT l.message, COUNT(l) AS cnt FROM LogEntry l "
            + "WHERE l.level = 'ERROR' AND l.serviceName = :serviceName "
            + "AND l.createdAt >= :start AND l.createdAt <= :end "
            + "GROUP BY l.message ORDER BY cnt DESC")
    List<Object[]> findCommonErrorsByServiceAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT l.serviceName, l.message, COUNT(l) AS cnt FROM LogEntry l "
            + "WHERE l.level = 'ERROR' AND l.createdAt >= :start AND l.createdAt <= :end "
            + "GROUP BY l.serviceName, l.message ORDER BY cnt DESC")
    List<Object[]> findAllServicesCommonErrors(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = "SELECT date_trunc('hour', created_at) AS time_bucket, service_name, COUNT(*) AS cnt "
            + "FROM log_entries WHERE service_name = :serviceName "
            + "AND created_at >= :start AND created_at <= :endTime "
            + "GROUP BY time_bucket, service_name ORDER BY time_bucket ASC", nativeQuery = true)
    List<Object[]> findHourlyVolume(
            @Param("serviceName") String serviceName,
            @Param("start") Instant start,
            @Param("endTime") Instant end);

    @Query(value = "SELECT date_trunc('day', created_at) AS time_bucket, service_name, COUNT(*) AS cnt "
            + "FROM log_entries WHERE service_name = :serviceName "
            + "AND created_at >= :start AND created_at <= :endTime "
            + "GROUP BY time_bucket, service_name ORDER BY time_bucket ASC", nativeQuery = true)
    List<Object[]> findDailyVolume(
            @Param("serviceName") String serviceName,
            @Param("start") Instant start,
            @Param("endTime") Instant end);

    @Query(value = "SELECT date_trunc('hour', created_at) AS time_bucket, service_name, COUNT(*) AS cnt "
            + "FROM log_entries WHERE created_at >= :start AND created_at <= :endTime "
            + "GROUP BY time_bucket, service_name ORDER BY time_bucket ASC", nativeQuery = true)
    List<Object[]> findAllServicesHourlyVolume(
            @Param("start") Instant start,
            @Param("endTime") Instant end);

    @Query(value = "SELECT date_trunc('day', created_at) AS time_bucket, service_name, COUNT(*) AS cnt "
            + "FROM log_entries WHERE created_at >= :start AND created_at <= :endTime "
            + "GROUP BY time_bucket, service_name ORDER BY time_bucket ASC", nativeQuery = true)
    List<Object[]> findAllServicesDailyVolume(
            @Param("start") Instant start,
            @Param("endTime") Instant end);

    @Query("SELECT l.serviceName, MAX(l.timestamp) FROM LogEntry l GROUP BY l.serviceName")
    List<Object[]> findLastLogTimestampByService();

    // Basic search variants used by SearchService to avoid complex dynamic JPQL issues
    Page<LogEntry> findByServiceNameAndLevelAndTimestampBetweenAndMessageContainingIgnoreCase(
            String serviceName,
            LogLevel level,
            Instant start,
            Instant end,
            String keyword,
            Pageable pageable);

    Page<LogEntry> findByServiceNameAndLevelAndTimestampBetween(
            String serviceName,
            LogLevel level,
            Instant start,
            Instant end,
            Pageable pageable);

    Page<LogEntry> findByServiceNameAndTimestampBetween(
            String serviceName,
            Instant start,
            Instant end,
            Pageable pageable);

    Page<LogEntry> findByLevelAndTimestampBetween(
            LogLevel level,
            Instant start,
            Instant end,
            Pageable pageable);

    Page<LogEntry> findByTimestampBetweenAndMessageContainingIgnoreCase(
            Instant start,
            Instant end,
            String keyword,
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
