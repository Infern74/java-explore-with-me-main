package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        validateRequestCreation(userId, event);

        ParticipationRequest request = new ParticipationRequest();
        request.setEvent(event);
        request.setRequester(user);
        request.setCreated(LocalDateTime.now());

        // Устанавливаем статус в зависимости от настроек события
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        ParticipationRequest savedRequest = requestRepository.save(request);

        // Если заявка сразу подтверждена, обновляем счетчик подтвержденных заявок
        if (request.getStatus() == RequestStatus.CONFIRMED) {
            updateConfirmedRequestsCount(event);
        }

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

        // Можно отменять только pending заявки
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Cannot cancel request that is not in PENDING status");
        }

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updatedRequest = requestRepository.save(request);

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
        Event event = getEventAndValidateInitiator(userId, eventId);
        ParticipationRequest request = getRequestAndValidateEvent(reqId, eventId);

        validateRequestConfirmation(event, request);

        // Проверяем лимит участников
        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Event has reached participant limit");
        }

        request.setStatus(RequestStatus.CONFIRMED);
        ParticipationRequest confirmedRequest = requestRepository.save(request);

        // После подтверждения проверяем, не достигнут ли лимит
        checkAndRejectPendingRequestsIfNeeded(event);

        return ParticipationRequestMapper.toParticipationRequestDto(confirmedRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto rejectRequest(Long userId, Long eventId, Long reqId) {
        Event event = getEventAndValidateInitiator(userId, eventId);
        ParticipationRequest request = getRequestAndValidateEvent(reqId, eventId);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request must be in PENDING status");
        }

        request.setStatus(RequestStatus.REJECTED);
        ParticipationRequest rejectedRequest = requestRepository.save(request);

        return ParticipationRequestMapper.toParticipationRequestDto(rejectedRequest);
    }

    private void validateRequestCreation(Long userId, Event event) {
        // Инициатор события не может подать заявку на участие
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Event initiator cannot create request for their own event");
        }

        // Нельзя участвовать в неопубликованном событии
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        // Проверяем, не подавал ли пользователь уже заявку
        if (requestRepository.findByEventIdAndRequesterId(event.getId(), userId).isPresent()) {
            throw new ConflictException("Request from user=" + userId + " for event=" + event.getId() + " already exists");
        }

        // Проверяем лимит участников
        Long confirmedCount = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Event has reached participant limit");
        }
    }

    private Event getEventAndValidateInitiator(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Only event initiator can manage requests");
        }

        return event;
    }

    private ParticipationRequest getRequestAndValidateEvent(Long reqId, Long eventId) {
        ParticipationRequest request = requestRepository.findById(reqId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + reqId + " not found"));

        if (!request.getEvent().getId().equals(eventId)) {
            throw new ValidationException("Request doesn't belong to this event");
        }

        return request;
    }

    private void validateRequestConfirmation(Event event, ParticipationRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request must be in PENDING status");
        }
    }

    private void checkAndRejectPendingRequestsIfNeeded(Event event) {
        Long confirmedCount = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);

        // Если лимит достигнут, отклоняем все оставшиеся заявки
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            List<ParticipationRequest> pendingRequests = requestRepository.findByEventId(event.getId()).stream()
                    .filter(request -> request.getStatus() == RequestStatus.PENDING)
                    .collect(Collectors.toList());

            for (ParticipationRequest request : pendingRequests) {
                request.setStatus(RequestStatus.REJECTED);
            }

            requestRepository.saveAll(pendingRequests);
            log.info("Rejected {} pending requests for event {} due to participant limit",
                    pendingRequests.size(), event.getId());
        }
    }

    private void updateConfirmedRequestsCount(Event event) {
        // Это метод для обновления счетчика, если нужно
        log.debug("Confirmed request count updated for event {}", event.getId());
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }
    }
}