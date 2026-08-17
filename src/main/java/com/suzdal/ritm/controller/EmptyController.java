package com.suzdal.ritm.controller;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suzdal.ritm.database.SqlServerDatabase;
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
        List<String> arrayList = new ArrayList<>(List.of("1", "2", "3", "4", "5"));
        arrayList.add("6");

        try {
            var credentials = credentialsReader.read();

            SqlServerDatabase sqlServerDatabase = new SqlServerDatabase(credentialsReader);
            sqlServerDatabase.getConnection(); // Establish the connection to the database


            return ResponseEntity.ok(
                Map.<String, String>of(
                    "date", currentDate,
                    "host", String.valueOf(credentials.host()),
                    "dbname", String.valueOf(credentials.dbname()),
                    "username", String.valueOf(credentials.username()),
                    "connection", "Established"
                )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.<String, String>of(
                    "errorType", e.getClass().getSimpleName(),
                    "errorMessage", String.valueOf(e.getMessage())
                )
            );

        }
    }
}