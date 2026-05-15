package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserChangePasswordCommand;
import com.loopers.application.user.UserInfo;
import com.loopers.application.user.UserRegisterCommand;
import lombok.Builder;

import java.time.LocalDate;

public class UserV1Dto {

    public record UserRegisterRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public UserRegisterCommand toCommand() {
            return new UserRegisterCommand(loginId, password, name, birthDate, email);
        }
    }

    public record UserChangePasswordRequest(String currentPassword, String newPassword) {
        public UserChangePasswordCommand toCommand() {
            return new UserChangePasswordCommand(currentPassword, newPassword);
        }
    }

    public record UserMeResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static UserMeResponse from(UserInfo info) {
            // info.name()은 Facade에서 이미 마스킹된 값
            return new UserMeResponse(info.loginId(), info.name(), info.birthDate(), info.email());
        }
    }

    @Builder
    public record UserRegisterResponse(Long id, String loginId, String name, LocalDate birthDate, String email) {
        public static UserRegisterResponse from(UserInfo info) {
            return UserRegisterResponse.builder()
                    .id(info.id())
                    .loginId(info.loginId())
                    .name(info.name())
                    .birthDate(info.birthDate())
                    .email(info.email())
                    .birthDate(info.birthDate())
                    .build();
        }
    }
}
