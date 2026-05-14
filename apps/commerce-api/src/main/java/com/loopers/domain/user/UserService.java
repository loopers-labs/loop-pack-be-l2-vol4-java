package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public UserModel signUp(String loginId, String password, String name, String birthDate, String email, Gender gender) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        return userRepository.save(new UserModel(loginId, password, name, birthDate, email, gender, passwordHasher));
    }

    @Transactional(readOnly = true)
    public UserModel findMyInfo(String loginId, String loginPw) {
        return userRepository.findByLoginId(loginId)
            .map(user -> {
                if (!user.matchesPassword(loginPw, passwordHasher)) {
                    throw new CoreException(ErrorType.BAD_REQUEST, "아이디 또는 비밀번호가 올바르지 않습니다.");
                }
                return user;
            })
            .orElse(null);
    }

    @Transactional
    public void changePassword(String loginId, String loginPw, String oldPassword, String newPassword) {
        UserModel user = Optional.ofNullable(findMyInfo(loginId, loginPw))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 회원을 찾을 수 없습니다."));

        user.changePassword(oldPassword, newPassword, passwordHasher);

        userRepository.save(user);
    }
}
