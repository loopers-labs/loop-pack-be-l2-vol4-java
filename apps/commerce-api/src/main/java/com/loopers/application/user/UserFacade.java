package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    public UserInfo register(String loginId, String password, String name, String birth, String email) {
        UserModel userModel = new UserModel(loginId, password, name, birth, email);
        UserModel saved = userService.register(userModel);
        return UserInfo.from(saved);
    }

    /**
     * 내 정보 조회 — loginId + password 로 인증 후 마스킹된 정보를 반환한다.
     * 인증 실패(존재하지 않는 회원 / 비밀번호 불일치) 시 UNAUTHORIZED.
     */
    public UserInfo getMyInfo(String loginId, String password) {
        UserModel user = userService.findByLoginId(loginId);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return UserInfo.from(user);
    }

    /** 비밀번호 변경 — 기존 비밀번호 불일치 시 UNAUTHORIZED, 새 비밀번호 RULE 위반 시 BAD_REQUEST. */
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        userService.changePassword(loginId, currentPassword, newPassword);
    }
}
