package ru.practicum.stats.server.mapper;

import org.mapstruct.Mapper;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.server.model.EndpointHit;

@Mapper(componentModel = "spring")
public interface EndpointHitMapper {

    EndpointHit toEntity(EndpointHitDto endpointHitDto);
}
