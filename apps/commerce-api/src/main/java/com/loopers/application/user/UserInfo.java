package com.loopers.application.user;

import com.loopers.domain.user.UserEntity;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String userId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserEntity user) {
        return new UserInfo(
            user.getId(),
            user.getUserId(),
            user.getMaskedName(),
            user.getBirthDate(),
            user.getEmail()
        );
    }
}
