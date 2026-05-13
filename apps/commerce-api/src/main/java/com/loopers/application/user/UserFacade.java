package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
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

        userService.checkLoginIdDuplication(loginId);

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
        userModel.validPasswordChange(oldPassword, targetPassword, bCryptPasswordEncoder::matches);

        String encrypted = bCryptPasswordEncoder.encode(targetPassword);
        userService.changePassword(userModel, encrypted);
    }
}
