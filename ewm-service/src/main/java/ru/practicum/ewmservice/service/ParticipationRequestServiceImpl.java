package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    public List<ParticipationRequestDto> getUsersRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        return requestRepository.findByRequesterId(userId).stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        // Проверка, что пользователь не инициатор события
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Event initiator cannot create request for their own event");
        }

        // Проверка, что событие опубликовано
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        // Проверка на дублирование заявки
        if (requestRepository.findByEventIdAndRequesterId(eventId, userId).isPresent()) {
            throw new ConflictException("Request from user=" + userId + " for event=" + eventId + " already exists");
        }

        // Проверка лимита участников
        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Event has reached participant limit");
        }

        ParticipationRequest request = new ParticipationRequest();
        request.setEvent(event);
        request.setRequester(user);
        request.setCreated(LocalDateTime.now());

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        ParticipationRequest savedRequest = requestRepository.save(request);
        return ParticipationRequestMapper.toParticipationRequestDto(savedRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " not found"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new ValidationException("User can only cancel their own requests");
        }

        RequestStatus oldStatus = request.getStatus();
        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updatedRequest = requestRepository.save(request);

        // Если заявка была подтверждена, уменьшаем счетчик подтвержденных заявок
        if (oldStatus == RequestStatus.CONFIRMED) {
            // Обновляем счетчик через репозиторий событий
            Event event = request.getEvent();
            Long confirmedCount = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
            // Мы не обновляем событие напрямую, так как confirmedRequests вычисляется динамически
        }

        return ParticipationRequestMapper.toParticipationRequestDto(updatedRequest);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Only event initiator can view event requests");
        }

        return requestRepository.findByEventId(eventId).stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto confirmRequest(Long userId, Long eventId, Long reqId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Only event initiator can confirm requests");
        }

        ParticipationRequest request = requestRepository.findById(reqId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + reqId + " not found"));

        if (!request.getEvent().getId().equals(eventId)) {
            throw new ValidationException("Request doesn't belong to this event");
        }

        // Проверка лимита участников
        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Event has reached participant limit");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request must be in PENDING status");
        }

        request.setStatus(RequestStatus.CONFIRMED);
        ParticipationRequest confirmedRequest = requestRepository.save(request);

        // Проверяем, не достигнут ли лимит после подтверждения
        confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            rejectPendingRequests(eventId);
        }

        return ParticipationRequestMapper.toParticipationRequestDto(confirmedRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto rejectRequest(Long userId, Long eventId, Long reqId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Only event initiator can reject requests");
        }

        ParticipationRequest request = requestRepository.findById(reqId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + reqId + " not found"));

        if (!request.getEvent().getId().equals(eventId)) {
            throw new ValidationException("Request doesn't belong to this event");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request must be in PENDING status");
        }

        request.setStatus(RequestStatus.REJECTED);
        ParticipationRequest rejectedRequest = requestRepository.save(request);

        return ParticipationRequestMapper.toParticipationRequestDto(rejectedRequest);
    }

    private void rejectPendingRequests(Long eventId) {
        List<ParticipationRequest> pendingRequests = requestRepository.findByEventId(eventId).stream()
                .filter(request -> request.getStatus() == RequestStatus.PENDING)
                .collect(Collectors.toList());

        for (ParticipationRequest request : pendingRequests) {
            request.setStatus(RequestStatus.REJECTED);
        }

        requestRepository.saveAll(pendingRequests);
    }
}