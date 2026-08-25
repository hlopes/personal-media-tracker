package org.hlopes.mapper;

import org.hlopes.dto.UserResponse;
import org.hlopes.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface UserMapper {

    UserResponse toResponse(User user);
}
