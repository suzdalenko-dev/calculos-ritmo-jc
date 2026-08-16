package com.suzdal.ritm.controller;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.suzdal.ritm.utils.service.SqlServerCredentialsReader;


@RestController
public class EmptyController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SqlServerCredentialsReader credentialsReader;

    public EmptyController(SqlServerCredentialsReader credentialsReader) {
        this.credentialsReader = credentialsReader;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> getEmpty() {
        String currentDate = DATE_TIME_FORMATTER.format(LocalDateTime.now());

        try {
            var credentials = credentialsReader.read();

            return ResponseEntity.ok(
                Map.of(
                    "date", currentDate,
                    "credentials", "loaded",
                    "host", String.valueOf(credentials.host()),
                    "port", String.valueOf(credentials.port()),
                    "dbname", String.valueOf(credentials.dbname())
                )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "errorType", e.getClass().getSimpleName(),
                    "errorMessage", String.valueOf(e.getMessage())
                )
            );

        }
    }
}