package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHitDto;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsHelperService {
    private final StatsClient statsClient;
    private final String appName;

    public void hit(HttpServletRequest request) {
        try {
            statsClient.hit(new EndpointHitDto(appName, request.getRequestURI(), request.getRemoteAddr(), LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Cannot save stats hit for uri={}", request.getRequestURI(), e);
        }
    }
}
