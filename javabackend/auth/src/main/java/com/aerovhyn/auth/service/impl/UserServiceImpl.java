package com.aerovhyn.auth.service.impl;

import com.aerovhyn.auth.service.UserService;
import com.aerovhyn.common.dto.UserCreateDto;
import com.aerovhyn.common.dto.UserDto;
import com.aerovhyn.common.exception.ResourceNotFoundException;
import com.aerovhyn.common.exception.ValidationException;
import com.aerovhyn.domain.entity.UserEntity;
import com.aerovhyn.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserDto> getAll() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public UserDto getById(Long id) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return toDto(entity);
    }

    @Override
    public UserDto create(UserCreateDto dto) {
        validateUserCredentials(dto.username(), dto.password());

        if (userRepository.existsByUsername(dto.username())) {
            throw new ValidationException("Username already exists");
        }

        UserEntity entity = new UserEntity(
                dto.username(),
                passwordEncoder.encode(dto.password()),
                dto.fullName(),
                dto.role() != null ? dto.role() : "paramedic"
        );
        entity.setAmbulanceId(dto.ambulanceId());
        entity.setHospitalId(dto.hospitalId());

        return toDto(userRepository.save(entity));
    }

    @Override
    public UserDto update(Long id, UserCreateDto dto) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (dto.fullName() != null) entity.setFullName(dto.fullName());
        if (dto.role() != null) entity.setRole(dto.role());
        if (dto.ambulanceId() != null) entity.setAmbulanceId(dto.ambulanceId());
        if (dto.hospitalId() != null) entity.setHospitalId(dto.hospitalId());

        return toDto(userRepository.save(entity));
    }

    @Override
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        userRepository.deleteById(id);
    }

    @Override
    public void changePassword(Long id, String newPassword) {
        validatePasswordStrength(newPassword);
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        entity.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(entity);
    }

    private void validateUserCredentials(String username, String password) {
        if (username == null || username.length() < 3) {
            throw new ValidationException("Username must be at least 3 characters");
        }
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            throw new ValidationException("Username must be alphanumeric");
        }
        validatePasswordStrength(password);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
        boolean hasUppercase = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUppercase = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasUppercase) {
            throw new ValidationException("Password must contain an uppercase letter");
        }
        if (!hasDigit) {
            throw new ValidationException("Password must contain a digit");
        }
    }

    private UserDto toDto(UserEntity entity) {
        return new UserDto(
                entity.getId(),
                entity.getUsername(),
                entity.getFullName(),
                entity.getRole(),
                entity.getAmbulanceId(),
                entity.getHospitalId(),
                entity.getCreatedAt() != null
                        ? entity.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null
        );
    }
}
