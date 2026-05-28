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
    private final PasswordEncoder passwordEncoder;

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

    /**
     * 비밀번호 변경 — loginId 기반(로그인 컨텍스트). 현재 비밀번호 검증은 여기서 한 번만 수행한다.
     * 자격 증명 단계 실패(부재/불일치)는 UNAUTHORIZED로 통일 응대해 계정 존재 여부를 노출하지 않는다.
     */
    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel user = userRepository.findByLoginId(new LoginId(loginId))
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!user.matchesPassword(currentPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (currentPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이전과 동일한 비밀번호는 사용할 수 없습니다.");
        }

        user.changePassword(newPassword, passwordEncoder);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(new LoginId(loginId))
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지않습니다."));
        if (!user.matchesPassword(rawPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return user;
    }
}
