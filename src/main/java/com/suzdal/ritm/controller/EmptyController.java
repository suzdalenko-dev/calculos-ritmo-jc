package com.suzdal.ritm.controller;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmptyController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/")
    public Map<String, String> getEmpty() {
        return Map.of(
            "time", DATE_TIME_FORMATTER.format(java.time.LocalDateTime.now())
        );
    }
}