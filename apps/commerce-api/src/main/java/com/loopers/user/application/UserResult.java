package com.loopers.user.application;

import com.loopers.user.domain.User;

import java.time.LocalDate;

public class UserResult {

    public record Detail(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static Detail from(User user) {
            return new Detail(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getBirthDate(),
                user.getEmail()
            );
        }
    }
}
