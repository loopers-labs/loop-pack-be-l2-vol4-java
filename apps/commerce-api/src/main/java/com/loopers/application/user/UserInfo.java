package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birth,
    String email
) {
    public static UserInfo from(User user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId(),
            user.getName(),
            user.getBirth(),
            user.getEmail()
        );
    }
}
