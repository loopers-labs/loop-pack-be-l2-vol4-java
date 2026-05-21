package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class UserV1Dto {
    public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{4,20}$")
        String loginId,
        @NotBlank
        @Size(min = 8, max = 16)
        String password,
        @NotBlank
        @Size(max = 50)
        String name,
        @NotNull
        @PastOrPresent
        LocalDate birth,
        @NotBlank
        @Email
        String email
    ) {}

    public record ChangePasswordRequest(
        @NotBlank
        String oldPassword,
        @NotBlank
        @Size(min = 8, max = 16)
        String newPassword
    ) {}

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birth,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birth(),
                info.email()
            );
        }
    }
}
