package com.suzdal.ritm.production.utils.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

import com.suzdal.ritm.production.database.MySqlDatabase;

@Repository
public class RateSettingsRepository  {

    public static List<Map<String, Object>> loadRateSettings(MySqlDatabase mySqlDatabase, String LOAD_RATE_SETTINGS_SQL) throws SQLException {
        
        List<Map<String, Object>> settings = new ArrayList<>();
        Connection connection              = mySqlDatabase.getConnection();

       try (
            PreparedStatement statement = connection.prepareStatement(LOAD_RATE_SETTINGS_SQL);
            ResultSet resultSet          = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                Map<String, Object> setting = new LinkedHashMap<>();
                setting.put("id", resultSet.getLong("id"));
                setting.put("__numero", resultSet.getObject("__numero"));
                setting.put("__min", resultSet.getObject("__min"));
                setting.put("__max", resultSet.getObject("__max"));
                setting.put("__sala", resultSet.getObject("__sala"));
                setting.put("__checked__frito", resultSet.getObject("__checked__frito"));
                settings.add(setting);
            }
        }

        return settings;
    }
}
