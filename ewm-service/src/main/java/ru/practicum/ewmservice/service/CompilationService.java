package ru.practicum.ewmservice.service;

import ru.practicum.ewmservice.dto.CompilationDto;
import ru.practicum.ewmservice.dto.NewCompilationDto;
import ru.practicum.ewmservice.dto.UpdateCompilationRequest;

import java.util.List;

public interface CompilationService {
    CompilationDto createCompilation(NewCompilationDto newCompilationDto);

    void deleteCompilation(Long compId);

    CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateCompilationRequest);

    List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size);

    CompilationDto getCompilationById(Long compId);
}