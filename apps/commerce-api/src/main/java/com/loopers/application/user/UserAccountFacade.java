package com.loopers.application.user;

import com.loopers.domain.user.PasswordPolicy;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserAccountFacade {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserInfo register(UserRegisterCommand command) {
        // 1. 비밀번호 형식 검증 (raw 기준, 암호화 전에 수행)
        passwordPolicy.validate(command.password(), command.birthDate());

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(command.password());

        // 3. UserModel 생성 (암호화된 비밀번호 전달)
        UserModel user = command.toDomain(encodedPassword);

        // 4. 중복 loginId 검증 + 저장
        UserModel saved = userService.register(user);

        // 5. 반환
        return UserInfo.from(saved);
    }

    public void changePassword(UserModel user, UserChangePasswordCommand command) {
        if (!passwordEncoder.matches(command.currentPassword(), user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        passwordPolicy.validate(command.newPassword(), user.getBirthDate());
        if (passwordEncoder.matches(command.newPassword(), user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        String encodedNewPassword = passwordEncoder.encode(command.newPassword());
        userService.changePassword(user, encodedNewPassword);
    }

    public UserInfo getMe(UserModel user) {
        // AuthInterceptor에서 인증이 완료된 UserModel을 그대로 수신
        // 이름 마스킹(비즈니스 규칙)을 적용해 UserInfo 생성
        return new UserInfo(user.getId(), user.getLoginId(), user.maskedName(), user.getBirthDate(), user.getEmail());
    }
}
