package com.suzdal.ritm.controller;
import static org.mockito.Answers.values;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suzdal.ritm.utils.service.SqlServerCredentialsReader;

@RestController
public class EmptyController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    private final SqlServerCredentialsReader credentialsReader;
    public EmptyController(SqlServerCredentialsReader credentialsReader) {
        this.credentialsReader = credentialsReader;
    }

    @GetMapping("/")
    public Map<String, String> getEmpty() {
        String currentDate = DATE_TIME_FORMATTER.format(java.time.LocalDateTime.now());

        try {
            var credentials = credentialsReader.read();

            return Map.of(
                "date", currentDate,
                "credentials", credentials != null
                    ? "loaded"
                    : "not loaded"
            );
        } catch (IOException e) {
            return Map.of(
                "date", currentDate,
                "credentials", e.getMessage().toString()
            );
        }
    }
}