package ru.practicum.ewmservice.service;

import ru.practicum.statsclient.StatsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.dto.EndpointHit;
import ru.practicum.dto.ViewStats;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsIntegrationService {
    private final StatsClient statsClient;

    public void saveHit(HttpServletRequest request) {
        try {
            EndpointHit hit = new EndpointHit();
            hit.setApp("ewm-main-service");
            hit.setUri(request.getRequestURI());
            hit.setIp(getClientIp(request));
            hit.setTimestamp(LocalDateTime.now());
            statsClient.saveHit(hit);
            log.info("Saved hit for URI: {}, IP: {}", hit.getUri(), hit.getIp());
        } catch (Exception e) {
            log.error("Failed to save hit to stats service: {}", e.getMessage());
        }
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        try {
            return statsClient.getStats(start, end, uris, unique);
        } catch (Exception e) {
            log.error("Failed to get stats from stats service: {}", e.getMessage());
            return List.of();
        }
    }

    public Long getEventViews(Long eventId) {
        try {
            String uri = "/events/" + eventId;
            List<String> uris = List.of(uri);

            LocalDateTime start = LocalDateTime.now().minusYears(1);
            LocalDateTime end = LocalDateTime.now();

            List<ViewStats> stats = getStats(start, end, uris, true);

            if (!stats.isEmpty()) {
                return stats.get(0).getHits();
            }
        } catch (Exception e) {
            log.warn("Failed to get views for event {}: {}", eventId, e.getMessage());
        }
        return 0L;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}