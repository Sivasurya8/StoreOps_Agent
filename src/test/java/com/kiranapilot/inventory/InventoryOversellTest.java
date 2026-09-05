package com.kiranapilot.inventory;

import com.kiranapilot.dto.ProductDto;
import com.kiranapilot.entity.Product;
import com.kiranapilot.exception.InsufficientStockException;
import com.kiranapilot.repository.InventoryTransactionRepository;
import com.kiranapilot.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryOversellTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product maggi;

    @BeforeEach
    void setUp() {
        maggi = Product.builder()
                .id(10L)
                .sku("SKU-MAGGI-70G")
                .name("Maggi 70g")
                .normalizedName("maggi 70g")
                .unit("packet")
                .costPrice(new BigDecimal("12.00"))
                .sellingPrice(new BigDecimal("14.00"))
                .mrp(new BigDecimal("14.00"))
                .quantity(new BigDecimal("6.000")) // Only 6 in stock
                .reorderLevel(new BigDecimal("25.000"))
                .gstRate(new BigDecimal("12.00"))
                .hsnCode("1902")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Oversell Guard: Billing 10 when 6 in stock must fail with InsufficientStockException at tool layer")
    void testOversellGuardThrowsException() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(maggi));
        // Atomic decrement query returns 0 modified rows because quantity < requested
        when(productRepository.decrementStockAtomic(10L, new BigDecimal("10"))).thenReturn(0);

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> inventoryService.decrementStockAtomic(10L, new BigDecimal("10"), "BILL-TEST-001", "Sale test")
        );

        assertTrue(exception.getMessage().contains("Insufficient stock"));
        assertTrue(exception.getMessage().contains("Maggi 70g"));
        assertEquals(new BigDecimal("10"), exception.getRequested());
        assertEquals(new BigDecimal("6.000"), exception.getAvailable());

        // Verify transaction was never saved
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid Sale: Decrementing 4 when 6 in stock succeeds atomically")
    void testValidStockDecrement() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(maggi));
        when(productRepository.decrementStockAtomic(10L, new BigDecimal("4"))).thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.decrementStockAtomic(10L, new BigDecimal("4"), "BILL-TEST-002", "Sale"));

        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Receive Stock: Receiving 50 packets of Maggi updates inventory quantity and prices")
    void testReceiveStock() {
        when(productRepository.findBySku("SKU-MAGGI-70G")).thenReturn(Optional.of(maggi));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDto updated = inventoryService.receiveStock(
                "SKU-MAGGI-70G",
                new BigDecimal("50"),
                new BigDecimal("12.00"),
                new BigDecimal("14.00"),
                "Distributor invoice #5421"
        );

        assertEquals(new BigDecimal("56.000"), updated.getQuantity());
        assertEquals(new BigDecimal("12.00"), updated.getCostPrice());
        assertEquals(new BigDecimal("14.00"), updated.getMrp());
        verify(transactionRepository, times(1)).save(any());
    }
}
