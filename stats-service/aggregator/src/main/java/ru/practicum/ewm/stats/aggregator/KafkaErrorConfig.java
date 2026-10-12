package ru.practicum.ewm.stats.aggregator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;

@Slf4j
@Configuration
public class KafkaErrorConfig {
    @Bean
    public CommonContainerStoppingErrorHandler kafkaErrorHandler() {
        return new CommonContainerStoppingErrorHandler();
    }

    @Bean
    public KafkaListenerErrorHandler deliveryErrorHandler(KafkaListenerEndpointRegistry registry) {
        return (message, exception) -> {
            log.error("Similarity delivery failed; stopping consumer without acknowledging the action", exception);
            var container = registry.getListenerContainer("action-consumer");
            if (container != null) {
                container.stop(() -> log.warn("Aggregator consumer stopped; restart after restoring Kafka"));
            }
            // Async listener errors must be handled here: throwing would acknowledge the failed action.
            return null;
        };
    }
}
