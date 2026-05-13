package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel register (UserModel user) {
        if (userRepository.existsByLoginId(user.getLoginId())) {
            throw new CoreException(ErrorType.CONFLICT);
        }
        user.encodePassword(passwordEncoder);
        return userRepository.save(user);
    }

}
