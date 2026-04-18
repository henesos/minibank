package com.minibank.transaction.config;

import com.minibank.transaction.saga.SagaEvent;
import com.minibank.transaction.saga.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Saga Event Consumer - Listens for events from Account Service.
 * 
 * Handles:
 * - DEBIT_SUCCESS / DEBIT_FAILURE
 * - CREDIT_SUCCESS / CREDIT_FAILURE
 * - COMPENSATE_SUCCESS / COMPENSATE_FAILURE
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventConsumer {

    private final SagaOrchestrator sagaOrchestrator;

    /**
     * Handles events from Account Service.
     */
    @KafkaListener(
        topics = "${app.kafka.topic.events:saga-events}",
        groupId = "${spring.kafka.consumer.group-id:transaction-service-group}",
        containerFactory = "sagaEventListenerFactory"
    )
    public void handleSagaEvent(SagaEvent event) {
        log.info("Received saga event: type={}, sagaId={}", event.getEventType(), event.getSagaId());

        try {
            switch (event.getEventType()) {
                case SagaEvent.EventType.DEBIT_SUCCESS:
                    sagaOrchestrator.handleDebitSuccess(event);
                    break;
                    
                case SagaEvent.EventType.DEBIT_FAILURE:
                    sagaOrchestrator.handleDebitFailure(event);
                    break;
                    
                case SagaEvent.EventType.CREDIT_SUCCESS:
                    sagaOrchestrator.handleCreditSuccess(event);
                    break;
                    
                case SagaEvent.EventType.CREDIT_FAILURE:
                    sagaOrchestrator.handleCreditFailure(event);
                    break;
                    
                case SagaEvent.EventType.COMPENSATE_SUCCESS:
                    sagaOrchestrator.handleCompensateSuccess(event);
                    break;
                    
                case SagaEvent.EventType.COMPENSATE_FAILURE:
                    sagaOrchestrator.handleCompensateFailure(event);
                    break;
                    
                default:
                    log.warn("Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing saga event: type={}, sagaId={}, error={}", 
                    event.getEventType(), event.getSagaId(), e.getMessage(), e);
        }
    }
}
