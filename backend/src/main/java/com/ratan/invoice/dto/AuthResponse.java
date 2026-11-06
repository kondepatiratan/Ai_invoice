package com.ratan.invoice.dto;

public record AuthResponse(
        String token,
        Long id,
        String name,
        String email,
        String businessName,
        String state,
        String gstin
) {
}
