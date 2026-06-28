package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(Long id, String loginId, String email, String nickname, LocalDate birthDate) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(user.getId(), user.getLoginId(), user.getEmail(), user.getNickname(), user.getBirthDate());
    }
}
