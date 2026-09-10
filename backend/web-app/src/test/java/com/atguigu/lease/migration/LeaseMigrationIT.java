package com.atguigu.lease.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LeaseMigrationIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("lease")
            .withUsername("lease")
            .withPassword("lease");

    @Test
    void emptyDatabaseContainsDemoInventoryAndClosedLoopTables() throws SQLException {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();

        assertThat(count("room_info")).isEqualTo(6);
        assertThat(countWhere("user_info", "phone = '13800000000' AND is_deleted = 0")).isEqualTo(1);
        assertThat(count("ai_appointment_idempotency")).isZero();
        assertThat(count("appointment_event_outbox")).isZero();
        assertThat(columnCount("view_appointment", "room_id")).isEqualTo(1);
    }

    private long count(String table) throws SQLException {
        return countWhere(table, "1 = 1");
    }

    private long countWhere(String table, String condition) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + condition)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long columnCount(String table, String column) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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
