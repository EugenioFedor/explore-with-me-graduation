package ru.practicum.ewm.stats.analyzer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;

@Configuration
public class KafkaErrorConfig {
    @Bean
    public CommonContainerStoppingErrorHandler kafkaErrorHandler() {
        // Stop without committing the failed batch; restart after restoring PostgreSQL.
        return new CommonContainerStoppingErrorHandler();
    }
}
