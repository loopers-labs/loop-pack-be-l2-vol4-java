package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@RequiredArgsConstructor
@Transactional
@Component
public class UserService {

    private final UserRepository userRepository;

    public UserModel createUserModel(String loginId, String name, BirthVO birthVO, PasswordVO passwordVO, EmailVO emailVO) {
        UserModel userModel = UserModel.of(loginId, name, birthVO, passwordVO, emailVO);
        return userRepository.save(userModel);
    }

    @Transactional(readOnly = true)
    public UserModel getUserModel(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("유저의 아이디가 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean checkLoginIdDuplication(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }
}
