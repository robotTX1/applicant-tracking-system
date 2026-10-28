package hu.robottx.ats.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.PagedUserResponse;
import hu.robottx.ats.model.UserResponse;

@Mapper(uses = PageResponseMapper.class)
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(source = "firstName", target = "firstName")
    @Mapping(source = "lastName", target = "lastName")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    CurrentUserResponse toModel(User user);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedUserResponse toPagedUserResponse(Page<User> users);

    @Mapping(source = "userId", target = "id")
    @Mapping(source = "firstName", target = "firstName")
    @Mapping(source = "lastName", target = "lastName")
    @Mapping(source = "email", target = "email")
    UserResponse toUserResponse(User user);

}
