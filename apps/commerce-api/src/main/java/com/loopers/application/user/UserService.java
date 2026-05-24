package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final UserFinder userFinder;

    public UserModel create(String loginId, String loginPw, String name, String birthDate, String email, Gender gender) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        return userRepository.save(new UserModel(loginId, loginPw, name, birthDate, email, gender, passwordEncryptor));
    }

    public void changePassword(String loginId, String loginPw, String oldPassword, String newPassword) {
        if (!loginPw.equals(oldPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        UserModel user = userFinder.getLoginUser(loginId, loginPw);
        user.changePassword(newPassword, passwordEncryptor);
        userRepository.save(user);
    }
}
