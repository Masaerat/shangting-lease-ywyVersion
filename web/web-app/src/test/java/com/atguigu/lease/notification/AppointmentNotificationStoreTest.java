package com.atguigu.lease.notification;

import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Embedded SQL tests, not a claim of MySQL / RabbitMQ end-to-end verification. */
class AppointmentNotificationStoreTest {
    private JdbcTemplate jdbc;
    private AppointmentNotificationStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        store = new AppointmentNotificationStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
        jdbc.execute("CREATE TABLE view_appointment(id bigint primary key, user_id bigint, room_id bigint, appointment_status int, appointment_time timestamp, is_deleted int default 0)");
        jdbc.execute("CREATE TABLE appointment_event_outbox(id bigint primary key, aggregate_id bigint, event_type varchar(64), status varchar(16))");
        jdbc.execute("CREATE TABLE user_notification(id bigint auto_increment primary key, event_key varchar(160) unique, event_id bigint, appointment_id bigint, user_id bigint, notification_type varchar(16), content varchar(1000), created_at timestamp default current_timestamp, read_at timestamp)");
        jdbc.update("INSERT INTO view_appointment(id,user_id,room_id,appointment_status) VALUES(20,7,100,1),(21,8,101,1)");
        jdbc.update("INSERT INTO appointment_event_outbox VALUES(10,20,'APPOINTMENT_CREATED','PENDING')");
    }

    private AppointmentMessage created() {
        return AppointmentMessage.builder().eventId(10L).appointmentId(20L).userId(7L).build();
    }

    @Test
    void redeliveryAndLegacyCreateConvergeToOneNotification() {
        store.persistCreated(created());
        store.persistCreated(created());
        store.persistLegacy(AppointmentNotificationMessage.builder().appointmentId(20L).userId(7L)
                .notificationType("CREATE").messageContent("legacy").build());
        assertThat(store.list(7L, 20)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notification", Integer.class)).isEqualTo(1);
    }

    @Test
    void enforcesOwnerOnInsertAndEveryRead() {
        AppointmentMessage invalid = created();
        invalid.setUserId(8L);
        assertThatThrownBy(() -> store.persistCreated(invalid)).isInstanceOf(IllegalArgumentException.class);
        store.persistCreated(created());
        Long id = store.list(7L, 20).getFirst().id();
        assertThat(store.list(8L, 20)).isEmpty();
        assertThat(store.status(8L, 20L)).isNull();
        assertThat(store.status(7L, 999L)).isNull();
        assertThat(store.markRead(8L, id)).isFalse();
        assertThat(store.list(7L, 20).getFirst().readAt()).isNull();
    }

    @Test
    void readIsIdempotentAndRedeliveryDoesNotResetIt() {
        store.persistCreated(created());
        Long id = store.list(7L, 20).getFirst().id();
        assertThat(store.markRead(7L, id)).isTrue();
        var firstRead = store.list(7L, 20).getFirst().readAt();
        assertThat(store.markRead(7L, id)).isTrue();
        store.persistCreated(created());
        assertThat(store.list(7L, 20).getFirst().readAt()).isEqualTo(firstRead).isNotNull();
    }

    @Test
    void publishedIsNotDeliveredAndConsumerCanWinConfirmRace() {
        assertThat(store.status(7L, 20L).deliveryStatus()).isEqualTo("PENDING");
        jdbc.update("UPDATE appointment_event_outbox SET status='PUBLISHED'");
        assertThat(store.status(7L, 20L).deliveryStatus()).isEqualTo("PUBLISHED");
        jdbc.update("UPDATE appointment_event_outbox SET status='PENDING'");
        store.persistCreated(created());
        assertThat(store.status(7L, 20L).deliveryStatus()).isEqualTo("DELIVERED");
        jdbc.update("UPDATE appointment_event_outbox SET status='DEAD'");
        assertThat(store.status(7L, 20L).deliveryStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void failedPublisherAndLegacyWithoutOutboxAreDistinct() {
        jdbc.update("UPDATE appointment_event_outbox SET status='DEAD'");
        assertThat(store.status(7L, 20L).deliveryStatus()).isEqualTo("FAILED");
        assertThat(store.status(8L, 21L).deliveryStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void updatesUseStableMessageIdsButDoNotCollapseDifferentEvents() {
        var message = AppointmentNotificationMessage.builder().appointmentId(20L).userId(7L)
                .notificationType("UPDATE").messageContent("updated").messageId("one").build();
        store.persistLegacy(message);
        store.persistLegacy(message);
        message.setMessageId("two");
        store.persistLegacy(message);
        assertThat(store.list(7L, 20)).hasSize(2);
        assertThat(store.list(7L, -1)).hasSize(1);
    }

    @Test
    void rejectsAnonymousReads() {
        assertThatThrownBy(() -> store.list(null, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.status(null, 20L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.markRead(null, 1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
