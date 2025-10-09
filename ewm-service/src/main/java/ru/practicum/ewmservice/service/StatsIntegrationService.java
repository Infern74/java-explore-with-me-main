package ru.practicum.ewmservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        try {
            log.debug("Requesting stats from stats service: start={}, end={}, uris={}, unique={}",
                    start, end, uris, unique);

            List<ViewStats> stats = statsClient.getStats(start, end, uris, unique);
            log.debug("Received stats: {}", stats);
            return stats;
        } catch (Exception e) {
            log.error("Failed to get stats from stats service: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public void saveHit(HttpServletRequest request) {
        try {
            EndpointHit hit = new EndpointHit();
            hit.setApp("ewm-main-service");
            hit.setUri(request.getRequestURI());
            hit.setIp(getClientIp(request));
            hit.setTimestamp(LocalDateTime.now());

            log.debug("Saving hit: {}", hit);
            statsClient.saveHit(hit);
            log.debug("Hit saved successfully");
        } catch (Exception e) {
            log.error("Failed to save hit to stats service: {}", e.getMessage(), e);
        }
    }

    public Long getEventViews(Long eventId) {
        try {
            String uri = "/events/" + eventId;
            LocalDateTime start = LocalDateTime.now().minusYears(1);
            LocalDateTime end = LocalDateTime.now().plusHours(1);

            log.debug("Getting views for event {} with uri: {}", eventId, uri);

            List<ViewStats> stats = getStats(start, end, List.of(uri), true);
            log.debug("Stats for event {}: {}", eventId, stats);

            if (stats == null || stats.isEmpty()) {
                return 0L;
            }

            return stats.get(0).getHits();
        } catch (Exception e) {
            log.error("Failed to get views for event {}: {}", eventId, e.getMessage(), e);
            return 0L;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }
        return request.getRemoteAddr();
    }
}