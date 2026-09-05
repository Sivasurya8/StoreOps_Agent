package com.kiranapilot.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiranapilot.dto.DraftBillDto;
import com.kiranapilot.entity.Bill;
import com.kiranapilot.entity.BillDraft;
import com.kiranapilot.entity.Product;
import com.kiranapilot.gst.GstCalculator;
import com.kiranapilot.inventory.InventoryService;
import com.kiranapilot.khata.KhataService;
import com.kiranapilot.repository.BillDraftRepository;
import com.kiranapilot.repository.BillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiTurnBillingTest {

    @Mock
    private BillRepository billRepository;

    @Mock
    private BillDraftRepository draftRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private KhataService khataService;

    private GstCalculator gstCalculator;
    private ObjectMapper objectMapper;
    private BillingService billingService;

    private Product maggi;
    private Product sugar;
    private Product butter;

    @BeforeEach
    void setUp() {
        gstCalculator = new GstCalculator();
        objectMapper = new ObjectMapper();
        billingService = new BillingService(billRepository, draftRepository, inventoryService, gstCalculator, khataService, objectMapper);

        maggi = Product.builder()
                .id(1L)
                .sku("SKU-MAGGI")
                .name("Maggi 70g")
                .unit("packet")
                .sellingPrice(new BigDecimal("14.00"))
                .mrp(new BigDecimal("14.00"))
                .quantity(new BigDecimal("100"))
                .gstRate(new BigDecimal("12.00"))
                .hsnCode("1902")
                .build();

        sugar = Product.builder()
                .id(2L)
                .sku("SKU-SUGAR")
                .name("Sugar Loose")
                .unit("kg")
                .sellingPrice(new BigDecimal("42.00"))
                .mrp(new BigDecimal("45.00"))
                .quantity(new BigDecimal("50"))
                .gstRate(BigDecimal.ZERO)
                .hsnCode("1701")
                .build();

        butter = Product.builder()
                .id(3L)
                .sku("SKU-BUTTER")
                .name("Amul Butter 100g")
                .unit("packet")
                .sellingPrice(new BigDecimal("58.00"))
                .mrp(new BigDecimal("60.00"))
                .quantity(new BigDecimal("30"))
                .gstRate(new BigDecimal("12.00"))
                .hsnCode("0405")
                .build();
    }

    @Test
    @DisplayName("Multi-turn billing: Create draft, add items, edit quantities, and remove items mid-build")
    void testMultiTurnDraftWorkflow() throws Exception {
        Long chatId = 12345L;
        when(draftRepository.findById(chatId)).thenReturn(Optional.empty());
        when(inventoryService.findProductEntity("maggi")).thenReturn(maggi);
        when(inventoryService.findProductEntity("sugar")).thenReturn(sugar);
        when(inventoryService.findProductEntity("butter")).thenReturn(butter);

        // 1. Add 4 Maggi
        DraftBillDto draft1 = billingService.addItem(chatId, "maggi", new BigDecimal("4"));
        assertEquals(1, draft1.getItems().size());
        assertEquals(new BigDecimal("56.00"), draft1.getGrandTotal());

        // 2. Add 2kg Sugar
        String json1 = objectMapper.writeValueAsString(draft1);
        when(draftRepository.findById(chatId)).thenReturn(Optional.of(BillDraft.builder().chatId(chatId).draftJson(json1).build()));
        DraftBillDto draft2 = billingService.addItem(chatId, "sugar", new BigDecimal("2"));
        assertEquals(2, draft2.getItems().size());
        assertEquals(new BigDecimal("140.00"), draft2.getGrandTotal());

        // 3. Add 1 Butter
        String json2 = objectMapper.writeValueAsString(draft2);
        when(draftRepository.findById(chatId)).thenReturn(Optional.of(BillDraft.builder().chatId(chatId).draftJson(json2).build()));
        DraftBillDto draft3 = billingService.addItem(chatId, "butter", new BigDecimal("1"));
        assertEquals(3, draft3.getItems().size());
        assertEquals(new BigDecimal("198.00"), draft3.getGrandTotal());

        // 4. Mid-bill edit: Drop butter
        String json3 = objectMapper.writeValueAsString(draft3);
        when(draftRepository.findById(chatId)).thenReturn(Optional.of(BillDraft.builder().chatId(chatId).draftJson(json3).build()));
        DraftBillDto draft4 = billingService.removeItem(chatId, "butter");
        assertEquals(2, draft4.getItems().size());
        assertEquals(new BigDecimal("140.00"), draft4.getGrandTotal());

        // 5. Mid-bill edit: Make it 6 Maggi
        String json4 = objectMapper.writeValueAsString(draft4);
        when(draftRepository.findById(chatId)).thenReturn(Optional.of(BillDraft.builder().chatId(chatId).draftJson(json4).build()));
        DraftBillDto draft5 = billingService.updateItemQuantity(chatId, "maggi", new BigDecimal("6"));
        assertEquals(2, draft5.getItems().size());
        // 6 * 14 = 84 (Maggi) + 84 (Sugar) = 168.00
        assertEquals(new BigDecimal("168.00"), draft5.getGrandTotal());
    }

    @Test
    @DisplayName("Bill Finalize: Atomically decrements stock only upon finalization and clears draft")
    void testFinalizeBill() throws Exception {
        Long chatId = 99999L;
        when(inventoryService.findProductEntity("maggi")).thenReturn(maggi);
        when(inventoryService.findProductEntity("1")).thenReturn(maggi);

        DraftBillDto draft = billingService.addItem(chatId, "maggi", new BigDecimal("4"));
        String json = objectMapper.writeValueAsString(draft);
        when(draftRepository.findById(chatId)).thenReturn(Optional.of(BillDraft.builder().chatId(chatId).draftJson(json).build()));
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> {
            Bill b = i.getArgument(0);
            b.setId(101L);
            return b;
        });

        Bill finalized = billingService.finalizeBill(chatId);

        assertNotNull(finalized);
        assertEquals(new BigDecimal("56.00"), finalized.getGrandTotal());
        assertEquals(Bill.PaymentMode.UPI, finalized.getPaymentMode());

        // Verify atomic decrement was called for Maggi
        verify(inventoryService, times(1)).decrementStockAtomic(eq(1L), eq(new BigDecimal("4")), anyString(), anyString());
        // Verify draft was deleted
        verify(draftRepository, times(1)).deleteById(chatId);
    }
}
