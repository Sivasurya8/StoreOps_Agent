package com.kiranapilot.khata;

import com.kiranapilot.dto.KhataSummaryDto;
import com.kiranapilot.entity.Customer;
import com.kiranapilot.entity.KhataTransaction;
import com.kiranapilot.exception.CustomerNotFoundException;
import com.kiranapilot.exception.InvalidOperationException;
import com.kiranapilot.repository.CustomerRepository;
import com.kiranapilot.repository.KhataTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KhataService {

    private final CustomerRepository customerRepository;
    private final KhataTransactionRepository khataTransactionRepository;

    @Transactional
    public Customer findOrCreateCustomer(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidOperationException("Customer name cannot be empty");
        }
        String clean = name.trim();
        Optional<Customer> opt = customerRepository.findByNameIgnoreCase(clean);
        if (opt.isPresent()) return opt.get();

        List<Customer> search = customerRepository.searchCustomers(clean);
        if (!search.isEmpty()) return search.get(0);

        // Create new customer
        Customer newCustomer = Customer.builder()
                .name(clean)
                .normalizedName(clean.toLowerCase().replaceAll("[^a-z0-9]", " "))
                .currentCreditBalance(BigDecimal.ZERO)
                .build();
        return customerRepository.save(newCustomer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntity(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new CustomerNotFoundException("Empty customer name");
        }
        String clean = name.trim();
        Optional<Customer> opt = customerRepository.findByNameIgnoreCase(clean);
        if (opt.isPresent()) return opt.get();

        List<Customer> search = customerRepository.searchCustomers(clean);
        if (!search.isEmpty()) return search.get(0);

        throw new CustomerNotFoundException(name);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntity(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer ID " + customerId));
    }

    /**
     * Adds credit to customer khata ledger ("put ₹500 on Ramesh's credit").
     */
    @Transactional
    public KhataSummaryDto addCredit(String customerName, BigDecimal amount, String billId, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Credit amount must be positive");
        }
        Customer customer = findOrCreateCustomer(customerName);
        BigDecimal newBalance = customer.getCurrentCreditBalance().add(amount).setScale(2, RoundingMode.HALF_UP);
        customer.setCurrentCreditBalance(newBalance);
        customerRepository.save(customer);

        KhataTransaction tx = KhataTransaction.builder()
                .customer(customer)
                .transactionType(KhataTransaction.TransactionType.CREDIT_GIVEN)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(newBalance)
                .billId(billId)
                .notes(notes != null ? notes : "Goods bought on credit")
                .build();
        khataTransactionRepository.save(tx);

        log.info("Added credit of ₹{} to customer '{}'. New balance: ₹{}", amount, customer.getName(), newBalance);
        return getStatement(customer.getName());
    }

    /**
     * Records customer payment / credit settlement ("Ramesh paid ₹300").
     */
    @Transactional
    public KhataSummaryDto recordPayment(String customerName, BigDecimal amount, String paymentMode, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Payment amount must be positive");
        }
        Customer customer = getCustomerEntity(customerName);

        BigDecimal newBalance = customer.getCurrentCreditBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP);
        customer.setCurrentCreditBalance(newBalance);
        customerRepository.save(customer);

        KhataTransaction tx = KhataTransaction.builder()
                .customer(customer)
                .transactionType(KhataTransaction.TransactionType.PAYMENT_RECEIVED)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(newBalance)
                .paymentMode(paymentMode != null ? paymentMode.toUpperCase() : "CASH")
                .notes(notes != null ? notes : "Credit payment / settlement")
                .build();
        khataTransactionRepository.save(tx);

        log.info("Recorded payment of ₹{} for customer '{}'. Remaining balance: ₹{}", amount, customer.getName(), newBalance);
        return getStatement(customer.getName());
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String customerName) {
        Customer customer = getCustomerEntity(customerName);
        return customer.getCurrentCreditBalance();
    }

    @Transactional(readOnly = true)
    public KhataSummaryDto getStatement(String customerName) {
        Customer customer = getCustomerEntity(customerName);
        List<KhataTransaction> txs = khataTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());

        List<KhataSummaryDto.KhataEntryDto> entryDtos = txs.stream()
                .map(t -> KhataSummaryDto.KhataEntryDto.builder()
                        .id(t.getId())
                        .type(t.getTransactionType().name())
                        .amount(t.getAmount())
                        .balanceAfter(t.getBalanceAfter())
                        .paymentMode(t.getPaymentMode())
                        .notes(t.getNotes())
                        .date(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return KhataSummaryDto.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .phone(customer.getPhone())
                .currentBalance(customer.getCurrentCreditBalance())
                .recentTransactions(entryDtos)
                .build();
    }
}
