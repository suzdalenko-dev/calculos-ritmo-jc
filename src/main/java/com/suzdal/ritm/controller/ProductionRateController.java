package com.suzdal.ritm.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ProductionRateController {

    @GetMapping("/api/production-rate")
    public Map<String, String> getProductionRate() {
        return Map.of(
            "message", "Production rate endpoint works"
        );
    }
}
