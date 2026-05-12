package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public record MyInfoV1Response(
    String loginId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static MyInfoV1Response from(UserInfo info) {
        return new MyInfoV1Response(
            info.loginId(),
            info.maskedName(),
            info.birthDate(),
            info.email()
        );
    }
}
