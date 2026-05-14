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
    public UserModel signUp(UserModel userModel) {
        if (userRepository.existsByLoginId(userModel.getLoginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 loginId입니다.");
        }
        return userRepository.save(userModel);
    }

    @Transactional(readOnly = true)
    public UserModel getMyInfo(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserModel user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));

        if (!user.matchesPassword(currentPassword)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (currentPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이전과 동일한 비밀번호는 사용할 수 없습니다.");
        }

        user.changePassword(newPassword);
        userRepository.save(user);
    }
}
