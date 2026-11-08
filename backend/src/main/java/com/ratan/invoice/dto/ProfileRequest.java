package com.ratan.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Company settings the owner can change. Email stays fixed because it's the login identity. */
public record ProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 150, message = "Business name must be at most 150 characters")
        String businessName,

        @Size(max = 15, message = "GSTIN must be at most 15 characters")
        String gstin,

        @NotBlank(message = "State is required (it decides CGST+SGST or IGST)")
        @Size(max = 100, message = "State must be at most 100 characters")
        String state
) {
}
