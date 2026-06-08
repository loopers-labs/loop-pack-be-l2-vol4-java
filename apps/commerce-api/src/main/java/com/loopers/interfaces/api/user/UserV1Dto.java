package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record CreateUserRequest(String loginId, String loginPw) {}

    public record UserResponse(Long id, String loginId) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.id(), info.loginId());
        }
    }
}
