package com.ratan.invoice.dto;

import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String name,
        String email,
        String phone,
        String gstin,
        String state,
        String address,
        LocalDateTime createdAt
) {
}
