package com.callaudit.analytics.repository;

import com.callaudit.analytics.model.AgentPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentPerformanceRepository extends JpaRepository<AgentPerformance, Long> {

    /**
     * Find performance metrics for a specific agent within a time range
     */
    List<AgentPerformance> findByAgentIdAndTimeBetweenOrderByTimeDesc(
            String agentId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * Find the most recent performance record for an agent
     */
    Optional<AgentPerformance> findFirstByAgentIdOrderByTimeDesc(String agentId);

    /**
     * Get top performing agents by average quality score
     */
    @Query("SELECT ap FROM AgentPerformance ap WHERE ap.time >= :since " +
           "ORDER BY ap.avgQualityScore DESC")
    List<AgentPerformance> findTopAgentsByQualityScore(
            @Param("since") LocalDateTime since
    );

    /**
     * Get aggregated metrics for all agents in a time range
     */
    @Query("SELECT ap FROM AgentPerformance ap WHERE ap.time BETWEEN :startTime AND :endTime " +
           "ORDER BY ap.time DESC")
    List<AgentPerformance> findAllByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Calculate average quality score across all agents
     */
    @Query("SELECT AVG(ap.avgQualityScore) FROM AgentPerformance ap WHERE ap.time >= :since")
    Double getAverageQualityScore(@Param("since") LocalDateTime since);

    /**
     * Calculate average compliance pass rate
     */
    @Query("SELECT AVG(ap.compliancePassRate) FROM AgentPerformance ap WHERE ap.time >= :since")
    Double getAverageCompliancePassRate(@Param("since") LocalDateTime since);

    /**
     * Calculate average sentiment score
     */
    @Query("SELECT AVG(ap.avgSentimentScore) FROM AgentPerformance ap WHERE ap.time >= :since")
    Double getAverageSentimentScore(@Param("since") LocalDateTime since);

    /**
     * Calculate average customer satisfaction
     */
    @Query("SELECT AVG(ap.avgCustomerSatisfaction) FROM AgentPerformance ap WHERE ap.time >= :since")
    Double getAverageCustomerSatisfaction(@Param("since") LocalDateTime since);

    /**
     * Get total calls processed
     */
    @Query("SELECT SUM(ap.callsProcessed) FROM AgentPerformance ap WHERE ap.time >= :since")
    Long getTotalCallsProcessed(@Param("since") LocalDateTime since);

    /**
     * Get distinct agent count
     */
    @Query("SELECT COUNT(DISTINCT ap.agentId) FROM AgentPerformance ap WHERE ap.time >= :since")
    Long getDistinctAgentCount(@Param("since") LocalDateTime since);

    /**
     * Get trend data for a specific metric
     */
    @Query("SELECT ap FROM AgentPerformance ap WHERE ap.time BETWEEN :startTime AND :endTime " +
           "ORDER BY ap.time ASC")
    List<AgentPerformance> getTrendData(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
