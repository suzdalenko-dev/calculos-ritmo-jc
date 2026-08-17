package com.suzdal.ritm.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.stereotype.Service;
import com.suzdal.ritm.utils.models.MySqlCredencials;
import com.suzdal.ritm.utils.service.MySqlCredencialsReader;
import jakarta.annotation.PreDestroy;

@Service
public class MySqlDatabase {

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public MySqlDatabase(MySqlCredencialsReader credentialsReader) throws IOException {

        MySqlCredencials credentials = credentialsReader.read();

        this.url = (
            "jdbc:mysql://%s:%d/%s" +
            "?useSSL=false" +
            "&allowPublicKeyRetrieval=true" +
            "&serverTimezone=Europe/Madrid"
        ).formatted(
            credentials.host(),
            credentials.port(),
            credentials.dbname()
        );

        this.username = credentials.username();
        this.password = credentials.password();
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, username, password);
        }

        return connection;
    }

    @PreDestroy
    public void closeConnection()
        throws SQLException {

        if (
            connection != null &&
            !connection.isClosed()
        ) {
            connection.close();
        }
    }
}