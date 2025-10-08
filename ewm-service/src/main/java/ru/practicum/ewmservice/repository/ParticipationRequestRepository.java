package ru.practicum.ewmservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewmservice.model.ParticipationRequest;
import ru.practicum.ewmservice.model.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {
    List<ParticipationRequest> findByRequesterId(Long requesterId);

    List<ParticipationRequest> findByEventId(Long eventId);

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long requesterId);

    Long countByEventIdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findByIdIn(List<Long> requestIds);

    @Query("SELECT COUNT(pr) FROM ParticipationRequest pr WHERE pr.event.id = :eventId AND pr.status = 'CONFIRMED'")
    Long getConfirmedRequestsCount(@Param("eventId") Long eventId);
}