package ru.practicum.ewmservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewmservice.dto.RatingStatsDto;
import ru.practicum.ewmservice.model.EventRating;

import java.util.List;
import java.util.Optional;

public interface EventRatingRepository extends JpaRepository<EventRating, Long> {

    Optional<EventRating> findByUserIdAndEventId(Long userId, Long eventId);

    List<EventRating> findByEventId(Long eventId);

    List<EventRating> findByUserId(Long userId);

    @Query("SELECT COUNT(er) FROM EventRating er WHERE er.event.id = :eventId AND er.isLike = true")
    Long countLikesByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COUNT(er) FROM EventRating er WHERE er.event.id = :eventId AND er.isLike = false")
    Long countDislikesByEventId(@Param("eventId") Long eventId);

    @Query("SELECT er.event.initiator.id, SUM(CASE WHEN er.isLike = true THEN 1 ELSE -1 END) " +
            "FROM EventRating er " +
            "WHERE er.event.initiator.id IN :userIds " +
            "GROUP BY er.event.initiator.id")
    List<Object[]> getRatingsByUserIds(@Param("userIds") List<Long> userIds);

    @Query("SELECT new ru.practicum.ewmservice.dto.RatingStatsDto(" +
            "e.id, e.title, e.initiator.id, e.initiator.name, " +
            "COUNT(CASE WHEN er.isLike = true THEN 1 END), " +
            "COUNT(CASE WHEN er.isLike = false THEN 1 END), " +
            "COUNT(CASE WHEN er.isLike = true THEN 1 END) - COUNT(CASE WHEN er.isLike = false THEN 1 END)) " +
            "FROM Event e LEFT JOIN EventRating er ON e.id = er.event.id " +
            "WHERE e.state = 'PUBLISHED' " +
            "GROUP BY e.id, e.title, e.initiator.id, e.initiator.name " +
            "ORDER BY (COUNT(CASE WHEN er.isLike = true THEN 1 END) - COUNT(CASE WHEN er.isLike = false THEN 1 END)) DESC")
    List<RatingStatsDto> getEventsRatingStats();
}