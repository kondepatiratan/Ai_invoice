package com.ratan.invoice.repository;

import com.ratan.invoice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
