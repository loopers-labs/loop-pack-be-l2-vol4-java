package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@RequiredArgsConstructor
@Transactional
@Component
public class UserService {

    private final UserRepository userRepository;

    public UserModel createUserModel(String loginId, String name, String password, BirthVO birthVO, EmailVO emailVO) {
        UserModel userModel = UserModel.of(loginId, name, password, birthVO, emailVO);
        return userRepository.save(userModel);
    }

    @Transactional(readOnly = true)
    public UserModel getUserModel(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저의 아이디가 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public void checkLoginIdDuplication(String loginId) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 유저의 아이디입니다.");
        }
    }

    public void changePassword(UserModel userModel, String encrypted) {
        userModel.changePassword(encrypted);
        userRepository.save(userModel);
    }
}
