package com.minibank.account.unit;

import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.kafka.SagaCommandConsumer;
import com.minibank.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaCommandConsumerTransactionalTest {

    @Mock
    private AccountService accountService;

    @Mock
    private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Map<String, Object>>> sendFuture;

    @InjectMocks
    private SagaCommandConsumer sagaCommandConsumer;

    private UUID sagaId;
    private UUID transactionId;
    private UUID fromAccountId;
    private BigDecimal amount;
    private AccountResponse mockResponse;

    @BeforeEach
    void setUp() {
        sagaId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        amount = new BigDecimal("100.00");

        mockResponse = AccountResponse.builder()
                .id(fromAccountId)
                .balance(new BigDecimal("1000.00"))
                .currency("TRY")
                .status("ACTIVE")
                .build();

        lenient().when(kafkaTemplate.send(anyString(), anyString(), any(Map.class)))
                .thenReturn(sendFuture);
        lenient().when(accountService.withdraw(any(UUID.class), any(BalanceUpdateRequest.class)))
                .thenReturn(mockResponse);
        lenient().when(accountService.deposit(any(UUID.class), any(BalanceUpdateRequest.class)))
                .thenReturn(mockResponse);
    }

    private Map<String, Object> buildDebitEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "DEBIT_REQUEST");
        event.put("sagaId", sagaId.toString());
        event.put("transactionId", transactionId.toString());
        event.put("fromAccountId", fromAccountId.toString());
        event.put("amount", amount);
        return event;
    }

    @Test
    @DisplayName("Kafka publish failure should throw RuntimeException to trigger rollback")
    void kafkaPublishFailure_ThrowsRuntimeException() {
        Map<String, Object> event = buildDebitEvent();

        doThrow(new RuntimeException("Kafka broker unavailable"))
                .when(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), any(Map.class));

        assertThrows(RuntimeException.class,
                () -> sagaCommandConsumer.consumeSagaCommand(event)
        );
    }

    @Test
    @DisplayName("Kafka publish success should commit without throwing")
    void kafkaPublishSuccess_NoException() {
        Map<String, Object> event = buildDebitEvent();

        assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

        verify(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));
        verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), any(Map.class));
    }

    @Test
    @DisplayName("RuntimeException in accountService should throw to trigger rollback")
    void accountServiceRuntimeException_ThrowsAndRollback() {
        Map<String, Object> event = buildDebitEvent();

        doThrow(new RuntimeException("Database error"))
                .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> sagaCommandConsumer.consumeSagaCommand(event)
        );

        assertTrue(exception.getMessage().contains("Database error"));
    }
}