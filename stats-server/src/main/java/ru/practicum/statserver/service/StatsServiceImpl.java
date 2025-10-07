package ru.practicum.statserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.statserver.model.EndpointHit;
import ru.practicum.statserver.model.ViewStats;
import ru.practicum.statserver.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private final StatsRepository statsRepository;

    @Override
    @Transactional
    public EndpointHit saveHit(ru.practicum.dto.EndpointHit endpointHitDto) {
        EndpointHit modelHit = new EndpointHit();
        modelHit.setApp(endpointHitDto.getApp());
        modelHit.setUri(endpointHitDto.getUri());
        modelHit.setIp(endpointHitDto.getIp());
        modelHit.setTimestamp(endpointHitDto.getTimestamp());

        return statsRepository.save(modelHit);
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        if (unique) {
            return statsRepository.getUniqueStats(start, end, uris);
        } else {
            return statsRepository.getStats(start, end, uris);
        }
    }
}