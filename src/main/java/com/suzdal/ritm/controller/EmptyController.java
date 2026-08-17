package com.suzdal.ritm.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final SqlServerDatabase sqlServerDatabase;

    public EmptyController(
        SqlServerCredentialsReader credentialsReader,
        SqlServerDatabase sqlServerDatabase
    ) {
        this.credentialsReader = credentialsReader;
        this.sqlServerDatabase = sqlServerDatabase;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getEmpty() {

        String currentDate =
            DATE_TIME_FORMATTER.format(LocalDateTime.now());

        /*
         * Equivale al PHP:
         *
         * date('Y-m-d', strtotime('-7 days')) . ' 00:00:00'
         */
        LocalDateTime sevenDaysAgo =
            LocalDate.now()
                .minusDays(3)
                .atStartOfDay();

        String sql = """
            SELECT
                ActualNetWeightValue,
                CreationDate,
                ArticleName,
                ArticleNumber,
                BatchNumber,
                DeviceName
            FROM PackageRecord
            WHERE CreationDate >= ?
              AND DeviceName = 'CWE 01'
              AND ErrorFlag = 0
            ORDER BY CreationDate ASC
            """;

        try {

            Connection connection =
                sqlServerDatabase.getConnection();

            List<Map<String, Object>> packageRecords =
                new ArrayList<>();

            try (
                PreparedStatement statement =
                    connection.prepareStatement(sql)
            ) {
                statement.setTimestamp(
                    1,
                    Timestamp.valueOf(sevenDaysAgo)
                );

                try (
                    ResultSet resultSet =
                        statement.executeQuery()
                ) {
                    while (resultSet.next()) {

                        Map<String, Object> packageRecord =
                            new LinkedHashMap<>();

                        packageRecord.put(
                            "ActualNetWeightValue",
                            resultSet.getBigDecimal(
                                "ActualNetWeightValue"
                            )
                        );

                        Timestamp creationDate =
                            resultSet.getTimestamp(
                                "CreationDate"
                            );

                        packageRecord.put(
                            "CreationDate",
                            creationDate == null
                                ? null
                                : DATE_TIME_FORMATTER.format(
                                    creationDate.toLocalDateTime()
                                )
                        );

                        packageRecord.put(
                            "ArticleName",
                            resultSet.getString("ArticleName")
                        );

                        packageRecord.put(
                            "ArticleNumber",
                            resultSet.getString("ArticleNumber")
                        );

                        packageRecord.put(
                            "BatchNumber",
                            resultSet.getString("BatchNumber")
                        );

                        packageRecord.put(
                            "DeviceName",
                            resultSet.getString("DeviceName")
                        );

                        packageRecords.add(packageRecord);
                    }
                }
            }

            Map<String, Object> response =
                new LinkedHashMap<>();

            //response.put("date", currentDate);
            response.put("dateFrom", DATE_TIME_FORMATTER.format(sevenDaysAgo));
            //     response.put("host", credentials.host());
            //     response.put("dbname", credentials.dbname());
            //     response.put("username", credentials.username());
            //     response.put("connection", "Established");
                 response.put("recordsCount", packageRecords.size());
            // response.put("records", packageRecords);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            Map<String, Object> error =
                new LinkedHashMap<>();

            error.put(
                "errorType",
                e.getClass().getSimpleName()
            );

            error.put(
                "errorMessage",
                String.valueOf(e.getMessage())
            );

            return ResponseEntity
                .internalServerError()
                .body(error);
        }
    }
}