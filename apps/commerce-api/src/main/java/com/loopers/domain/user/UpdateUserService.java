package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UpdateUserService {
    private final FindUserService findUserService;
    private final PasswordEncryptor passwordEncryptor;
    private final UserRepository userRepository;

    @Transactional
    public void changePassword(String loginId, String loginPw, String oldPassword, String newPassword) {
        if (!loginPw.equals(oldPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        UserModel user = findUserService.getLoginUser(loginId, loginPw);

        user.changePassword(newPassword, passwordEncryptor);

        userRepository.save(user);
    }
}
