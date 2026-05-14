package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;

import java.time.format.DateTimeFormatter;

// maskedName은 UserModel.getMaskedName()에서 한 번만 계산해 저장한다.
// 마스킹 규칙 변경 시 UserModel 한 곳만 수정하면 모든 흐름에 반영된다.
public record UserInfo(Long id, String loginId, String name, String maskedName, String email, String birthDate, Gender gender) {

    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getId(),
            model.getLoginId(),
            model.getName(),
            model.getMaskedName(),
            model.getEmail(),
            model.getBirthDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            model.getGender()
        );
    }
}
