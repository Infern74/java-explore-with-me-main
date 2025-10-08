package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewmservice.dto.CompilationDto;
import ru.practicum.ewmservice.dto.NewCompilationDto;
import ru.practicum.ewmservice.dto.UpdateCompilationRequest;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.exception.ValidationException;
import ru.practicum.ewmservice.mapper.CompilationMapper;
import ru.practicum.ewmservice.model.Compilation;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.repository.CompilationRepository;
import ru.practicum.ewmservice.repository.EventRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newCompilationDto) {
        validateCompilationTitle(newCompilationDto.getTitle());

        Compilation compilation = new Compilation();
        compilation.setTitle(newCompilationDto.getTitle().trim());
        compilation.setPinned(newCompilationDto.getPinned() != null ? newCompilationDto.getPinned() : false);

        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            List<Event> events = eventRepository.findAllById(newCompilationDto.getEvents());
            compilation.setEvents(events);
        } else {
            compilation.setEvents(new ArrayList<>());
        }

        Compilation savedCompilation = compilationRepository.save(compilation);
        return compilationMapper.toCompilationDto(savedCompilation);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " not found");
        }
        compilationRepository.deleteById(compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateCompilationRequest) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " not found"));

        if (updateCompilationRequest.getTitle() != null) {
            validateCompilationTitle(updateCompilationRequest.getTitle());
            compilation.setTitle(updateCompilationRequest.getTitle().trim());
        }

        if (updateCompilationRequest.getPinned() != null) {
            compilation.setPinned(updateCompilationRequest.getPinned());
        }

        if (updateCompilationRequest.getEvents() != null) {
            List<Event> events = eventRepository.findAllById(updateCompilationRequest.getEvents());
            compilation.setEvents(events);
        }

        Compilation updatedCompilation = compilationRepository.save(compilation);
        return compilationMapper.toCompilationDto(updatedCompilation);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size) {
        if (from < 0) {
            throw new ValidationException("From must be non-negative");
        }
        if (size <= 0) {
            throw new ValidationException("Size must be positive");
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Compilation> compilations = compilationRepository.findCompilations(pinned, pageable);

        return compilations.stream()
                .map(compilationMapper::toCompilationDto)
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " not found"));
        return compilationMapper.toCompilationDto(compilation);
    }

    private void validateCompilationTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Title cannot be empty");
        }
        String trimmedTitle = title.trim();
        if (trimmedTitle.length() < 1 || trimmedTitle.length() > 50) {
            throw new ValidationException("Title must be between 1 and 50 characters");
        }
    }
}