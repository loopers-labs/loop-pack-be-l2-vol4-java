package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(String loginId, String password, String name, String email, String birthDate, Gender gender) {
        UserModel user = new UserModel(loginId, password, name, email, birthDate, gender);
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }

    public UserInfo getMyInfo(String loginId) {
        // [유지/제거 검토] orElseThrow는 HTTP 흐름에서 도달 불가 — LoginUserResolver가 사용자 존재를 보장한 뒤 호출됨.
        // 제거 관점: 현재 호출 경로가 HTTP 전용이므로 죽은 코드에 해당, 오버엔지니어링.
        // 유지 관점: Facade는 호출자를 신뢰하지 않아야 함 — 배치나 다른 컨텍스트에서 직접 호출될 경우 안전 보장.
        return userService.findByLoginId(loginId)
            .map(UserInfo::from)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
