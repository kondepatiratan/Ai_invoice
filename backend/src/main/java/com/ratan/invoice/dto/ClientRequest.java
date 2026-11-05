package com.ratan.invoice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone,

        @Size(max = 15, message = "GSTIN must be at most 15 characters")
        String gstin,

        @Size(max = 100, message = "State must be at most 100 characters")
        String state,

        @Size(max = 500, message = "Address must be at most 500 characters")
        String address
) {
}
