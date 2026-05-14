package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserModel signUp(UserModel user) {
        if (userRepository.existsByLoginId(user.getLoginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        return userRepository.save(user);
    }
}
