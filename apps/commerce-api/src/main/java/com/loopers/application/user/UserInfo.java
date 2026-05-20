package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Optional;

public record UserInfo(
    String loginId,
    String name,
    BirthVO birthVO,
    EmailVO emailVO
) {
    public static UserInfo from(UserModel userModel) {
        String originalName = userModel.getName();
        String masking = Optional.ofNullable(originalName)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "유저의 이름이 빈 값입니다."))
                .substring(0, originalName.length() - 1) + "*";

        return new UserInfo(
            userModel.getLoginId(),
            masking,
            userModel.getBirth(),
            userModel.getEmailVO()
        );
    }
}
