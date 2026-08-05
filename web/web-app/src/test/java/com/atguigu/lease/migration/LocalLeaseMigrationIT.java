package com.atguigu.lease.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "lease.local-migration.enabled", matches = "true")
class LocalLeaseMigrationIT {

    private static final String URL = System.getProperty(
            "lease.local-migration.url", "jdbc:mysql://localhost:3306/lease");
    private static final String USER = System.getProperty("lease.local-migration.user", "root");
    private static final String PASSWORD = System.getProperty("lease.local-migration.password", "");

    @Test
    void baselinesExistingSchemaAndPreservesOriginalRowsWhileApplyingIncrementalMigrations()
            throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(countWhere("room_info", "id < 900000")).isEqualTo(14);
        assertThat(countWhere("apartment_info", "id < 900000")).isEqualTo(3);
        assertThat(countWhere("graph_info", "id < 900000")).isEqualTo(50);
        assertThat(countWhere("user_info", "id < 900000")).isEqualTo(1);
        assertThat(countWhere("room_info", "id BETWEEN 930001 AND 930006")).isEqualTo(6);
        assertThat(countWhere("user_info", "id = 990001 AND phone = '13800000000'")).isEqualTo(1);
        assertThat(count("ai_appointment_idempotency")).isZero();
        assertThat(count("appointment_event_outbox")).isZero();
        assertThat(columnCount("view_appointment", "room_id")).isEqualTo(1);
    }

    private long count(String table) throws SQLException {
        return countWhere(table, "1 = 1");
    }

    private long countWhere(String table, String condition) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + condition)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long columnCount(String table, String column) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             java.sql.PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
