package ru.practicum.ewmservice.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventState;

import java.time.LocalDateTime;
import java.util.List;

public class EventSpecifications {

    public static Specification<Event> hasTextInAnnotationOrDescription(String text) {
        return (root, query, criteriaBuilder) -> {
            if (text == null || text.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            String likePattern = "%" + text.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("annotation")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likePattern)
            );
        };
    }

    public static Specification<Event> hasCategories(List<Long> categoryIds) {
        return (root, query, criteriaBuilder) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return root.get("category").get("id").in(categoryIds);
        };
    }

    public static Specification<Event> isPaid(Boolean paid) {
        return (root, query, criteriaBuilder) -> {
            if (paid == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("paid"), paid);
        };
    }

    public static Specification<Event> isInDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return (root, query, criteriaBuilder) -> {
            if (rangeStart == null && rangeEnd == null) {
                return criteriaBuilder.greaterThan(root.get("eventDate"), LocalDateTime.now());
            }
            if (rangeStart == null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get("eventDate"), rangeEnd);
            }
            if (rangeEnd == null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("eventDate"), rangeStart);
            }
            return criteriaBuilder.between(root.get("eventDate"), rangeStart, rangeEnd);
        };
    }

    public static Specification<Event> hasStates(List<EventState> states) {
        return (root, query, criteriaBuilder) -> {
            if (states == null || states.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return root.get("state").in(states);
        };
    }

    public static Specification<Event> hasUsers(List<Long> userIds) {
        return (root, query, criteriaBuilder) -> {
            if (userIds == null || userIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return root.get("initiator").get("id").in(userIds);
        };
    }

    public static Specification<Event> isPublished() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("state"), EventState.PUBLISHED);
    }
}