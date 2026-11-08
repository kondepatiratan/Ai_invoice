package com.ratan.invoice.controller;

import com.ratan.invoice.dto.ProfileRequest;
import com.ratan.invoice.dto.ProfileResponse;
import com.ratan.invoice.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get() {
        return profileService.get();
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody ProfileRequest request) {
        return profileService.update(request);
    }
}
