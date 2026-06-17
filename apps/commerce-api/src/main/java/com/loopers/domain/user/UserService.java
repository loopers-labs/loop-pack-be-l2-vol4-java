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
    public UserModel register(String loginId, String loginPw, String email, String nickname) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        return userRepository.save(new UserModel(loginId, loginPw, email, nickname));
    }

    @Transactional(readOnly = true)
    public UserModel login(String loginId, String loginPw) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
        if (!user.getLoginPw().equals(loginPw)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 올바르지 않습니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserModel getUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public UserModel getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
    }
}
