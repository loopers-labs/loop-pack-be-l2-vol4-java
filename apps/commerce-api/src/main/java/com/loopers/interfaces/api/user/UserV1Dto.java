package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.UserCommand;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public UserCommand.SignUp toCommand() {
            return new UserCommand.SignUp(
                new LoginId(loginId),
                new Name(name),
                new BirthDate(birthDate),
                new Email(email),
                password
            );
        }
    }

    public record UserResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId().getValue(),
                info.name().masked(),
                info.birthDate().getValue(),
                info.email().getValue()
            );
        }
    }
}
