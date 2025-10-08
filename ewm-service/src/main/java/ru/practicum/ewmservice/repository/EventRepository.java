package ru.practicum.ewmservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByInitiatorId(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiatorId(Long eventId, Long initiatorId);

    List<Event> findByCategoryId(Long categoryId);

    @Query("SELECT e FROM Event e WHERE " +
            "(:users IS NULL OR e.initiator.id IN :users) AND " +
            "(:states IS NULL OR e.state IN :states) AND " +
            "(:categories IS NULL OR e.category.id IN :categories) AND " +
            "(e.eventDate BETWEEN :rangeStart AND :rangeEnd)")
    List<Event> findEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                  LocalDateTime rangeStart, LocalDateTime rangeEnd, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE " +
            "e.state = 'PUBLISHED' AND " +
            "(:text IS NULL OR LOWER(e.annotation) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(e.description) LIKE LOWER(CONCAT('%', :text, '%'))) AND " +
            "(:categories IS NULL OR e.category.id IN :categories) AND " +
            "(:paid IS NULL OR e.paid = :paid) AND " +
            "(e.eventDate BETWEEN :rangeStart AND :rangeEnd)")
    List<Event> findEventsByPublic(String text, List<Long> categories, Boolean paid,
                                   LocalDateTime rangeStart, LocalDateTime rangeEnd, Pageable pageable);
}