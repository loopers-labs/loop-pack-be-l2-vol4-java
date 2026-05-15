package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;

public record UserInfo(
    String loginId,
    String name,
    BirthVO birthVO,
    EmailVO emailVO
) {
    public static UserInfo from(UserModel userModel) {
        String originalName = userModel.getName();
        String masking = originalName.substring(0, originalName.length() - 1) + "*";

        return new UserInfo(
            userModel.getLoginId(),
            masking,
            userModel.getBirth(),
            userModel.getEmailVO()
        );
    }
}
