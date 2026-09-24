package ru.practicum.ewm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.practicum.stats.client.StatsClient;

@Configuration
public class StatsClientConfig {

    @Bean
    public StatsClient statsClient(DiscoveryClient discoveryClient,
                                   RestClient.Builder restClientBuilder) {
        return new StatsClient(discoveryClient, restClientBuilder);
    }

    @Bean
    public String appName(@Value("${spring.application.name:ewm-main-service}") String appName) {
        return appName;
    }
}