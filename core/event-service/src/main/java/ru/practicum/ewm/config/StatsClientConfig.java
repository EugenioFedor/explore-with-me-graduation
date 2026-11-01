package ru.practicum.ewm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.practicum.stats.client.StatsClientConfiguration;

@Configuration
@Import(StatsClientConfiguration.class)
public class StatsClientConfig {
}
