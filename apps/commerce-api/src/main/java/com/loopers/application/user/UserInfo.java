package com.loopers.application.user;

import java.time.LocalDate;

public record UserInfo(
        String loginId,
        String name,
        LocalDate birthday,
        String email
) {}
