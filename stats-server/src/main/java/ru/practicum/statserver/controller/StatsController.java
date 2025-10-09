package ru.practicum.statserver.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.EndpointHit;
import ru.practicum.statserver.model.ViewStats;
import ru.practicum.statserver.service.StatsService;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public ru.practicum.statserver.model.EndpointHit saveHit(@Valid @RequestBody EndpointHit endpointHit) {
        return statsService.saveHit(endpointHit);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<ViewStats>> getStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) List<String> uris,
            @RequestParam(defaultValue = "false") Boolean unique) {

        if (start != null && end != null && start.isAfter(end)) {
            return ResponseEntity.badRequest().build();
        }

        List<ViewStats> stats = statsService.getStats(start, end, uris, unique);
        return ResponseEntity.ok(stats);
    }
}