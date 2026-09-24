package ru.practicum.stats.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;
import ru.practicum.stats.server.dto.StatsRequestDto;
import ru.practicum.stats.server.mapper.EndpointHitMapper;
import ru.practicum.stats.server.model.EndpointHit;
import ru.practicum.stats.server.repository.StatsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsRepository statsRepository;
    private final EndpointHitMapper endpointHitMapper;

    public void saveHit(EndpointHitDto endpointHitDto) {
        EndpointHit endpointHit = endpointHitMapper.toEntity(endpointHitDto);

        statsRepository.save(endpointHit);
    }

    public List<ViewStatsDto> getStats(StatsRequestDto request) {
        return request.getUnique()
                ? statsRepository.findUniqueStats(request.getStart(), request.getEnd(), request.getUris())
                : statsRepository.findStats(request.getStart(), request.getEnd(), request.getUris());
    }
}
