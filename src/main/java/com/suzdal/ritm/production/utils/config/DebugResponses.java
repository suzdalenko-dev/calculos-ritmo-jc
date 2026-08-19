package com.suzdal.ritm.production.utils.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;

public final class DebugResponses {

    /*
     * Impide crear objetos DebugResponses.
     */
    private DebugResponses() {
    }

    /*
     * MOSTRAR CUALQUIER VARIABLE COMO JSON
     */
    public static ResponseEntity<Map<String, Object>> json(Object value) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("res", value);
        return ResponseEntity.ok(response);
    }
}