package com.loopers.domain.user.exception;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class UserAlreadyExistsException extends CoreException {
    public UserAlreadyExistsException(String loginId) {
        super(ErrorType.CONFLICT, "이미 존재하는 로그인 ID 입니다: " + loginId);
    }
}
