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
        boolean exists = userRepository.findByLoginId(user.getLoginId()).isPresent();
        if (exists) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + user.getLoginId() + "] 이미 존재하는 아이디입니다.");
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String loginId, String password) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 유저를 찾을 수 없습니다."));

        if (!user.matchesPassword(password)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    @Transactional
    public void updatePassword(String loginId, String oldPassword, String newPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 유저를 찾을 수 없습니다."));

        user.updatePassword(oldPassword, newPassword);
    }
}
