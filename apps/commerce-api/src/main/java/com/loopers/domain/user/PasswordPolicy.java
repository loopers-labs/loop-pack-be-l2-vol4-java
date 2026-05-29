package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class PasswordPolicy {

    public static void validatePasswordNotContainBirthDay(RawPassword rawPassword, BirthDay birthDay) {
        if (rawPassword.getValue().contains(birthDay.getValue().replace("-", ""))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비밀번호에 포함될 수 없습니다.");
        }
    }

    public static void validateNotSamePassword(boolean isSameAsCurrentPassword) {
        if (isSameAsCurrentPassword) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 사용할 수 없습니다.");
        }
    }
}
