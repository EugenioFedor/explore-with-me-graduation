package ru.practicum.ewm.stats.analyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Opt-in integration test: EWM_TEST_JDBC_URL must point to a dedicated disposable database.
@SpringJUnitConfig(StatsIngestionIT.Config.class)
class StatsIngestionIT {
    @Autowired
    private StatsListeners listeners;
    @Autowired
    private StatsRepository repository;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void initializeDatabase() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        new JdbcTemplate(dataSource).execute("TRUNCATE user_interactions, event_similarities");
    }

    private UserActionAvro action(long user, ActionTypeAvro type, long time) {
        return UserActionAvro.newBuilder().setUserId(user).setEventId(10L).setActionType(type)
                .setTimestamp(Instant.ofEpochMilli(time)).build();
    }

    private EventSimilarityAvro pair(long first, long second, double score) {
        return EventSimilarityAvro.newBuilder().setEventA(first).setEventB(second).setScore(score)
                .setTimestamp(Instant.ofEpochMilli(123)).build();
    }

    @Test
    void actionBatchPreservesMaximumWeightsAndLatestTimesOnReplay() {
        var actions = IntStream.rangeClosed(1, 500).mapToObj(user -> action(user, ActionTypeAvro.VIEW, 1)).toList();
        listeners.action(actions);
        listeners.action(actions);
        listeners.action(List.of(action(1, ActionTypeAvro.LIKE, 2), action(1, ActionTypeAvro.VIEW, 3),
                action(1, ActionTypeAvro.REGISTER, 1)));
        assertThat(repository.interactions(List.of(10L)).getFirst().score()).isCloseTo(200.6,
                org.assertj.core.data.Offset.offset(1e-9));
        var history = repository.history(1).getFirst();
        assertThat(history.weight()).isEqualTo(1.0);
        assertThat(history.timestamp()).isEqualTo(Instant.ofEpochMilli(3));
    }

    @Test
    void actionBatchFailureRollsBackEarlierRowsAndCanBeRetried() {
        var valid = action(1, ActionTypeAvro.VIEW, 1);
        var invalid = action(2, ActionTypeAvro.VIEW, 2);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE user_interactions ADD CONSTRAINT review_failure CHECK (user_id <> 2)");
        try {
            assertThatThrownBy(() -> listeners.action(List.of(valid, invalid)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(repository.history(1)).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE user_interactions DROP CONSTRAINT review_failure");
        }
        listeners.action(List.of(valid, invalid));
        assertThat(repository.history(1)).hasSize(1);
        assertThat(repository.history(2)).hasSize(1);
    }

    @Test
    void similarityBatchRollsBackOnDatabaseFailureAndReplayIsIdempotent() {
        var valid = pair(10, 20, 0.4);
        var invalid = pair(20, 30, 2.0);
        assertThatThrownBy(() -> listeners.similarity(List.of(valid, invalid)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(repository.similarities(List.of(10L, 20L))).isEmpty();
        invalid.setScore(0.8);
        listeners.similarity(List.of(valid, invalid));
        listeners.similarity(List.of(valid, invalid, pair(20, 10, 0.6)));
        var result = repository.similarities(List.of(10L, 20L));
        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(value -> {
            assertThat(value.eventA()).isEqualTo(10L);
            assertThat(value.eventB()).isEqualTo(20L);
            assertThat(value.score()).isEqualTo(0.6);
        });
    }

    @Configuration
    @EnableTransactionManagement
    @Import({StatsRepository.class, StatsIngestionService.class, StatsListeners.class})
    static class Config {
        @Bean
        DataSource dataSource() {
            String url = System.getenv("EWM_TEST_JDBC_URL");
            if (url == null) {
                throw new IllegalStateException("Set EWM_TEST_JDBC_URL to a disposable PostgreSQL database");
            }
            return new DriverManagerDataSource(url, System.getenv("EWM_TEST_DB_USER"),
                    System.getenv("EWM_TEST_DB_PASSWORD"));
        }

        @Bean
        NamedParameterJdbcTemplate jdbc(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
