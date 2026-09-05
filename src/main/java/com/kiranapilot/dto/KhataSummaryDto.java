package com.kiranapilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KhataSummaryDto {
    private Long customerId;
    private String customerName;
    private String phone;
    private BigDecimal currentBalance;
    private List<KhataEntryDto> recentTransactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KhataEntryDto {
        private Long id;
        private String type;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private String paymentMode;
        private String notes;
        private OffsetDateTime date;
    }
}
