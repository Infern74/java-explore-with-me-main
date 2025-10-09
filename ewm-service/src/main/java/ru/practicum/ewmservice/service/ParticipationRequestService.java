package ru.practicum.ewmservice.service;

import ru.practicum.ewmservice.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewmservice.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewmservice.dto.ParticipationRequestDto;
import java.util.List;

public interface ParticipationRequestService {
    List<ParticipationRequestDto> getUsersRequests(Long userId);

    ParticipationRequestDto createRequest(Long userId, Long eventId);

    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    ParticipationRequestDto confirmRequest(Long userId, Long eventId, Long reqId);

    ParticipationRequestDto rejectRequest(Long userId, Long eventId, Long reqId);

    EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest request);

}