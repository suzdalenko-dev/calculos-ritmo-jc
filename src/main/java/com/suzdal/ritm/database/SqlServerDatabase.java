package com.suzdal.ritm.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.stereotype.Service;

import com.suzdal.ritm.utils.models.SqlServerCredentials;
import com.suzdal.ritm.utils.service.SqlServerCredentialsReader;

import jakarta.annotation.PreDestroy;

@Service
public class SqlServerDatabase {
    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public SqlServerDatabase(SqlServerCredentialsReader credentialsReader) throws IOException {

        SqlServerCredentials credentials = credentialsReader.read();

        this.url = (
            "jdbc:sqlserver://%s:%d;" +
            "databaseName=%s;" +
            "encrypt=true;" +
            "trustServerCertificate=true;"
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
    public void closeConnection() throws SQLException {

        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}