package com.ratan.invoice.repository;

import com.ratan.invoice.entity.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"client", "items"})
    List<Invoice> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = {"client", "items"})
    Optional<Invoice> findByIdAndUserId(Long id, Long userId);

    boolean existsByClientId(Long clientId);

    @Modifying
    @Query("update Invoice i set i.status = com.ratan.invoice.entity.InvoiceStatus.OVERDUE "
            + "where i.status = com.ratan.invoice.entity.InvoiceStatus.SENT and i.dueDate < :today")
    int markSentInvoicesOverdue(@Param("today") LocalDate today);
}
