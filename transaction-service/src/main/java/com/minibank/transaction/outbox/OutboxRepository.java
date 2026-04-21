package com.minibank.transaction.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox Repository for database operations.
 * 
 * Part of the Outbox Pattern implementation.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Finds all pending events ordered by creation time.
     * 
     * @return list of pending events
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    /**
     * Finds pending events with limit.
     * 
     * @param limit maximum number of events to return
     * @return list of pending events
     */
    @Query(value = "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
    List<OutboxEvent> findPendingEventsWithLimit(@Param("limit") int limit);

    /**
     * Finds events by saga ID.
     * 
     * @param sagaId the saga correlation ID
     * @return list of events
     */
    List<OutboxEvent> findBySagaId(UUID sagaId);

    /**
     * Finds events by transaction ID.
     * 
     * @param transactionId the transaction ID
     * @return list of events
     */
    List<OutboxEvent> findByTransactionId(UUID transactionId);

    /**
     * Finds failed events that can be retried.
     * 
     * @param maxRetryCount maximum retry count
     * @return list of retryable events
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetryCount ORDER BY e.createdAt ASC")
    List<OutboxEvent> findRetryableEvents(@Param("maxRetryCount") int maxRetryCount);

    /**
     * Marks an event as sent.
     * 
     * @param eventId the event ID
     * @param sentAt the timestamp when sent
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'SENT', e.sentAt = :sentAt WHERE e.id = :eventId")
    int markAsSent(@Param("eventId") UUID eventId, @Param("sentAt") LocalDateTime sentAt);

    /**
     * Marks an event as failed.
     * 
     * @param eventId the event ID
     * @param errorMessage the error message
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'FAILED', e.errorMessage = :errorMessage, e.retryCount = e.retryCount + 1 WHERE e.id = :eventId")
    int markAsFailed(@Param("eventId") UUID eventId, @Param("errorMessage") String errorMessage);

    /**
     * Deletes events older than a threshold.
     * Used for cleanup of already processed events.
     * 
     * @param threshold the datetime threshold
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'SENT' AND e.sentAt < :threshold")
    int deleteOldEvents(@Param("threshold") LocalDateTime threshold);

    /**
     * Counts events by status.
     * 
     * @param status the status
     * @return count
     */
    long countByStatus(OutboxEvent.EventStatus status);
}
