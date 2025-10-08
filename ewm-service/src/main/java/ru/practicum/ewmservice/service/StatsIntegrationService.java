package ru.practicum.ewmservice.service;

import ru.practicum.statsclient.StatsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.dto.EndpointHit;
import ru.practicum.dto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsIntegrationService {
    private final StatsClient statsClient;

    public void saveHit(String uri, String ip) {
        EndpointHit hit = new EndpointHit();
        hit.setApp("ewm-main-service");
        hit.setUri(uri);
        hit.setIp(ip);
        hit.setTimestamp(LocalDateTime.now());
        statsClient.saveHit(hit);
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        return statsClient.getStats(start, end, uris, unique);
    }
}