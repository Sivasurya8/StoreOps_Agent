package com.kiranapilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GstBreakupDto {
    private BigDecimal gstRate;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal totalTax;
}
