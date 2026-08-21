package com.example.ordersapp.dto;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class OrderRequest {

    @NotBlank(message = "customerName is required")
    @Size(max = 100, message = "customerName must be at most 100 characters")
    private String customerName;

    @NotBlank(message = "productName is required")
    @Size(max = 100, message = "productName must be at most 100 characters")
    private String productName;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be >= 1")
    private Integer quantity;

    @NotNull(message = "totalAmount is required")
    @DecimalMin(value = "0.00", message = "totalAmount must be >= 0")
    private BigDecimal totalAmount;

    @NotBlank(message = "status is required")
    @Size(max = 20, message = "status must be at most 20 characters")
    private String status;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
