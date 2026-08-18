package com.suzdal.ritm.production.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suzdal.ritm.production.database.MySqlDatabase;
import com.suzdal.ritm.production.database.SqlServerDatabase;

import tools.jackson.databind.json.JsonMapper;

@RestController
@CrossOrigin(origins = "*")
public class ProductionRateController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INDIVIDUAL_WEIGHTS_URL = "http://192.168.14.1/api/get-pesadas-individuales";
    private static final int ANALYSIS_STEP_SECONDS = 150;
    private static final int ASSESSMENT_WINDOW_SECONDS = 300;
    private static final int MINIMUM_STOP_SECONDS = 300;
    private static final int LONG_STOP_SECONDS = 18_000;
    private static final int BREAK_SECONDS = 1_800;
    private static final double HOURLY_RATE_MULTIPLIER = 3600.0 / ASSESSMENT_WINDOW_SECONDS;
    private final SqlServerDatabase sqlServerDatabase;
    private final MySqlDatabase mySqlDatabase;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public ProductionRateController(
        SqlServerDatabase sqlServerDatabase,
        MySqlDatabase mySqlDatabase,
        JsonMapper jsonMapper
    ) {
        this.sqlServerDatabase = sqlServerDatabase;
        this.mySqlDatabase = mySqlDatabase;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    // http://192.168.1.98:8080/api/informe_jc/java/
    @GetMapping("/api/informe_jc/java/")
    public ResponseEntity<Map<String, Object>> getProductionRate() {

        LocalDateTime dateFrom =
            LocalDate.now()
                .minusDays(7)
                .atStartOfDay();

        try {
            List<Map<String, Object>> rateSettings =
                loadRateSettings();

            /*
             * LÍNEA 3
             */

            List<Map<String, Object>> line3Materials =
                loadPackageRecords(
                    dateFrom,
                    "CWE"
                );

            List<Map<String, Object>> individualWeights =
                loadIndividualWeights(dateFrom);

            line3Materials.addAll(individualWeights);

            sortByCreationDate(line3Materials);

            List<Map<String, Object>> line3Result =
                calculateProductionReport(
                    line3Materials,
                    3,
                    rateSettings
                );

            prefixProductNames(
                line3Result,
                "L3 "
            );

            /*
             * LÍNEA 1
             */

            List<Map<String, Object>> line1Materials =
                loadPackageRecords(
                    dateFrom,
                    "CWE 01"
                );

            List<Map<String, Object>> line1Result =
                calculateProductionReport(
                    line1Materials,
                    1,
                    rateSettings
                );

            prefixProductNames(
                line1Result,
                "L1 "
            );

            /*
             * UNIR LAS DOS LÍNEAS
             */

            List<Map<String, Object>> mergedResult =
                new ArrayList<>();

            mergedResult.addAll(line3Result);
            mergedResult.addAll(line1Result);

            mergedResult.sort(
                Comparator.comparing(
                    row -> parseDateTime(
                        row.get("fechaIni")
                    )
                )
            );

            Map<String, Object> response =
                new LinkedHashMap<>();

            response.put(
                "res",
                mergedResult
            );

            return ResponseEntity.ok(response);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return errorResponse(exception);

        } catch (Exception exception) {
            return errorResponse(exception);
        }
    }

    /*
     * CONSULTA SQL SERVER
     */

    private List<Map<String, Object>> loadPackageRecords(
        LocalDateTime dateFrom,
        String deviceName
    ) throws Exception {

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
              AND DeviceName = ?
              AND ErrorFlag = 0
            ORDER BY CreationDate ASC
            """;

        List<Map<String, Object>> records =
            new ArrayList<>();

        Connection connection =
            sqlServerDatabase.getConnection();

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setTimestamp(
                1,
                Timestamp.valueOf(dateFrom)
            );

            statement.setString(
                2,
                deviceName
            );

            try (
                ResultSet resultSet =
                    statement.executeQuery()
            ) {
                while (resultSet.next()) {

                    Map<String, Object> record =
                        new LinkedHashMap<>();

                    record.put(
                        "ActualNetWeightValue",
                        resultSet.getBigDecimal(
                            "ActualNetWeightValue"
                        )
                    );

                    Timestamp creationDate =
                        resultSet.getTimestamp(
                            "CreationDate"
                        );

                    record.put(
                        "CreationDate",
                        creationDate == null
                            ? null
                            : formatDateTime(
                                creationDate.toLocalDateTime()
                            )
                    );

                    record.put(
                        "ArticleName",
                        resultSet.getString(
                            "ArticleName"
                        )
                    );

                    record.put(
                        "ArticleNumber",
                        resultSet.getString(
                            "ArticleNumber"
                        )
                    );

                    record.put(
                        "BatchNumber",
                        resultSet.getString(
                            "BatchNumber"
                        )
                    );

                    record.put(
                        "DeviceName",
                        resultSet.getString(
                            "DeviceName"
                        )
                    );

                    records.add(record);
                }
            }
        }

        return records;
    }

    /*
     * CONSULTA MYSQL
     */

    private List<Map<String, Object>> loadRateSettings()
        throws Exception {

        String sql = """
            SELECT
                id,
                __numero,
                __min,
                __max,
                __sala,
                __checked__frito
            FROM ritmoproducciones
            ORDER BY id ASC
            """;

        List<Map<String, Object>> settings =
            new ArrayList<>();

        Connection connection =
            mySqlDatabase.getConnection();

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql);

            ResultSet resultSet =
                statement.executeQuery()
        ) {
            while (resultSet.next()) {

                Map<String, Object> setting =
                    new LinkedHashMap<>();

                setting.put(
                    "id",
                    resultSet.getLong("id")
                );

                setting.put(
                    "__numero",
                    resultSet.getObject("__numero")
                );

                setting.put(
                    "__min",
                    resultSet.getObject("__min")
                );

                setting.put(
                    "__max",
                    resultSet.getObject("__max")
                );

                setting.put(
                    "__sala",
                    resultSet.getObject("__sala")
                );

                setting.put(
                    "__checked__frito",
                    resultSet.getObject(
                        "__checked__frito"
                    )
                );

                settings.add(setting);
            }
        }

        return settings;
    }

    /*
     * API DE PESADAS INDIVIDUALES
     */

    private List<Map<String, Object>> loadIndividualWeights(
        LocalDateTime dateFrom
    ) throws IOException, InterruptedException {

        YearMonth currentMonth =
            YearMonth.now();

        List<Map<String, Object>> weights =
            new ArrayList<>(
                requestIndividualWeights(
                    currentMonth
                )
            );

        /*
         * Durante los primeros siete días del mes también
         * necesitamos consultar el mes anterior.
         */

        if (LocalDate.now().getDayOfMonth() <= 7) {
            weights.addAll(
                requestIndividualWeights(
                    currentMonth.minusMonths(1)
                )
            );
        }

        /*
         * La API devuelve meses completos.
         * Dejamos únicamente los últimos siete días.
         */

        weights.removeIf(weight -> {

            Object creationDate =
                weight.get("CreationDate");

            if (creationDate == null) {
                return true;
            }

            try {
                return parseDateTime(creationDate)
                    .isBefore(dateFrom);

            } catch (Exception exception) {
                return true;
            }
        });

        return weights;
    }

    private List<Map<String, Object>> requestIndividualWeights(
        YearMonth month
    ) throws IOException, InterruptedException {

        String url =
            INDIVIDUAL_WEIGHTS_URL
                + "?year=" + month.getYear()
                + "&month=" + month.getMonthValue();

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<String> response =
            httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

        if (
            response.statusCode() < 200 ||
            response.statusCode() >= 300
        ) {
            throw new IOException(
                "La API de pesadas individuales respondió HTTP "
                    + response.statusCode()
            );
        }

        Object json =
            jsonMapper.readValue(
                response.body(),
                Object.class
            );

        if (!(json instanceof List<?> jsonList)) {
            throw new IOException(
                "La API de pesadas individuales no devolvió una lista JSON"
            );
        }

        List<Map<String, Object>> weights =
            new ArrayList<>();

        for (Object item : jsonList) {

            if (!(item instanceof Map<?, ?> sourceMap)) {
                continue;
            }

            Map<String, Object> weight =
                new LinkedHashMap<>();

            for (
                Map.Entry<?, ?> entry :
                sourceMap.entrySet()
            ) {
                weight.put(
                    String.valueOf(entry.getKey()),
                    entry.getValue()
                );
            }

            weights.add(weight);
        }

        return weights;
    }

    /*
     * CONVERSIÓN DE informeProducciones() DE PHP
     */

    private List<Map<String, Object>> calculateProductionReport(
        List<Map<String, Object>> materials,
        int productionLine,
        List<Map<String, Object>> rateSettings
    ) {

        /*
         * Índice por número y sala.
         *
         * Ejemplo:
         *
         * 61|3
         * 61|1
         */

        Map<String, Map<String, Object>>
            ratesByNumberAndRoom =
                new HashMap<>();

        /*
         * Índice solamente por número.
         */

        Map<String, Map<String, Object>>
            ratesByNumber =
                new HashMap<>();

        for (Map<String, Object> setting : rateSettings) {

            String number =
                valueAsKey(
                    setting.get("__numero")
                );

            String room =
                valueAsKey(
                    setting.get("__sala")
                );

            ratesByNumberAndRoom.put(
                number + "|" + room,
                setting
            );

            ratesByNumber.putIfAbsent(
                number,
                setting
            );
        }

        int articleIndex = 0;

        String previousArticle = "";

        Set<String> articleTitles =
            new HashSet<>();

        Map<String, Map<String, Object>> workData =
            new LinkedHashMap<>();

        double totalKilograms = 0;

        LocalDateTime previousProductionTime =
            null;

        /*
         * AGRUPAR LAS PESADAS EN PRODUCCIONES
         */

        for (Map<String, Object> material : materials) {

            String currentArticleNumber =
                valueAsString(
                    material.get("ArticleNumber")
                );

            String currentArticleName =
                valueAsString(
                    material.get("ArticleName")
                );

            String batchNumber =
                valueAsString(
                    material.get("BatchNumber")
                );

            String currentArticle =
                currentArticleNumber
                    + currentArticleName;

            if (!previousArticle.equals(currentArticle)) {
                articleIndex++;
            }

            previousArticle = currentArticle;

            String title =
                batchNumber
                    + "___"
                    + articleIndex
                    + "_______"
                    + currentArticleNumber
                    + "_______"
                    + currentArticleName;

            LocalDateTime materialDate =
                parseDateTime(
                    material.get("CreationDate")
                );

            /*
             * Crear una nueva agrupación de producción.
             */

            if (!articleTitles.contains(title)) {

                Map<String, Object> roomSetting =
                    ratesByNumberAndRoom.get(
                        valueAsKey(currentArticleNumber)
                            + "|"
                            + productionLine
                    );

                Object fried =
                    roomSetting == null
                        ? null
                        : integerValue(
                            roomSetting.get(
                                "__checked__frito"
                            )
                        );

                previousProductionTime =
                    materialDate;

                articleTitles.add(title);

                Map<String, Object> group =
                    createProductionGroup(
                        fried,
                        productionLine,
                        currentArticleNumber,
                        currentArticleName,
                        title,
                        materialDate
                    );

                /*
                 * Ajustes mínimo y máximo.
                 */

                Map<String, Object> numberSetting =
                    ratesByNumber.get(
                        valueAsKey(
                            currentArticleNumber
                        )
                    );

                if (numberSetting != null) {

                    group.put(
                        "ajusteMin",
                        doubleValue(
                            numberSetting.get("__min")
                        )
                    );

                    group.put(
                        "ajusteMax",
                        doubleValue(
                            numberSetting.get("__max")
                        )
                    );
                }

                workData.put(
                    title,
                    group
                );

                totalKilograms = 0;
            }

            Map<String, Object> group =
                workData.get(title);

            double weight =
                doubleValue(
                    material.get(
                        "ActualNetWeightValue"
                    )
                );

            totalKilograms += weight;

            group.put(
                "total_kilos",
                totalKilograms
            );

            group.put(
                "fechaFin",
                formatDateTime(materialDate)
            );

            LocalDateTime startDate =
                parseDateTime(
                    group.get("fechaIni")
                );

            /*
             * Guardar cada pesada para las ventanas móviles.
             */

            @SuppressWarnings("unchecked")
            List<WeightPoint> weightPoints =
                (List<WeightPoint>)
                    group.get("tiempoYValor");

            weightPoints.add(
                new WeightPoint(
                    materialDate,
                    weight
                )
            );

            /*
             * Tiempo desde la pesada anterior.
             */

            long gapSeconds =
                Duration.between(
                    previousProductionTime,
                    materialDate
                ).getSeconds();

            /*
             * Paradas mayores de cinco minutos y de hasta cinco horas.
             */

            if (
                gapSeconds > MINIMUM_STOP_SECONDS &&
                gapSeconds <= LONG_STOP_SECONDS
            ) {
                @SuppressWarnings("unchecked")
                List<Long> shortStops =
                    (List<Long>) group.get(
                        "segundos_perdidos_5minutos"
                    );

                shortStops.add(gapSeconds);

                long totalShortStops =
                    shortStops.stream()
                        .mapToLong(Long::longValue)
                        .sum();

                group.put(
                    "segundos_pedidos_5minutos",
                    totalShortStops
                );

                group.put(
                    "total_horas_perdidos_5minutos",
                    totalShortStops / 3600.0
                );

                group.put(
                    "hh_ii_horas_perdidas_5minutos",
                    formatHoursAndMinutes(
                        totalShortStops
                    )
                );
            }

            /*
             * Paradas mayores de cinco horas.
             */

            if (gapSeconds > LONG_STOP_SECONDS) {

                @SuppressWarnings("unchecked")
                List<Long> longStops =
                    (List<Long>) group.get(
                        "segundos_perdidos_5horas"
                    );

                longStops.add(gapSeconds);

                long totalLongStops =
                    longStops.stream()
                        .mapToLong(Long::longValue)
                        .sum();

                group.put(
                    "segPerdidos5horasTotal",
                    totalLongStops
                );
            }

            long elapsedSeconds =
                Duration.between(
                    startDate,
                    materialDate
                ).getSeconds();

            if (elapsedSeconds != 0) {

                group.put(
                    "difInicioFinSegundos",
                    elapsedSeconds
                );

                double elapsedHours =
                    (
                        elapsedSeconds
                            - longValue(
                                group.get(
                                    "segPerdidos5horasTotal"
                                )
                            )
                    ) / 3600.0;

                group.put(
                    "difInicioFinHoras",
                    elapsedHours
                );

                group.put(
                    "ritmo_medio_por_hora_inicio_fin",
                    elapsedHours != 0
                        ? totalKilograms / elapsedHours
                        : 0
                );

                group.put(
                    "duracionProduccionInicioFin",
                    formatHoursAndMinutes(
                        elapsedSeconds
                    )
                );
            }

            /*
             * Guardar la hora para detectar bocadillos.
             */

            @SuppressWarnings("unchecked")
            List<LocalTime> allTimes =
                (List<LocalTime>)
                    group.get("linesTodas");

            allTimes.add(
                materialDate.toLocalTime()
            );

            previousProductionTime =
                materialDate;
        }

        List<Map<String, Object>> result =
            new ArrayList<>();

        /*
         * CÁLCULOS FINALES DE CADA PRODUCCIÓN.
         */

        for (
            Map<String, Object> group :
            workData.values()
        ) {
            calculateFinalGroupValues(group);

            result.add(group);
        }

        return result;
    }

    private Map<String, Object> createProductionGroup(
        Object fried,
        int productionLine,
        String articleNumber,
        String articleName,
        String title,
        LocalDateTime startDate
    ) {

        Map<String, Object> group =
            new LinkedHashMap<>();

        group.put("frito_si_no", fried);
        group.put("linea", productionLine);
        group.put("numero", articleNumber);
        group.put("nombre", articleName);
        group.put("title", title);

        group.put("total_kilos", 0);
        group.put("ritmo_max_var", 0);

        group.put(
            "segundosHornadaLaboral",
            0
        );

        group.put(
            "segundos_perdidos_5horas",
            new ArrayList<Long>()
        );

        group.put(
            "segPerdidos5horasTotal",
            0L
        );

        group.put("_", "");

        group.put(
            "segundos_perdidos_5minutos",
            new ArrayList<Long>()
        );

        group.put(
            "segundos_pedidos_5minutos",
            0L
        );

        group.put(
            "total_horas_perdidos_5minutos",
            0
        );

        group.put(
            "hh_ii_horas_perdidas_5minutos",
            0
        );

        group.put("__", "");

        group.put(
            "ritmo_medio_por_hora_inicio_fin",
            0
        );

        group.put("___", "");

        group.put(
            "fechaIni",
            formatDateTime(startDate)
        );

        group.put("fechaFin", "");
        group.put("______", "");

        group.put(
            "difInicioFinSegundos",
            0L
        );

        group.put(
            "difInicioFinHoras",
            0
        );

        group.put(
            "duracionProduccionInicioFin",
            0
        );

        group.put(
            "duracionProduccionRealSegundos",
            0L
        );

        group.put(
            "duracionProduccionRealHoras",
            0
        );

        group.put(
            "duracionProduccionRealLeenda",
            0
        );

        group.put(
            "ritmoProduccionReal",
            0
        );

        group.put("____", "");

        group.put("ajusteMin", 0);
        group.put("ajusteMax", 0);

        group.put(
            "ritmo_de_horas_productivas",
            0
        );

        group.put(
            "linesTodas",
            new ArrayList<LocalTime>()
        );

        group.put("bocadillos", 0);

        group.put(
            "tiempoYValor",
            new ArrayList<WeightPoint>()
        );

        group.put(
            "tiempoPerdidoJustificado",
            0L
        );

        return group;
    }

    /*
     * CÁLCULOS FINALES DE CADA GRUPO
     */

    private void calculateFinalGroupValues(
        Map<String, Object> group
    ) {

        /*
         * DETECTAR BOCADILLOS
         */

        @SuppressWarnings("unchecked")
        List<LocalTime> allTimes =
            (List<LocalTime>)
                group.get("linesTodas");

        int breaks = 0;

        for (
            int index = 0;
            index < allTimes.size() - 1;
            index++
        ) {
            LocalTime first =
                allTimes.get(index);

            LocalTime second =
                allTimes.get(index + 1);

            if (
                crossesTime(
                    first,
                    second,
                    LocalTime.of(2, 15)
                ) ||
                crossesTime(
                    first,
                    second,
                    LocalTime.of(10, 15)
                ) ||
                crossesTime(
                    first,
                    second,
                    LocalTime.of(18, 15)
                )
            ) {
                breaks++;
            }
        }

        group.put(
            "bocadillos",
            breaks
        );

        /*
         * TIEMPO PERDIDO JUSTIFICADO
         */

        long longStopSeconds =
            longValue(
                group.get(
                    "segPerdidos5horasTotal"
                )
            );

        long justifiedLostTime =
            (long) breaks * BREAK_SECONDS
                + longStopSeconds;

        group.put(
            "tiempoPerdidoJustificado",
            justifiedLostTime
        );

        /*
         * DURACIÓN LABORAL
         */

        long totalElapsedSeconds =
            longValue(
                group.get(
                    "difInicioFinSegundos"
                )
            );

        long workingSeconds =
            totalElapsedSeconds
                - longStopSeconds;

        group.put(
            "segTiempoLaboral",
            workingSeconds
        );

        group.put(
            "segTiempoLaboralHoras",
            workingSeconds / 3600.0
        );

        group.put(
            "segTiempoLaboralLeenda",
            formatHoursAndMinutes(
                workingSeconds
            )
        );

        /*
         * RITMO R
         */

        long shortStopSeconds =
            longValue(
                group.get(
                    "segundos_pedidos_5minutos"
                )
            );

        long realProductionSeconds =
            totalElapsedSeconds
                - shortStopSeconds
                - longStopSeconds
                - (
                    (long) breaks
                        * BREAK_SECONDS
                );

        double realProductionHours =
            realProductionSeconds / 3600.0;

        group.put(
            "duracionProduccionRealSegundos",
            realProductionSeconds
        );

        group.put(
            "duracionProduccionRealHoras",
            realProductionHours
        );

        group.put(
            "duracionProduccionRealLeenda",
            formatHoursAndMinutes(
                realProductionSeconds
            )
        );

        double totalKilograms =
            doubleValue(
                group.get("total_kilos")
            );

        double productiveRate =
            realProductionHours != 0
                ? totalKilograms
                    / realProductionHours
                : 0;

        /*
         * RITMO C/B
         */

        long productionSecondsWithBreak =
            totalElapsedSeconds
                - shortStopSeconds
                - longStopSeconds;

        double productionHoursWithBreak =
            productionSecondsWithBreak
                / 3600.0;

        double productionRateWithBreak =
            productionHoursWithBreak != 0
                ? totalKilograms
                    / productionHoursWithBreak
                : 0;

        group.put(
            "CBduracionProduccionRealSegundos",
            productionSecondsWithBreak
        );

        group.put(
            "CBduracionProduccionRealHoras",
            productionHoursWithBreak
        );

        group.put(
            "CBduracionProduccionRealLeenda",
            formatHoursAndMinutes(
                productionSecondsWithBreak
            )
        );

        group.put(
            "CBritmo_de_horas_productivas",
            productionRateWithBreak
        );

        /*
         * Si el ritmo R es cero o negativo,
         * utilizar el ritmo C/B.
         */

        if (productiveRate <= 0) {
            productiveRate =
                productionRateWithBreak;
        }

        group.put(
            "ritmo_de_horas_productivas",
            productiveRate
        );

        calculateTimeWindows(group);
        calculateRatePercentages(group);

        /*
         * Igual que en PHP, estos datos internos
         * no se devuelven.
         */

        group.put("tiempoYValor", null);
        group.put("linesTodas", null);
    }

    /*
     * VENTANAS MÓVILES DE CINCO MINUTOS
     */

    private void calculateTimeWindows(
        Map<String, Object> group
    ) {

        LocalDateTime startDate =
            parseDateTime(
                group.get("fechaIni")
            );

        LocalDateTime endDate =
            parseDateTime(
                group.get("fechaFin")
            );

        LocalDateTime firstAssessmentPoint =
            startDate.plusSeconds(
                ASSESSMENT_WINDOW_SECONDS
            );

        List<Map<String, Object>> timeWindows =
            new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<WeightPoint> weightPoints =
            (List<WeightPoint>)
                group.get("tiempoYValor");

        double maximumRate = 0;

        LocalDateTime lastGeneratedTimestamp =
            null;

        for (
            LocalDateTime currentTimestamp =
                firstAssessmentPoint;

            !currentTimestamp.isAfter(endDate);

            currentTimestamp =
                currentTimestamp.plusSeconds(
                    ANALYSIS_STEP_SECONDS
                )
        ) {

            LocalDateTime windowStart =
                currentTimestamp.minusSeconds(
                    ASSESSMENT_WINDOW_SECONDS
                );

            double windowKilograms =
                sumWindowKilograms(
                    weightPoints,
                    windowStart,
                    currentTimestamp
                );

            double hourlyRate =
                windowKilograms
                    * HOURLY_RATE_MULTIPLIER;

            maximumRate =
                Math.max(
                    maximumRate,
                    hourlyRate
                );

            timeWindows.add(
                createTimeWindow(
                    currentTimestamp,
                    windowStart,
                    windowKilograms,
                    hourlyRate,
                    ANALYSIS_STEP_SECONDS,
                    false
                )
            );

            lastGeneratedTimestamp =
                currentTimestamp;
        }

        /*
         * ÚLTIMO TRAMO PARCIAL
         */

        if (
            !endDate.isBefore(
                firstAssessmentPoint
            ) &&
            lastGeneratedTimestamp != null &&
            lastGeneratedTimestamp.isBefore(
                endDate
            )
        ) {

            LocalDateTime windowStart =
                endDate.minusSeconds(
                    ASSESSMENT_WINDOW_SECONDS
                );

            double windowKilograms =
                sumWindowKilograms(
                    weightPoints,
                    windowStart,
                    endDate
                );

            double hourlyRate =
                windowKilograms
                    * HOURLY_RATE_MULTIPLIER;

            maximumRate =
                Math.max(
                    maximumRate,
                    hourlyRate
                );

            long finalSegmentSeconds =
                Duration.between(
                    lastGeneratedTimestamp,
                    endDate
                ).getSeconds();

            if (finalSegmentSeconds > 0) {

                timeWindows.add(
                    createTimeWindow(
                        endDate,
                        windowStart,
                        windowKilograms,
                        hourlyRate,
                        finalSegmentSeconds,
                        true
                    )
                );
            }
        }

        group.put(
            "franjaHoraria",
            timeWindows
        );

        group.put(
            "ritmo_max_var",
            maximumRate
        );
    }

    private double sumWindowKilograms(
        List<WeightPoint> weightPoints,
        LocalDateTime windowStart,
        LocalDateTime windowEnd
    ) {

        double kilograms = 0;

        for (WeightPoint point : weightPoints) {

            if (
                !point.time().isBefore(
                    windowStart
                ) &&
                !point.time().isAfter(
                    windowEnd
                )
            ) {
                kilograms +=
                    point.value();
            }
        }

        return kilograms;
    }

    private Map<String, Object> createTimeWindow(
        LocalDateTime timestamp,
        LocalDateTime windowStart,
        double windowKilograms,
        double hourlyRate,
        long weightSeconds,
        boolean finalSegment
    ) {

        Map<String, Object> window =
            new LinkedHashMap<>();

        window.put(
            "tiempo",
            formatDateTime(timestamp)
        );

        window.put(
            "ventanaIni",
            formatDateTime(windowStart)
        );

        window.put(
            "ventanaFin",
            formatDateTime(timestamp)
        );

        window.put(
            "kgVentana",
            windowKilograms
        );

        window.put(
            "ritmo",
            hourlyRate
        );

        window.put(
            "segundosPeso",
            weightSeconds
        );

        window.put(
            "esUltimoTramo",
            finalSegment
        );

        return window;
    }

    /*
     * PORCENTAJES BAJO, MEDIO Y SUPERIOR
     */

    private void calculateRatePercentages(
        Map<String, Object> group
    ) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeWindows =
            (List<Map<String, Object>>)
                group.get("franjaHoraria");

        double minimumRate =
            doubleValue(
                group.get("ajusteMin")
            );

        double maximumRate =
            doubleValue(
                group.get("ajusteMax")
            );

        long lowSeconds = 0;
        long mediumSeconds = 0;
        long highSeconds = 0;

        long remainingJustifiedTime =
            longValue(
                group.get(
                    "tiempoPerdidoJustificado"
                )
            );

        for (
            Map<String, Object> window :
            timeWindows
        ) {

            double rate =
                doubleValue(
                    window.get("ritmo")
                );

            long weightSeconds =
                longValue(
                    window.get("segundosPeso")
                );

            if (rate < minimumRate) {

                if (remainingJustifiedTime > 0) {

                    remainingJustifiedTime -=
                        weightSeconds;

                    if (remainingJustifiedTime < 0) {
                        remainingJustifiedTime = 0;
                    }

                    continue;
                }

                lowSeconds +=
                    weightSeconds;

            } else if (
                rate >= minimumRate &&
                rate <= maximumRate &&
                rate != 0
            ) {

                mediumSeconds +=
                    weightSeconds;

            } else {

                highSeconds +=
                    weightSeconds;
            }
        }

        long totalAssessedSeconds =
            lowSeconds
                + mediumSeconds
                + highSeconds;

        if (totalAssessedSeconds != 0) {

            group.put(
                "porcentajeBajo",
                lowSeconds
                    * 100.0
                    / totalAssessedSeconds
            );

            group.put(
                "porcentajeMedio",
                mediumSeconds
                    * 100.0
                    / totalAssessedSeconds
            );

            group.put(
                "porcentajeSuperior",
                highSeconds
                    * 100.0
                    / totalAssessedSeconds
            );

        } else {

            group.put(
                "porcentajeBajo",
                0
            );

            group.put(
                "porcentajeMedio",
                0
            );

            group.put(
                "porcentajeSuperior",
                0
            );
        }

        /*
         * DATOS DE CONTROL
         */

        group.put(
            "segundosPasoAnalisis",
            ANALYSIS_STEP_SECONDS
        );

        group.put(
            "minutosPasoAnalisis",
            ANALYSIS_STEP_SECONDS / 60.0
        );

        group.put(
            "segundosVentanaValoracion",
            ASSESSMENT_WINDOW_SECONDS
        );

        group.put(
            "minutosVentanaValoracion",
            ASSESSMENT_WINDOW_SECONDS / 60.0
        );

        group.put(
            "multiplicadorRitmoHora",
            HOURLY_RATE_MULTIPLIER
        );

        group.put(
            "tiempoBajoSegundos",
            lowSeconds
        );

        group.put(
            "tiempoMedioSegundos",
            mediumSeconds
        );

        group.put(
            "tiempoSuperiorSegundos",
            highSeconds
        );

        group.put(
            "tiempoTotalValoradoSegundos",
            totalAssessedSeconds
        );

        group.put(
            "tiempoBajoLeenda",
            formatHoursAndMinutes(
                lowSeconds
            )
        );

        group.put(
            "tiempoMedioLeenda",
            formatHoursAndMinutes(
                mediumSeconds
            )
        );

        group.put(
            "tiempoSuperiorLeenda",
            formatHoursAndMinutes(
                highSeconds
            )
        );

        group.put(
            "tiempoJustificadoRestante",
            remainingJustifiedTime
        );
    }

    /*
     * MÉTODOS AUXILIARES
     */

    private void sortByCreationDate(
        List<Map<String, Object>> materials
    ) {

        materials.sort(
            Comparator.comparing(
                material -> parseDateTime(
                    material.get("CreationDate")
                )
            )
        );
    }

    private void prefixProductNames(
        List<Map<String, Object>> result,
        String prefix
    ) {

        for (Map<String, Object> row : result) {

            row.put(
                "nombre",
                prefix
                    + valueAsString(
                        row.get("nombre")
                    )
            );
        }
    }

    private boolean crossesTime(
        LocalTime first,
        LocalTime second,
        LocalTime limit
    ) {

        return !first.isAfter(limit)
            && second.isAfter(limit);
    }

    private LocalDateTime parseDateTime(
        Object value
    ) {

        if (
            value instanceof
            LocalDateTime localDateTime
        ) {
            return localDateTime;
        }

        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        String text =
            valueAsString(value)
                .trim()
                .replace('T', ' ');

        /*
         * Eliminar milisegundos o cualquier contenido
         * posterior a yyyy-MM-dd HH:mm:ss.
         */

        if (text.length() > 19) {
            text = text.substring(0, 19);
        }

        return LocalDateTime.parse(
            text,
            DATE_TIME_FORMATTER
        );
    }

    private static String formatDateTime(
        LocalDateTime value
    ) {

        return DATE_TIME_FORMATTER.format(
            value
        );
    }

    private String formatHoursAndMinutes(
        long totalSeconds
    ) {

        long hours =
            Math.floorDiv(
                totalSeconds,
                3600L
            );

        long minutes =
            Math.floorDiv(
                totalSeconds % 3600L,
                60L
            );

        return "%02d:%02d".formatted(
            hours,
            minutes
        );
    }

    private String valueAsString(
        Object value
    ) {

        return value == null
            ? ""
            : String.valueOf(value);
    }

    /*
     * Normaliza números para crear las claves de los índices.
     *
     * 61
     * 61.0
     * BigDecimal("61.00")
     *
     * Todos producen la clave "61".
     */

    private String valueAsKey(
        Object value
    ) {

        if (value instanceof BigDecimal decimal) {

            return decimal
                .stripTrailingZeros()
                .toPlainString();
        }

        if (value instanceof Number number) {

            BigDecimal decimal =
                new BigDecimal(
                    number.toString()
                );

            return decimal
                .stripTrailingZeros()
                .toPlainString();
        }

        String text =
            valueAsString(value).trim();

        try {

            return new BigDecimal(text)
                .stripTrailingZeros()
                .toPlainString();

        } catch (NumberFormatException exception) {

            return text;
        }
    }

    private double doubleValue(
        Object value
    ) {

        if (value == null) {
            return 0;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue ? 1 : 0;
        }

        return Double.parseDouble(
            value.toString()
        );
    }

    private long longValue(
        Object value
    ) {

        if (value == null) {
            return 0;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue ? 1 : 0;
        }

        return new BigDecimal(
            value.toString()
        ).longValue();
    }

    private int integerValue(
        Object value
    ) {

        return (int) longValue(value);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
        Exception exception
    ) {

        Map<String, Object> error =
            new LinkedHashMap<>();

        error.put(
            "errorType",
            exception
                .getClass()
                .getSimpleName()
        );

        error.put(
            "errorMessage",
            String.valueOf(
                exception.getMessage()
            )
        );

        return ResponseEntity
            .internalServerError()
            .body(error);
    }

    /*
     * Estructura interna para guardar cada pesada.
     * No es una entidad, ni un modelo de base de datos.
     */

    private record WeightPoint(
        LocalDateTime time,
        double value
    ) {
    }
}