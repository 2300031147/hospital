package com.aerovhyn.auth.service;

import com.aerovhyn.common.dto.UserDto;
import com.aerovhyn.common.dto.UserCreateDto;

import java.util.List;

public interface UserService {
    List<UserDto> getAll();
    UserDto getById(Long id);
    UserDto create(UserCreateDto dto);
    UserDto update(Long id, UserCreateDto dto);
    void delete(Long id);
    void changePassword(Long id, String newPassword);
}
