package com.kiranapilot.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiranapilot.dto.DraftBillDto;
import com.kiranapilot.dto.DraftBillItemDto;
import com.kiranapilot.entity.*;
import com.kiranapilot.exception.InsufficientStockException;
import com.kiranapilot.exception.InvalidOperationException;
import com.kiranapilot.gst.GstCalculator;
import com.kiranapilot.inventory.InventoryService;
import com.kiranapilot.khata.KhataService;
import com.kiranapilot.repository.BillDraftRepository;
import com.kiranapilot.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillRepository billRepository;
    private final BillDraftRepository draftRepository;
    private final InventoryService inventoryService;
    private final GstCalculator gstCalculator;
    private final KhataService khataService;
    private final ObjectMapper objectMapper;

    @Transactional
    public DraftBillDto getOrCreateDraft(Long chatId) {
        Optional<BillDraft> opt = draftRepository.findById(chatId);
        if (opt.isPresent()) {
            try {
                return objectMapper.readValue(opt.get().getDraftJson(), DraftBillDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse draft for chat {}, creating new.", chatId, e);
            }
        }
        DraftBillDto newDraft = DraftBillDto.builder()
                .chatId(chatId)
                .paymentMode("UPI")
                .items(new ArrayList<>())
                .build();
        saveDraft(newDraft);
        return newDraft;
    }

    @Transactional
    public void saveDraft(DraftBillDto draft) {
        draft.recalculateTotals();
        try {
            String json = objectMapper.writeValueAsString(draft);
            BillDraft entity = BillDraft.builder()
                    .chatId(draft.getChatId())
                    .draftJson(json)
                    .build();
            draftRepository.save(entity);
        } catch (Exception e) {
            log.error("Error serializing draft", e);
            throw new RuntimeException("Failed to persist bill draft", e);
        }
    }

    @Transactional
    public DraftBillDto addItem(Long chatId, String productIdentifier, BigDecimal quantity) {
        DraftBillDto draft = getOrCreateDraft(chatId);
        Product product = inventoryService.findProductEntity(productIdentifier);

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = BigDecimal.ONE;
        }

        // Check if item already in draft, increment quantity
        Optional<DraftBillItemDto> existing = draft.getItems().stream()
                .filter(i -> i.getProductId().equals(product.getId()))
                .findFirst();

        if (existing.isPresent()) {
            BigDecimal newQty = existing.get().getQuantity().add(quantity);
            DraftBillItemDto recalculated = gstCalculator.calculateLineItem(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getUnit(),
                    newQty,
                    product.getSellingPrice(),
                    product.getMrp(),
                    product.getGstRate(),
                    product.getHsnCode()
            );
            draft.getItems().remove(existing.get());
            draft.getItems().add(recalculated);
        } else {
            DraftBillItemDto item = gstCalculator.calculateLineItem(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getUnit(),
                    quantity,
                    product.getSellingPrice(),
                    product.getMrp(),
                    product.getGstRate(),
                    product.getHsnCode()
            );
            draft.getItems().add(item);
        }

        saveDraft(draft);
        return draft;
    }

    @Transactional
    public DraftBillDto updateItemQuantity(Long chatId, String productIdentifier, BigDecimal newQuantity) {
        DraftBillDto draft = getOrCreateDraft(chatId);
        Product product = inventoryService.findProductEntity(productIdentifier);

        if (newQuantity == null || newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return removeItem(chatId, productIdentifier);
        }

        draft.getItems().removeIf(i -> i.getProductId().equals(product.getId()));

        DraftBillItemDto recalculated = gstCalculator.calculateLineItem(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnit(),
                newQuantity,
                product.getSellingPrice(),
                product.getMrp(),
                product.getGstRate(),
                product.getHsnCode()
        );
        draft.getItems().add(recalculated);

        saveDraft(draft);
        return draft;
    }

    @Transactional
    public DraftBillDto removeItem(Long chatId, String productIdentifier) {
        DraftBillDto draft = getOrCreateDraft(chatId);
        Product product = inventoryService.findProductEntity(productIdentifier);

        draft.getItems().removeIf(i -> i.getProductId().equals(product.getId()));
        saveDraft(draft);
        return draft;
    }

    @Transactional
    public DraftBillDto setPaymentMode(Long chatId, String paymentMode, String reference) {
        DraftBillDto draft = getOrCreateDraft(chatId);
        if (paymentMode != null && !paymentMode.trim().isEmpty()) {
            draft.setPaymentMode(paymentMode.trim().toUpperCase());
        }
        if (reference != null && !reference.trim().isEmpty()) {
            draft.setPaymentReference(reference.trim());
        }
        saveDraft(draft);
        return draft;
    }

    @Transactional
    public DraftBillDto setCustomer(Long chatId, String customerName) {
        DraftBillDto draft = getOrCreateDraft(chatId);
        Customer customer = khataService.findOrCreateCustomer(customerName);
        draft.setCustomerId(customer.getId());
        draft.setCustomerName(customer.getName());
        saveDraft(draft);
        return draft;
    }

    @Transactional
    public void cancelDraft(Long chatId) {
        draftRepository.deleteById(chatId);
    }

    /**
     * Finalizes the current draft bill.
     * Enforces strict atomic oversell guard and records transactions.
     */
    @Transactional(rollbackFor = Exception.class)
    public Bill finalizeBill(Long chatId) {
        DraftBillDto draft = getOrCreateDraft(chatId);

        if (draft.getItems() == null || draft.getItems().isEmpty()) {
            throw new InvalidOperationException("Cannot finalize an empty bill. Add items first.");
        }

        draft.recalculateTotals();

        String billNumber = generateBillNumber();

        // 1. Atomic oversell guard: Decrement stock for all items
        for (DraftBillItemDto item : draft.getItems()) {
            inventoryService.decrementStockAtomic(
                    item.getProductId(),
                    item.getQuantity(),
                    billNumber,
                    "Sale on Bill #" + billNumber
            );
        }

        // 2. Resolve Customer if Khata or named
        Customer customer = null;
        if (draft.getCustomerId() != null) {
            customer = khataService.getCustomerEntity(draft.getCustomerId());
        }

        Bill.PaymentMode mode;
        try {
            mode = Bill.PaymentMode.valueOf(draft.getPaymentMode().toUpperCase());
        } catch (Exception e) {
            mode = Bill.PaymentMode.UPI;
        }

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .chatId(chatId)
                .customer(customer)
                .totalTaxable(draft.getTotalTaxable())
                .totalCgst(draft.getTotalCgst())
                .totalSgst(draft.getTotalSgst())
                .totalTax(draft.getTotalTax())
                .grandTotal(draft.getGrandTotal())
                .paymentMode(mode)
                .paymentReference(draft.getPaymentReference())
                .status(Bill.BillStatus.COMPLETED)
                .build();

        for (DraftBillItemDto itemDto : draft.getItems()) {
            Product product = inventoryService.findProductEntity(itemDto.getProductId().toString());
            BillItem item = BillItem.builder()
                    .product(product)
                    .productName(itemDto.getProductName())
                    .unit(itemDto.getUnit())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(itemDto.getUnitPrice())
                    .mrp(itemDto.getMrp())
                    .gstRate(itemDto.getGstRate())
                    .hsnCode(itemDto.getHsnCode())
                    .taxableValue(itemDto.getTaxableValue())
                    .cgstAmount(itemDto.getCgstAmount())
                    .sgstAmount(itemDto.getSgstAmount())
                    .totalAmount(itemDto.getTotalAmount())
                    .build();
            bill.addItem(item);
        }

        Bill savedBill = billRepository.save(bill);

        // 3. If Khata payment, add to customer credit ledger
        if (mode == Bill.PaymentMode.KHATA) {
            if (customer == null) {
                throw new InvalidOperationException("Customer name is required when billing on Khata (Credit).");
            }
            khataService.addCredit(customer.getName(), draft.getGrandTotal(), billNumber, "Bill #" + billNumber);
        }

        // 4. Remove active draft
        draftRepository.deleteById(chatId);

        log.info("Successfully finalized Bill #{} for total ₹{}", billNumber, draft.getGrandTotal());
        return savedBill;
    }

    @Transactional(readOnly = true)
    public Optional<Bill> getLatestBill(Long chatId) {
        return billRepository.findTopByChatIdOrderByCreatedAtDesc(chatId);
    }

    @Transactional(readOnly = true)
    public Optional<Bill> getBillByNumber(String billNumber) {
        return billRepository.findByBillNumber(billNumber);
    }

    private String generateBillNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = billRepository.count() + 1;
        return String.format("KP-%s-%04d", datePrefix, count);
    }
}
