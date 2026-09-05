package com.kiranapilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftBillDto {
    private Long chatId;
    private Long customerId;
    private String customerName;

    @Builder.Default
    private List<DraftBillItemDto> items = new ArrayList<>();

    @Builder.Default
    private String paymentMode = "UPI";

    private String paymentReference;

    @Builder.Default
    private BigDecimal totalTaxable = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalCgst = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalSgst = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalTax = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    public void recalculateTotals() {
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal grand = BigDecimal.ZERO;

        if (items != null) {
            for (DraftBillItemDto item : items) {
                if (item.getTaxableValue() != null) taxable = taxable.add(item.getTaxableValue());
                if (item.getCgstAmount() != null) cgst = cgst.add(item.getCgstAmount());
                if (item.getSgstAmount() != null) sgst = sgst.add(item.getSgstAmount());
                if (item.getTotalAmount() != null) grand = grand.add(item.getTotalAmount());
            }
        }
        tax = cgst.add(sgst);
        this.totalTaxable = taxable;
        this.totalCgst = cgst;
        this.totalSgst = sgst;
        this.totalTax = tax;
        this.grandTotal = grand;
    }
}
