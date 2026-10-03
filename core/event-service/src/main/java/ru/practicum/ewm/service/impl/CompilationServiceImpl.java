package ru.practicum.ewm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.CompilationDto;
import ru.practicum.ewm.dto.NewCompilationDto;
import ru.practicum.ewm.dto.UpdateCompilationRequest;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CompilationMapper;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.repository.CompilationRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.service.CompilationService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;
    private final EventMapper eventMapper;

    @Override
    public CompilationDto addCompilation(NewCompilationDto newCompilationDto) {
        log.info("Adding new compilation: {}", newCompilationDto.getTitle());
        Compilation compilation = compilationMapper.toEntity(newCompilationDto);
        compilation.setPinned(newCompilationDto.getPinned());

        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            List<Event> events = eventRepository.findAllById(newCompilationDto.getEvents());
            compilation.setEvents(events);
        } else {
            compilation.setEvents(new ArrayList<>());
        }

        compilation = compilationRepository.save(compilation);
        return toDtoWithEvents(compilation);
    }

    @Override
    public void deleteCompilation(Long compId) {
        log.info("Deleting compilation with id: {}", compId);
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " was not found");
        }
        compilationRepository.deleteById(compId);
    }

    @Override
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        log.info("Updating compilation with id: {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));

        if (updateRequest.getTitle() != null) {
            compilation.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }
        if (updateRequest.getEvents() != null) {
            List<Event> events = eventRepository.findAllById(updateRequest.getEvents());
            compilation.setEvents(events);
        }

        compilation = compilationRepository.save(compilation);
        return toDtoWithEvents(compilation);
    }

    private CompilationDto toDtoWithEvents(Compilation compilation) {
        CompilationDto dto = compilationMapper.toDto(compilation);
        if (compilation.getEvents() != null) {
            dto.setEvents(compilation.getEvents().stream()
                    .map(eventMapper::toShortDto)
                    .toList());
        }
        return dto;
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        PageRequest pageRequest = PageRequest.of(from / size, size);

        List<Compilation> compilations = pinned == null
                ? compilationRepository.findAll(pageRequest).getContent()
                : compilationRepository.findAllByPinned(pinned, pageRequest).getContent();

        return compilations.stream()
                .map(compilation -> {
                    CompilationDto dto = compilationMapper.toDto(compilation);
                    if (compilation.getEvents() != null) {
                        dto.setEvents(compilation.getEvents().stream()
                                .map(eventMapper::toShortDto)
                                .toList());
                    }
                    return dto;
                })
                .toList();
    }

    @Override
    public CompilationDto getCompilation(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));

        CompilationDto dto = compilationMapper.toDto(compilation);
        if (compilation.getEvents() != null) {
            dto.setEvents(compilation.getEvents().stream()
                    .map(eventMapper::toShortDto)
                    .toList());
        }
        return dto;
    }
}