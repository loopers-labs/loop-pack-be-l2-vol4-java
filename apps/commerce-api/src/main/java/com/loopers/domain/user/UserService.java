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
        userRepository.findByLoginId(user.getLoginId())
            .ifPresent(existing -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 ID입니다.");
            });
        return userRepository.save(user);
    }
}
