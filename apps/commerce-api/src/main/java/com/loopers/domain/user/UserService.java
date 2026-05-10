package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    public UserModel createUserModel(String loginId, String name, LocalDate birth, String password, String email) {
        BirthVO birthVO = new BirthVO(birth);
        PasswordVO passwordVO = new PasswordVO(password);
        EmailVO emailVO = new EmailVO(email);

        if (userRepository.findByLoginId(loginId).isPresent()) {
            throw new IllegalArgumentException(ErrorType.CONFLICT.getMessage());
        }

        UserModel userModel = UserModel.of(loginId, name, birthVO, passwordVO, emailVO);
        return userRepository.save(userModel);
    }
}
