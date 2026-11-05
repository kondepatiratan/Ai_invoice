package com.ratan.invoice.repository;

import com.ratan.invoice.entity.Client;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findAllByUserId(Long userId);

    List<Client> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<Client> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndEmail(Long userId, String email);

    boolean existsByUserIdAndEmailAndIdNot(Long userId, String email, Long id);
}
