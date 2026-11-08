package com.ratan.invoice.dto;

public record ProfileResponse(
        Long id,
        String name,
        String email,
        String businessName,
        String gstin,
        String state
) {
}
