package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.application.user.UserRegisterCommand;
import com.loopers.domain.user.UserModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

public class UserV1Dto {

    public record UserRegisterRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull  LocalDate birthDate,
        @NotBlank String email
    ) {
        public UserRegisterCommand toCommand() {
            return new UserRegisterCommand(loginId, password, name, birthDate, email);
        }
    }

    public record UserChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record UserMeResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static UserMeResponse from(UserModel model) {
            return new UserMeResponse(model.getLoginId(), model.maskedName(), model.getBirthDate(), model.getEmail());
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
                    .build();
        }
    }
}
