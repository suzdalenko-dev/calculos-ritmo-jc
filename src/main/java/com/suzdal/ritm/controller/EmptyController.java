package com.suzdal.ritm.controller;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmptyController {

    @GetMapping("/")
    public Map<String, String> getEmpty() {
        return Map.of(
            "time", "Empty endpoint works"
        );
    }
}