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
    public UserModel createUser(String loginId, String loginPw) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + loginId + "] 이미 존재하는 아이디입니다.");
        }
        return userRepository.save(new UserModel(loginId, loginPw));
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String loginId, String loginPw) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 유저를 찾을 수 없습니다."));
        if (!user.getLoginPw().equals(loginPw)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 유저를 찾을 수 없습니다.");
        }
        return user;
    }
}
