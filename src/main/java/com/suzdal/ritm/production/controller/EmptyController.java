package com.suzdal.ritm.production.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Time;
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

import com.suzdal.ritm.production.database.MySqlDatabase;
import com.suzdal.ritm.production.database.SqlServerDatabase;

@RestController
public class EmptyController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss"
        );

    private final SqlServerDatabase sqlServerDatabase;
    private final MySqlDatabase mySqlDatabase;

    public EmptyController(
        SqlServerDatabase sqlServerDatabase,
        MySqlDatabase mySqlDatabase
    ) {
        this.sqlServerDatabase = sqlServerDatabase;
        this.mySqlDatabase = mySqlDatabase;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getEmpty() {

        LocalDateTime dateFrom =
            LocalDate.now()
                .minusDays(3)
                .atStartOfDay();

        String sqlServerQuery = """
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

        String mySqlQuery = """
            SELECT *
            FROM ritmoproducciones
            """;

        try {
            /*
             * CONSULTA SQL SERVER
             */

            Connection sqlServerConnection =
                sqlServerDatabase.getConnection();

            List<Map<String, Object>> packageRecords =
                new ArrayList<>();

            try (
                PreparedStatement statement =
                    sqlServerConnection.prepareStatement(
                        sqlServerQuery
                    )
            ) {
                statement.setTimestamp(
                    1,
                    Timestamp.valueOf(dateFrom)
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
                            resultSet.getString(
                                "ArticleName"
                            )
                        );

                        packageRecord.put(
                            "ArticleNumber",
                            resultSet.getString(
                                "ArticleNumber"
                            )
                        );

                        packageRecord.put(
                            "BatchNumber",
                            resultSet.getString(
                                "BatchNumber"
                            )
                        );

                        packageRecord.put(
                            "DeviceName",
                            resultSet.getString(
                                "DeviceName"
                            )
                        );

                        packageRecords.add(packageRecord);
                    }
                }
            }

            /*
             * CONSULTA MYSQL
             */

            Connection mySqlConnection =
                mySqlDatabase.getConnection();

            List<Map<String, Object>> ritmoProducciones =
                new ArrayList<>();

            try (
                PreparedStatement statement =
                    mySqlConnection.prepareStatement(
                        mySqlQuery
                    );

                ResultSet resultSet =
                    statement.executeQuery()
            ) {
                ResultSetMetaData metadata =
                    resultSet.getMetaData();

                int columnCount =
                    metadata.getColumnCount();

                while (resultSet.next()) {

                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    for (
                        int columnIndex = 1;
                        columnIndex <= columnCount;
                        columnIndex++
                    ) {
                        String columnName =
                            metadata.getColumnLabel(
                                columnIndex
                            );

                        Object columnValue =
                            resultSet.getObject(
                                columnIndex
                            );

                        row.put(
                            columnName,
                            formatDatabaseValue(
                                columnValue
                            )
                        );
                    }

                    ritmoProducciones.add(row);
                }
            }

            /*
             * RESPUESTA JSON
             */

            Map<String, Object> response =
                new LinkedHashMap<>();

            response.put(
                "sqlServerDateFrom",
                DATE_TIME_FORMATTER.format(dateFrom)
            );

            response.put(
                "sqlServerRecordsCount",
                packageRecords.size()
            );

            response.put(
                "mySqlRecordsCount",
                ritmoProducciones.size()
            );

            response.put(
                "ritmoproducciones",
                ritmoProducciones
            );

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

    private Object formatDatabaseValue(
        Object value
    ) {
        if (value instanceof Timestamp timestamp) {
            return DATE_TIME_FORMATTER.format(
                timestamp.toLocalDateTime()
            );
        }

        if (value instanceof LocalDateTime localDateTime) {
            return DATE_TIME_FORMATTER.format(
                localDateTime
            );
        }

        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }

        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }

        return value;
    }
}