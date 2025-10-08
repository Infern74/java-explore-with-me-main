package ru.practicum.ewmservice.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewmservice.dto.CompilationDto;
import ru.practicum.ewmservice.model.Compilation;
import ru.practicum.ewmservice.repository.ParticipationRequestRepository;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompilationMapper {

    private final ParticipationRequestRepository requestRepository;

    public CompilationDto toCompilationDto(Compilation compilation) {
        CompilationDto dto = new CompilationDto();
        dto.setId(compilation.getId());
        dto.setTitle(compilation.getTitle());
        dto.setPinned(compilation.getPinned());

        if (compilation.getEvents() != null) {
            dto.setEvents(compilation.getEvents().stream()
                    .map(event -> {
                        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(event.getId());
                        return EventMapper.toEventShortDto(event, event.getViews(), confirmedRequests);
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}