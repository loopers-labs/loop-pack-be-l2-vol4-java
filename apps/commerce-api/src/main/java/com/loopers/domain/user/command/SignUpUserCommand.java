package com.loopers.domain.user.command;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.PlainPassword;
import com.loopers.domain.user.vo.UserName;

public record SignUpUserCommand(
    LoginId loginId,
    PlainPassword plainPassword,
    UserName name,
    BirthDate birthDate,
    Email email
) {
}
