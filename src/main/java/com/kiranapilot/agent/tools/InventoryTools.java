package com.kiranapilot.agent.tools;

import com.kiranapilot.agent.ToolResult;
import com.kiranapilot.dto.ProductDto;
import com.kiranapilot.exception.KiranaPilotException;
import com.kiranapilot.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryTools {

    private final InventoryService inventoryService;

    /**
     * Tool: receive_stock
     * Record inward stock from a distributor/supplier.
     */
    public ToolResult receiveStock(Map<String, Object> args) {
        try {
            String product = (String) args.get("product");
            BigDecimal qty = new BigDecimal(String.valueOf(args.get("quantity")));
            BigDecimal costPrice = args.get("cost_price") != null ? new BigDecimal(String.valueOf(args.get("cost_price"))) : null;
            BigDecimal mrp = args.get("mrp") != null ? new BigDecimal(String.valueOf(args.get("mrp"))) : null;
            String remarks = (String) args.get("remarks");

            ProductDto updated = inventoryService.receiveStock(product, qty, costPrice, mrp, remarks);
            return ToolResult.success(updated, String.format("Successfully received %s %s of %s. Current stock: %s %s.",
                    qty.stripTrailingZeros().toPlainString(), updated.getUnit(), updated.getName(),
                    updated.getQuantity().stripTrailingZeros().toPlainString(), updated.getUnit()));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error in receiveStock tool", e);
            return ToolResult.error("Failed to receive stock: " + e.getMessage());
        }
    }

    /**
     * Tool: add_product
     * Add a new product SKU into the store catalog.
     */
    public ToolResult addProduct(Map<String, Object> args) {
        try {
            String name = (String) args.get("name");
            String unit = args.get("unit") != null ? (String) args.get("unit") : "packet";
            Boolean isPackaged = args.get("is_packaged") != null ? Boolean.parseBoolean(String.valueOf(args.get("is_packaged"))) : true;
            BigDecimal costPrice = args.get("cost_price") != null ? new BigDecimal(String.valueOf(args.get("cost_price"))) : BigDecimal.ZERO;
            BigDecimal mrp = args.get("mrp") != null ? new BigDecimal(String.valueOf(args.get("mrp"))) : BigDecimal.ZERO;
            BigDecimal sellingPrice = args.get("selling_price") != null ? new BigDecimal(String.valueOf(args.get("selling_price"))) : mrp;
            BigDecimal initialQty = args.get("initial_quantity") != null ? new BigDecimal(String.valueOf(args.get("initial_quantity"))) : BigDecimal.ZERO;
            BigDecimal reorderLevel = args.get("reorder_level") != null ? new BigDecimal(String.valueOf(args.get("reorder_level"))) : new BigDecimal("5.000");
            BigDecimal gstRate = args.get("gst_rate") != null ? new BigDecimal(String.valueOf(args.get("gst_rate"))) : BigDecimal.ZERO;
            String hsnCode = args.get("hsn_code") != null ? (String) args.get("hsn_code") : "9999";

            ProductDto created = inventoryService.addProduct(name, unit, isPackaged, costPrice, sellingPrice, mrp, initialQty, reorderLevel, gstRate, hsnCode);
            return ToolResult.success(created, String.format("Added new product '%s' (SKU: %s) with MRP ₹%s, GST %s%%, Stock: %s %s.",
                    created.getName(), created.getSku(), created.getMrp(), created.getGstRate(), created.getQuantity(), created.getUnit()));
        } catch (Exception e) {
            log.error("Error in addProduct tool", e);
            return ToolResult.error("Failed to add product: " + e.getMessage());
        }
    }

    /**
     * Tool: check_stock
     * Check available stock, pricing, and details for a specific item.
     */
    public ToolResult checkStock(Map<String, Object> args) {
        try {
            String product = (String) args.get("product");
            ProductDto p = inventoryService.getProduct(product);
            return ToolResult.success(p, String.format("Product: %s | In Stock: %s %s | Selling Price: ₹%s | MRP: ₹%s | GST: %s%%",
                    p.getName(), p.getQuantity().stripTrailingZeros().toPlainString(), p.getUnit(),
                    p.getSellingPrice().toPlainString(), p.getMrp().toPlainString(), p.getGstRate().toPlainString()));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Error checking stock: " + e.getMessage());
        }
    }

    /**
     * Tool: search_products
     * Search product catalog by name or keyword.
     */
    public ToolResult searchProducts(Map<String, Object> args) {
        try {
            String query = (String) args.get("query");
            List<ProductDto> list = inventoryService.searchProducts(query);
            return ToolResult.success(list, String.format("Found %d matching products.", list.size()));
        } catch (Exception e) {
            return ToolResult.error("Failed to search products: " + e.getMessage());
        }
    }

    /**
     * Tool: get_low_stock
     * Get list of items running low or below reorder threshold.
     */
    public ToolResult getLowStock(Map<String, Object> args) {
        try {
            List<ProductDto> list = inventoryService.getLowStockProducts();
            return ToolResult.success(list, String.format("Found %d products at or below reorder level.", list.size()));
        } catch (Exception e) {
            return ToolResult.error("Failed to query low stock: " + e.getMessage());
        }
    }
}
