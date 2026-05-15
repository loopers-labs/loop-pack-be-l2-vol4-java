package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class FindUserService {
    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional(readOnly = true)
    public UserModel getLoginUser(String loginId, String loginPw) {
        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        if (!user.matchesPassword(loginPw, passwordEncryptor)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

}
