package com.minibank.transaction.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when daily transfer limit is exceeded.
 */
public class DailyLimitExceededException extends TransactionServiceException {

    /** Constructor. */
    public DailyLimitExceededException(BigDecimal attempted, BigDecimal dailyLimit, BigDecimal alreadyTransferred) {
        super(String.format("Daily transfer limit exceeded. Attempted: %s, Already transferred: %s, Limit: %s",
              attempted, alreadyTransferred, dailyLimit),
              org.springframework.http.HttpStatus.BAD_REQUEST,
              "DAILY_LIMIT_EXCEEDED");
    }
}
