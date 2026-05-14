package com.loopers.application.user;

import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.UserModel;

public record UserInfo(LoginId loginId, Name name, BirthDate birthDate, Email email) {

    public static UserInfo from(UserModel user) {
        return new UserInfo(
            user.getLoginId(),
            user.getName(),
            user.getBirthDate(),
            user.getEmail()
        );
    }
}
