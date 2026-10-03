package org.hlopes.auth.mapper;

import org.hlopes.auth.dto.UserResponse;
import org.hlopes.auth.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface UserMapper {

    UserResponse toResponse(User user);
}
