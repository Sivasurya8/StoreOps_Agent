package com.kiranapilot.exception;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class InsufficientStockException extends KiranaPilotException {
    private final String productName;
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientStockException(String productName, BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient stock for '%s': requested %s, but only %s available in inventory.", 
                productName, requested.stripTrailingZeros().toPlainString(), available.stripTrailingZeros().toPlainString()));
        this.productName = productName;
        this.requested = requested;
        this.available = available;
    }
}
