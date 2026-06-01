package com.loopers.user.application;

import com.loopers.user.domain.Gender;
import com.loopers.user.domain.UserModel;
import com.loopers.user.domain.UserRepository;
import com.loopers.user.domain.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    public UserInfo signUp(String loginId, String password, String name, String email, String birthDate, Gender gender) {
        UserModel newUser = new UserModel(loginId, password, name, email, birthDate, gender);
        UserModel saved = userRepository.save(userService.signUp(userRepository.findByLoginId(loginId), newUser));
        return UserInfo.from(saved);
    }

    @Transactional
    public void changePassword(String loginId, String newPassword) {
        UserModel user = userService.getOrThrow(userRepository.findByLoginId(loginId));
        userService.changePassword(user, newPassword);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId) {
        // [유지/제거 검토] orElseThrow는 HTTP 흐름에서 도달 불가 — LoginUserResolver가 사용자 존재를 보장한 뒤 호출됨.
        // 제거 관점: 현재 호출 경로가 HTTP 전용이므로 죽은 코드에 해당, 오버엔지니어링.
        // 유지 관점: Facade는 호출자를 신뢰하지 않아야 함 — 배치나 다른 컨텍스트에서 직접 호출될 경우 안전 보장.
        return UserInfo.from(userService.getOrThrow(userRepository.findByLoginId(loginId)));
    }
}
