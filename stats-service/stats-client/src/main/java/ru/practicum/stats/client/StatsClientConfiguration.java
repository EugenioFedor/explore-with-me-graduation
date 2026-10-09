package ru.practicum.stats.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatsClientConfiguration {
    @Bean
    public CollectorClient collectorClient() {
        return new CollectorClient();
    }

    @Bean
    public AnalyzerClient analyzerClient() {
        return new AnalyzerClient();
    }
}
