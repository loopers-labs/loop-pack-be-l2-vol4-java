package com.loopers.user.domain.command;

import com.loopers.user.domain.vo.BirthDate;
import com.loopers.user.domain.vo.Email;
import com.loopers.user.domain.vo.LoginId;
import com.loopers.user.domain.vo.PlainPassword;
import com.loopers.user.domain.vo.UserName;

public record SignUpUserCommand(
    LoginId loginId,
    PlainPassword plainPassword,
    UserName name,
    BirthDate birthDate,
    Email email
) {
}
