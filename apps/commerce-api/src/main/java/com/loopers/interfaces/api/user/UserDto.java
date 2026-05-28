package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class UserDto {

    private UserDto() {}

    public static final class Register {

        private Register() {}

        public static final class V1 {

            private V1() {}

            public record Request(
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

            public record Response(
                Long id,
                String loginId,
                String name,
                LocalDate birth,
                String email,
                UserStatus status
            ) {
                public static Response from(UserInfo info) {
                    return new Response(
                        info.id(),
                        info.loginId(),
                        info.name(),
                        info.birth(),
                        info.email(),
                        UserStatus.ACTIVE
                    );
                }
            }

            public enum UserStatus {
                ACTIVE
            }
        }
    }

    public static final class GetMyInfo {

        private GetMyInfo() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long id,
                String loginId,
                String name,
                LocalDate birth,
                String email,
                UserStatus status
            ) {
                public static Response from(UserInfo info) {
                    return new Response(
                        info.id(),
                        info.loginId(),
                        info.name(),
                        info.birth(),
                        info.email(),
                        UserStatus.ACTIVE
                    );
                }
            }

            public enum UserStatus {
                ACTIVE
            }
        }
    }

    public static final class ChangePassword {

        private ChangePassword() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotBlank
                String oldPassword,
                @NotBlank
                @Size(min = 8, max = 16)
                String newPassword
            ) {}
        }
    }
}
