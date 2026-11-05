package com.ratan.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InvoiceItemRequest(
        @NotBlank(message = "Item description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
        BigDecimal quantity,

        @NotNull(message = "Rate is required")
        @DecimalMin(value = "0.00", message = "Rate must be at least 0")
        BigDecimal rate,

        @NotNull(message = "GST rate is required")
        @DecimalMin(value = "0.00", message = "GST rate must be between 0 and 40")
        @DecimalMax(value = "40.00", message = "GST rate must be between 0 and 40")
        BigDecimal gstRate
) {
}
