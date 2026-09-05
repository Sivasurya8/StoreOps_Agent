package com.kiranapilot.khata;

import com.kiranapilot.dto.KhataSummaryDto;
import com.kiranapilot.entity.Customer;
import com.kiranapilot.exception.CustomerNotFoundException;
import com.kiranapilot.repository.CustomerRepository;
import com.kiranapilot.repository.KhataTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KhataServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private KhataTransactionRepository khataTransactionRepository;

    @InjectMocks
    private KhataService khataService;

    private Customer ramesh;

    @BeforeEach
    void setUp() {
        ramesh = Customer.builder()
                .id(1L)
                .name("Ramesh Kumar")
                .normalizedName("ramesh kumar ramesh")
                .currentCreditBalance(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("Khata Credit Cycle: Add credit -> Check balance -> Record settlement payment -> Check remaining balance")
    void testKhataCreditAndSettlementCycle() {
        when(customerRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(ramesh));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
        when(khataTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());

        // 1. Put 500 on Ramesh's credit
        KhataSummaryDto afterCredit = khataService.addCredit("Ramesh", new BigDecimal("500.00"), null, "Rice & Oil on credit");
        assertEquals(new BigDecimal("500.00"), afterCredit.getCurrentBalance());
        verify(khataTransactionRepository, times(1)).save(any());

        // 2. Ramesh paid 300
        KhataSummaryDto afterPayment = khataService.recordPayment("Ramesh", new BigDecimal("300.00"), "CASH", "Cash settlement");
        assertEquals(new BigDecimal("200.00"), afterPayment.getCurrentBalance());
        verify(khataTransactionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Non-existent customer: Settle payment on unknown customer throws CustomerNotFoundException")
    void testUnknownCustomerPaymentThrowsException() {
        when(customerRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
        when(customerRepository.searchCustomers("Unknown")).thenReturn(Collections.emptyList());

        assertThrows(CustomerNotFoundException.class, () ->
                khataService.recordPayment("Unknown", new BigDecimal("100"), "CASH", null));
    }
}
