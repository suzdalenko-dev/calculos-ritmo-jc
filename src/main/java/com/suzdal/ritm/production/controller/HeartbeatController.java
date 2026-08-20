package com.suzdal.ritm.production.controller;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@CrossOrigin(origins = "*")
public class HeartbeatController {
    
    // http://192.168.1.98:8181/api/heartbeat/java/
    @GetMapping("/api/heartbeat/java/")
    public ResponseEntity<Map<String, Object>> getHearBeat() {
        long uptimeMilliseconds = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds      = uptimeMilliseconds / 1000;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("res", new HeartbeatResponse("UP", "ritm", Instant.now(), uptimeSeconds));
        return ResponseEntity.ok(response);
    }

    public record HeartbeatResponse(String status, String application, Instant timestamp, long uptimeSeconds) {}
}
