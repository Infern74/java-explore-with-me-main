package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewmservice.dto.UserDto;
import ru.practicum.ewmservice.exception.ConflictException;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.mapper.UserMapper;
import ru.practicum.ewmservice.model.User;
import ru.practicum.ewmservice.repository.EventRatingRepository;
import ru.practicum.ewmservice.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EventRatingRepository ratingRepository;

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) {
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new ConflictException("User with email '" + userDto.getEmail() + "' already exists");
        }
        User user = UserMapper.toUser(userDto);
        User savedUser = userRepository.save(user);

        UserDto result = UserMapper.toUserDto(savedUser);
        result.setRating(0L);

        return result;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }
        userRepository.deleteById(userId);
    }

    @Override
    public List<UserDto> getUsers(List<Long> ids, Integer from, Integer size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<User> users;

        if (ids == null || ids.isEmpty()) {
            users = userRepository.findAll(pageable).getContent();
        } else {
            users = userRepository.findByIdIn(ids, pageable);
        }

        Map<Long, Long> userRatings = getUsersRatings(users);

        return users.stream()
                .map(user -> {
                    UserDto dto = UserMapper.toUserDto(user);
                    dto.setRating(userRatings.getOrDefault(user.getId(), 0L)); // NEW
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, Long> getUsersRatings(List<User> users) {
        List<Long> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        List<Object[]> userRatings = ratingRepository.getRatingsByUserIds(userIds);
        return userRatings.stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1]
                ));
    }
}