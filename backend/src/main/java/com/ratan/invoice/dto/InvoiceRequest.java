package com.ratan.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
        @NotNull(message = "Client is required")
        Long clientId,

        @NotNull(message = "Issue date is required")
        LocalDate issueDate,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @Size(max = 1000, message = "Notes must be at most 1000 characters")
        String notes,

        @NotEmpty(message = "At least one invoice item is required")
        List<@NotNull @Valid InvoiceItemRequest> items
) {
    @AssertTrue(message = "Due date must not be before issue date")
    public boolean isDueDateValid() {
        return issueDate == null || dueDate == null || !dueDate.isBefore(issueDate);
    }
}
