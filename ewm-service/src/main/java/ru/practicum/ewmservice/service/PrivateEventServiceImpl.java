package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.dto.NewEventDto;
import ru.practicum.ewmservice.dto.UpdateEventUserRequest;
import ru.practicum.ewmservice.exception.ConflictException;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.exception.ValidationException;
import ru.practicum.ewmservice.mapper.EventMapper;
import ru.practicum.ewmservice.model.Category;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventState;
import ru.practicum.ewmservice.model.User;
import ru.practicum.ewmservice.repository.CategoryRepository;
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
public class PrivateEventServiceImpl implements PrivateEventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatsIntegrationService statsIntegrationService;

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        if (from < 0) {
            throw new ValidationException("From must be non-negative");
        }
        if (size <= 0) {
            throw new ValidationException("Size must be positive");
        }

        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable);

        return events.stream()
                .map(event -> {
                    Long views = statsIntegrationService.getEventViews(event.getId());
                    Long confirmedRequests = requestRepository.getConfirmedRequestsCount(event.getId());
                    return EventMapper.toEventShortDto(event, views, confirmedRequests);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));
        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + newEventDto.getCategory() + " not found"));

        validateEventFields(newEventDto);

        // Проверка даты события (минимум 2 часа от текущего момента)
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        Event event = new Event();
        event.setTitle(newEventDto.getTitle().trim());
        event.setAnnotation(newEventDto.getAnnotation().trim());
        event.setDescription(newEventDto.getDescription().trim());
        event.setCategory(category);
        event.setInitiator(user);
        event.setEventDate(newEventDto.getEventDate());
        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);
        event.setLocation(newEventDto.getLocation());
        event.setPaid(newEventDto.getPaid() != null ? newEventDto.getPaid() : false);
        event.setParticipantLimit(newEventDto.getParticipantLimit() != null ? newEventDto.getParticipantLimit() : 0);
        event.setRequestModeration(newEventDto.getRequestModeration() != null ? newEventDto.getRequestModeration() : true);

        Event savedEvent = eventRepository.save(event);

        // Для нового события views и confirmedRequests будут 0
        return EventMapper.toEventFullDto(savedEvent, 0L, 0L);
    }

    private void validateEventFields(NewEventDto newEventDto) {
        // Валидация аннотации
        if (newEventDto.getAnnotation() == null || newEventDto.getAnnotation().trim().isEmpty()) {
            throw new ValidationException("Annotation cannot be empty");
        }
        String annotation = newEventDto.getAnnotation().trim();
        if (annotation.length() < 20 || annotation.length() > 2000) {
            throw new ValidationException("Annotation must be between 20 and 2000 characters");
        }

        // Валидация описания
        if (newEventDto.getDescription() == null || newEventDto.getDescription().trim().isEmpty()) {
            throw new ValidationException("Description cannot be empty");
        }
        String description = newEventDto.getDescription().trim();
        if (description.length() < 20 || description.length() > 7000) {
            throw new ValidationException("Description must be between 20 and 7000 characters");
        }

        // Валидация заголовка
        if (newEventDto.getTitle() == null || newEventDto.getTitle().trim().isEmpty()) {
            throw new ValidationException("Title cannot be empty");
        }
        String title = newEventDto.getTitle().trim();
        if (title.length() < 3 || title.length() > 120) {
            throw new ValidationException("Title must be between 3 and 120 characters");
        }

        // Валидация лимита участников
        if (newEventDto.getParticipantLimit() != null && newEventDto.getParticipantLimit() < 0) {
            throw new ValidationException("Participant limit cannot be negative");
        }
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found for user=" + userId));

        Long views = statsIntegrationService.getEventViews(eventId);
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(eventId);
        return EventMapper.toEventFullDto(event, views, confirmedRequests);
    }

    @Override
    @Transactional
    public EventFullDto updateEvent(Long userId, Long eventId, UpdateEventUserRequest updateEventUserRequest) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found for user=" + userId));

        // Проверка, что событие можно редактировать
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        // Валидация полей при обновлении
        validateUpdateEventFields(updateEventUserRequest);

        // Обновление полей
        updateEventFields(event, updateEventUserRequest);

        // Обработка stateAction
        if (updateEventUserRequest.getStateAction() != null) {
            if ("SEND_TO_REVIEW".equals(updateEventUserRequest.getStateAction())) {
                event.setState(EventState.PENDING);
            } else if ("CANCEL_REVIEW".equals(updateEventUserRequest.getStateAction())) {
                event.setState(EventState.CANCELED);
            } else {
                throw new ValidationException("Invalid state action: " + updateEventUserRequest.getStateAction());
            }
        }

        Event updatedEvent = eventRepository.save(event);

        Long views = statsIntegrationService.getEventViews(eventId);
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(eventId);
        return EventMapper.toEventFullDto(updatedEvent, views, confirmedRequests);
    }

    private void validateUpdateEventFields(UpdateEventUserRequest updateRequest) {
        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        if (updateRequest.getAnnotation() != null) {
            String annotation = updateRequest.getAnnotation().trim();
            if (annotation.isEmpty()) {
                throw new ValidationException("Annotation cannot be empty");
            }
            if (annotation.length() < 20 || annotation.length() > 2000) {
                throw new ValidationException("Annotation must be between 20 and 2000 characters");
            }
        }

        if (updateRequest.getDescription() != null) {
            String description = updateRequest.getDescription().trim();
            if (description.isEmpty()) {
                throw new ValidationException("Description cannot be empty");
            }
            if (description.length() < 20 || description.length() > 7000) {
                throw new ValidationException("Description must be between 20 and 7000 characters");
            }
        }

        if (updateRequest.getTitle() != null) {
            String title = updateRequest.getTitle().trim();
            if (title.isEmpty()) {
                throw new ValidationException("Title cannot be empty");
            }
            if (title.length() < 3 || title.length() > 120) {
                throw new ValidationException("Title must be between 3 and 120 characters");
            }
        }

        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new ValidationException("Participant limit cannot be negative");
        }
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle().trim());
        }
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation().trim());
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription().trim());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + updateRequest.getCategory() + " not found"));
            event.setCategory(category);
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(updateRequest.getLocation());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
    }
}