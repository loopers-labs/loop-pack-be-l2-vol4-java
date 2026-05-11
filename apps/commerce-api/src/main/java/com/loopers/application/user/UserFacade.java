package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public UserInfo createUser(String loginId, String name, LocalDate birth, String password, String email) {
        BirthVO birthVO = new BirthVO(birth);
        EmailVO emailVO = new EmailVO(email);

        if (userService.checkLoginIdDuplication(loginId)) {
            throw new IllegalArgumentException(ErrorType.CONFLICT.getMessage());
        }

        if (password.contains(String.valueOf(birthVO.toInt()))) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 생년월일은 비밀번호 내에 포함할 수 없습니다.");
        }

        String encrypted = bCryptPasswordEncoder.encode(password);
        UserModel userModel = userService.createUserModel(loginId, name, encrypted, birthVO, emailVO);

        return UserInfo.from(userModel);
    }

    public UserInfo getUserInfo(Long id) {
        UserModel userModel = userService.getUserModel(id);
        return UserInfo.from(userModel);
    }

    public void changePassword(Long id, String oldPassword, String targetPassword) {
        UserModel userModel = userService.getUserModel(id);

        if (!bCryptPasswordEncoder.matches(oldPassword, userModel.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (bCryptPasswordEncoder.matches(targetPassword, userModel.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호는 사용할 수 없습니다.");
        }
        String encrypted = bCryptPasswordEncoder.encode(targetPassword);

        userService.changePassword(userModel, encrypted);
    }
}
