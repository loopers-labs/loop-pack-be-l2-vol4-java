package com.loopers.application.auth;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class AuthFacade {

    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public AuthenticatedUserInfo authenticate(String loginId, String password) {
        userService.validateCredential(loginId, password);
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));
        userService.authenticate(user, password);
        return AuthenticatedUserInfo.from(user);
    }
}
