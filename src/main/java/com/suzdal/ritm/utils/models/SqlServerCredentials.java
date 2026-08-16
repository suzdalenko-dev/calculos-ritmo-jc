package com.suzdal.ritm.utils.models;

public record SqlServerCredentials(
    String host,
    int port,
    String dbname,
    String username,
    String password
) {
}
