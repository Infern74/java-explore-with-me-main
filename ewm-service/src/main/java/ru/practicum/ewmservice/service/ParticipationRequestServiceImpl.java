package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewmservice.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewmservice.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewmservice.dto.ParticipationRequestDto;
import ru.practicum.ewmservice.exception.ConflictException;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.exception.ValidationException;
import ru.practicum.ewmservice.mapper.ParticipationRequestMapper;
import ru.practicum.ewmservice.model.*;
import ru.practicum.ewmservice.repository.EventRepository;
import ru.practicum.ewmservice.repository.ParticipationRequestRepository;
import ru.practicum.ewmservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    public List<ParticipationRequestDto> getUsersRequests(Long userId) {
        checkUserExists(userId);
        return requestRepository.findByRequesterId(userId).stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        User user = getUserByIdOrThrow(userId);
        Event event = getEventByIdOrThrow(eventId);

        validateRequestCreation(userId, event);

        ParticipationRequest request = new ParticipationRequest();
        request.setEvent(event);
        request.setRequester(user);
        request.setCreated(LocalDateTime.now());

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        try {
            ParticipationRequest savedRequest = requestRepository.save(request);
            return ParticipationRequestMapper.toParticipationRequestDto(savedRequest);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Request from user=" + userId + " for event=" + eventId + " already exists");
        }
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = getRequestByIdOrThrow(requestId);

        if (!request.getRequester().getId().equals(userId)) {
            throw new ValidationException("User can only cancel their own requests");
        }

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updatedRequest = requestRepository.save(request);

        return ParticipationRequestMapper.toParticipationRequestDto(updatedRequest);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Getting requests for event {} by user {}", eventId, userId);

        Event event = getEventByIdOrThrow(eventId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("User is not the initiator of this event");
        }

        return requestRepository.findByEventId(eventId).stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    private void validateRequestCreation(Long userId, Event event) {
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Event initiator cannot create request for their own event");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        if (requestRepository.findByEventIdAndRequesterId(event.getId(), userId).isPresent()) {
            throw new ConflictException("Request from user=" + userId + " for event=" + event.getId() + " already exists");
        }

        if (event.getParticipantLimit() > 0) {
            Long confirmedCount = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
            if (confirmedCount >= event.getParticipantLimit()) {
                throw new ConflictException("Event has reached participant limit");
            }
        }
    }

    private Event getEventAndValidateInitiator(Long userId, Long eventId) {
        Event event = getEventByIdOrThrow(eventId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Only event initiator can manage requests");
        }

        return event;
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest request) {
        log.info("Updating request status: userId={}, eventId={}, request={}", userId, eventId, request);

        Event event = getEventAndValidateInitiator(userId, eventId);
        List<ParticipationRequest> requestsToUpdate = requestRepository.findByIdIn(request.getRequestIds());

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        boolean limitReached = event.getParticipantLimit() > 0 &&
                confirmedCount >= event.getParticipantLimit();

        if (limitReached && "CONFIRMED".equals(request.getStatus())) {
            throw new ConflictException("Event has reached participant limit");
        }

        for (ParticipationRequest participationRequest : requestsToUpdate) {
            if (!participationRequest.getEvent().getId().equals(eventId)) {
                throw new ValidationException("Request doesn't belong to this event");
            }

            if (participationRequest.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must be in PENDING status");
            }

            if ("CONFIRMED".equals(request.getStatus())) {
                if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
                    participationRequest.setStatus(RequestStatus.REJECTED);
                    rejected.add(ParticipationRequestMapper.toParticipationRequestDto(participationRequest));
                } else {
                    participationRequest.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(ParticipationRequestMapper.toParticipationRequestDto(participationRequest));
                    confirmedCount++;
                }
            } else if ("REJECTED".equals(request.getStatus())) {
                participationRequest.setStatus(RequestStatus.REJECTED);
                rejected.add(ParticipationRequestMapper.toParticipationRequestDto(participationRequest));
            }
        }

        requestRepository.saveAll(requestsToUpdate);
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    private User getUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));
    }

    private Event getEventByIdOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));
    }

    private ParticipationRequest getRequestByIdOrThrow(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " not found"));
    }
}