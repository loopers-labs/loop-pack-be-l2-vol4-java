package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserRepository;

import java.time.LocalDate;

public class UserV1Dto {
    public record SignUpRequest(
            String loginId,
            String password,
            String name,
            String birthDate,
            String email
    ){}

    public record UserResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static UserResponse from (UserInfo info){
            return new UserResponse(info.loginId(), info.name(), info.birthDate(), info.email());
        }
    }

    public record MyInfoResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(info.loginId(), maskName(info.name()), info.birthDate(), info.email());
        }

        private static String maskName(String name) {
            if (name == null || name.isEmpty()) return name;
            return name.substring(0, name.length() - 1) + "*";
        }
    }
}
