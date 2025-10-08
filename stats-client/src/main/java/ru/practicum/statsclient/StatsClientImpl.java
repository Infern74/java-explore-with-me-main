package ru.practicum.statsclient;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import ru.practicum.dto.EndpointHit;
import ru.practicum.dto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class StatsClientImpl implements StatsClient {

    private final RestTemplate restTemplate;
    private final String serverUrl;

    @Override
    public void saveHit(EndpointHit endpointHit) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<EndpointHit> requestEntity = new HttpEntity<>(endpointHit, headers);
        restTemplate.exchange(
                serverUrl + "/hit",
                HttpMethod.POST,
                requestEntity,
                Void.class
        );
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String uri = serverUrl + "/stats?start={start}&end={end}&unique={unique}";
        Map<String, Object> parameters = Map.of(
                "start", start.format(formatter),
                "end", end.format(formatter),
                "unique", unique
        );

        if (uris != null && !uris.isEmpty()) {
            uri += "&uris={uris}";
            parameters.put("uris", String.join(",", uris));
        }

        ResponseEntity<List<ViewStats>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                parameters
        );

        return response.getBody();
    }
}