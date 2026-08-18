package com.suzdal.ritm.production.utils.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

import com.suzdal.ritm.production.utils.models.MySqlCredencials;

import tools.jackson.databind.json.JsonMapper;

@Service
public class MySqlCredencialsReader {

    private static final Path CREDENTIALS_PATH = Path.of("C:/projects/secret/mysql.json");
    private final JsonMapper jsonMapper;

    public MySqlCredencialsReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public MySqlCredencials read() throws IOException {

        String json = Files.readString(CREDENTIALS_PATH);
        return jsonMapper.readValue(json, MySqlCredencials.class);
    }
}