package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public class UserInfo {

    public record User(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static User from(UserModel model) {
            return new User(
                model.getId(),
                model.getLoginId().getValue(),
                model.getName(),
                model.getBirthDate().getValue(),
                model.getEmail().getValue()
            );
        }
    }
}
