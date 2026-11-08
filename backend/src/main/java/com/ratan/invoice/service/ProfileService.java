package com.ratan.invoice.service;

import com.ratan.invoice.dto.ProfileRequest;
import com.ratan.invoice.dto.ProfileResponse;
import com.ratan.invoice.entity.User;
import com.ratan.invoice.exception.ResourceNotFoundException;
import com.ratan.invoice.repository.UserRepository;
import com.ratan.invoice.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Company settings for the logged-in business. */
@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return toResponse(currentUser());
    }

    /**
     * Updates the company details. A new state only affects invoices created or edited
     * afterwards; sent and paid invoices keep the tax they were issued with.
     */
    @Transactional
    public ProfileResponse update(ProfileRequest request) {
        User user = currentUser();
        user.setName(request.name().trim());
        user.setBusinessName(blankToNull(request.businessName()));
        user.setGstin(blankToNull(request.gstin()) == null ? null : request.gstin().trim().toUpperCase());
        user.setState(request.state().trim());
        return toResponse(user);
    }

    private User currentUser() {
        Long userId = SecurityUtils.getCurrentUser().getId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getBusinessName(),
                user.getGstin(),
                user.getState());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
