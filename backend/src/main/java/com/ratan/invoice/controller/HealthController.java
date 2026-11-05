package com.ratan.invoice.controller;

import com.ratan.invoice.service.DataInitializer;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataInitializer dataInitializer;

    public HealthController(DataInitializer dataInitializer) {
        this.dataInitializer = dataInitializer;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "message", "AI Invoice Generator Backend is running");
    }

    @PostMapping("/seed")
    public Map<String, String> seed() {
        dataInitializer.seedDemoData();
        return Map.of("status", "SUCCESS", "message", "Demo data seeded successfully (demo@invoice.ai / password123)");
    }
}
