package ru.practicum.ewmservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.practicum.dto.EndpointHit;
import ru.practicum.dto.ViewStats;
import ru.practicum.statsclient.StatsClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsIntegrationService {
    private final StatsClient statsClient;

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Берем первый IP из списка, если их несколько
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        try {
            return statsClient.getStats(start, end, uris, unique);
        } catch (Exception e) {
            log.error("Failed to get stats from stats service: {}", e.getMessage());
            return Collections.emptyList(); // Возвращаем пустой список в случае ошибки
        }
    }

    @Async
    public void saveHit(HttpServletRequest request) {
        try {
            EndpointHit hit = new EndpointHit();
            hit.setApp("ewm-main-service");
            hit.setUri(request.getRequestURI());
            hit.setIp(getClientIp(request));
            hit.setTimestamp(LocalDateTime.now());
            statsClient.saveHit(hit);
        } catch (Exception e) {
            log.error("Failed to save hit to stats service: {}", e.getMessage());
        }
    }

    public Long getEventViews(Long eventId) {
        try {
            String uri = "/events/" + eventId;
            LocalDateTime start = LocalDateTime.now().minusYears(1);
            LocalDateTime end = LocalDateTime.now().plusHours(1);

            List<ViewStats> stats = statsClient.getStats(start, end, List.of(uri), true);

            return stats.isEmpty() ? 0L : stats.get(0).getHits();
        } catch (Exception e) {
            log.warn("Failed to get views for event {}: {}", eventId, e.getMessage());
            return 0L;
        }
    }
}