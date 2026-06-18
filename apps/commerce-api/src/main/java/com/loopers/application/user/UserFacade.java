package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRole;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserFacade {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        UserModel.validatePassword(password, birthDate);

        UserModel user = UserModel.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(password))
                .name(name)
                .role(UserRole.USER)
                .birthDate(birthDate)
                .email(email)
                .build();

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId, String password) {
        UserModel.validateLoginId(loginId);

        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        return UserInfo.fromMasked(user);
    }

    @Transactional
    public void updatePassword(
            String loginId,
            String currentPassword,
            String oldPassword,
            String newPassword
    ) {
        UserModel.validateLoginId(loginId);

        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        if (oldPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.SAME_PASSWORD_AS_OLD);
        }

        user.updatePassword(passwordEncoder.encode(newPassword), newPassword);
        userRepository.save(user);
    }
}
