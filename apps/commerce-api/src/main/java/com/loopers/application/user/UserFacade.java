package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional
    public UserInfo signup(String loginId, String password, String name, LocalDate birth, String email) {
        boolean loginIdExists = userRepository.existsByLoginId(loginId);
        UserModel user = userService.signup(loginId, password, name, birth, email, loginIdExists);
        return UserInfo.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId) {
        UserModel user = getUser(loginId);
        return UserInfo.fromMasked(user);
    }

    @Transactional
    public void changePassword(String loginId, String oldPassword, String newPassword) {
        UserModel user = getUser(loginId);
        userService.changePassword(user, oldPassword, newPassword);
        userRepository.save(user);
    }

    private UserModel getUser(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 회원을 찾을 수 없습니다."));
    }
}
