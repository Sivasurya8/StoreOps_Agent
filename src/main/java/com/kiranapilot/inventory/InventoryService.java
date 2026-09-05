package com.kiranapilot.inventory;

import com.kiranapilot.dto.ProductDto;
import com.kiranapilot.dto.StockHealthDto;
import com.kiranapilot.entity.InventoryTransaction;
import com.kiranapilot.entity.Product;
import com.kiranapilot.exception.InsufficientStockException;
import com.kiranapilot.exception.ProductNotFoundException;
import com.kiranapilot.repository.InventoryTransactionRepository;
import com.kiranapilot.repository.ProductRepository;
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
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return productRepository.findAll().stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }
        return productRepository.searchActiveProducts(query.trim()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Product findProductEntity(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ProductNotFoundException("Empty identifier");
        }
        String clean = identifier.trim();

        // 1. Exact SKU match
        Optional<Product> bySku = productRepository.findBySku(clean);
        if (bySku.isPresent()) return bySku.get();

        // 2. Exact name match
        Optional<Product> byName = productRepository.findByNameIgnoreCase(clean);
        if (byName.isPresent()) return byName.get();

        // 3. Normalized search
        List<Product> search = productRepository.searchActiveProducts(clean);
        if (!search.isEmpty()) {
            return search.get(0);
        }

        throw new ProductNotFoundException(identifier);
    }

    @Transactional(readOnly = true)
    public ProductDto getProduct(String identifier) {
        return mapToDto(findProductEntity(identifier));
    }

    /**
     * Receives supplier stock.
     * Updates quantity atomically and adjusts cost / MRP if provided.
     */
    @Transactional
    public ProductDto receiveStock(String identifier, BigDecimal quantity, BigDecimal costPrice, BigDecimal mrp, String remarks) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        Product product = findProductEntity(identifier);

        if (costPrice != null && costPrice.compareTo(BigDecimal.ZERO) > 0) {
            product.setCostPrice(costPrice.setScale(2, RoundingMode.HALF_UP));
        }
        if (mrp != null && mrp.compareTo(BigDecimal.ZERO) > 0) {
            product.setMrp(mrp.setScale(2, RoundingMode.HALF_UP));
            // Default selling price to MRP if not set lower
            if (product.getSellingPrice().compareTo(mrp) > 0) {
                product.setSellingPrice(mrp.setScale(2, RoundingMode.HALF_UP));
            }
        }

        BigDecimal newQuantity = product.getQuantity().add(quantity);
        product.setQuantity(newQuantity);
        product = productRepository.save(product);

        InventoryTransaction tx = InventoryTransaction.builder()
                .product(product)
                .transactionType(InventoryTransaction.TransactionType.STOCK_IN)
                .quantityChange(quantity)
                .costPrice(product.getCostPrice())
                .sellingPrice(product.getSellingPrice())
                .balanceAfter(newQuantity)
                .remarks(remarks != null ? remarks : "Supplier Stock Received")
                .build();
        transactionRepository.save(tx);

        log.info("Received stock for product '{}': +{} units. New balance: {}", product.getName(), quantity, newQuantity);
        return mapToDto(product);
    }

    /**
     * Adds a new product to store catalog.
     */
    @Transactional
    public ProductDto addProduct(
            String name,
            String unit,
            Boolean isPackaged,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            BigDecimal initialQuantity,
            BigDecimal reorderLevel,
            BigDecimal gstRate,
            String hsnCode
    ) {
        String sku = "SKU-" + name.replaceAll("[^a-zA-Z0-9]", "-").toUpperCase() + "-" + System.currentTimeMillis() % 10000;
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", " ");

        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .normalizedName(normalized)
                .unit(unit != null ? unit.toLowerCase() : "piece")
                .isPackaged(isPackaged != null ? isPackaged : true)
                .costPrice(costPrice != null ? costPrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .sellingPrice(sellingPrice != null ? sellingPrice.setScale(2, RoundingMode.HALF_UP) : mrp)
                .mrp(mrp != null ? mrp.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .quantity(initialQuantity != null ? initialQuantity : BigDecimal.ZERO)
                .reorderLevel(reorderLevel != null ? reorderLevel : new BigDecimal("5.000"))
                .gstRate(gstRate != null ? gstRate.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .hsnCode(hsnCode != null ? hsnCode : "9999")
                .active(true)
                .build();

        Product saved = productRepository.save(product);

        if (initialQuantity != null && initialQuantity.compareTo(BigDecimal.ZERO) > 0) {
            InventoryTransaction tx = InventoryTransaction.builder()
                    .product(saved)
                    .transactionType(InventoryTransaction.TransactionType.STOCK_IN)
                    .quantityChange(initialQuantity)
                    .costPrice(saved.getCostPrice())
                    .sellingPrice(saved.getSellingPrice())
                    .balanceAfter(initialQuantity)
                    .remarks("Initial Stock Creation")
                    .build();
            transactionRepository.save(tx);
        }

        return mapToDto(saved);
    }

    /**
     * Atomic oversell protection:
     * Executes atomic update query. Throws InsufficientStockException if unavailable.
     */
    @Transactional
    public void decrementStockAtomic(Long productId, BigDecimal quantity, String referenceId, String remarks) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product ID " + productId));

        int rows = productRepository.decrementStockAtomic(productId, quantity);
        if (rows == 0) {
            throw new InsufficientStockException(product.getName(), quantity, product.getQuantity());
        }

        BigDecimal balanceAfter = product.getQuantity().subtract(quantity);
        InventoryTransaction tx = InventoryTransaction.builder()
                .product(product)
                .transactionType(InventoryTransaction.TransactionType.SALE)
                .quantityChange(quantity.negate())
                .costPrice(product.getCostPrice())
                .sellingPrice(product.getSellingPrice())
                .balanceAfter(balanceAfter)
                .referenceId(referenceId)
                .remarks(remarks != null ? remarks : "Sale finalization")
                .build();
        transactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getLowStockProducts() {
        return productRepository.findLowStockProducts().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StockHealthDto getStockHealth() {
        List<Product> all = productRepository.findAll();
        int lowStock = 0;
        int outOfStock = 0;
        BigDecimal totalValuation = BigDecimal.ZERO;

        for (Product p : all) {
            if (p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                outOfStock++;
                lowStock++;
            } else if (p.getQuantity().compareTo(p.getReorderLevel()) <= 0) {
                lowStock++;
            }
            BigDecimal value = p.getQuantity().multiply(p.getCostPrice());
            totalValuation = totalValuation.add(value);
        }

        List<ProductDto> lowStockList = productRepository.findLowStockProducts().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return StockHealthDto.builder()
                .totalSkus(all.size())
                .lowStockCount(lowStock)
                .outOfStockCount(outOfStock)
                .totalInventoryValuation(totalValuation.setScale(2, RoundingMode.HALF_UP))
                .lowStockProducts(lowStockList)
                .build();
    }

    public ProductDto mapToDto(Product p) {
        return ProductDto.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .unit(p.getUnit())
                .isPackaged(p.getIsPackaged())
                .costPrice(p.getCostPrice())
                .sellingPrice(p.getSellingPrice())
                .mrp(p.getMrp())
                .quantity(p.getQuantity())
                .reorderLevel(p.getReorderLevel())
                .gstRate(p.getGstRate())
                .hsnCode(p.getHsnCode())
                .active(p.getActive())
                .build();
    }
}
