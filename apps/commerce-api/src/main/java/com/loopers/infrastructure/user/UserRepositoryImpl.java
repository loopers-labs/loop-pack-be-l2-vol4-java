package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User domain) {
        if (domain.getId() == null) {
            UserEntity entity = new UserEntity(
                domain.getLoginId(), domain.getLoginPassword(),
                domain.getName(), domain.getBirthday(), domain.getEmail()
            );
            return userJpaRepository.save(entity).toDomain();
        }
        UserEntity entity = userJpaRepository.findById(domain.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다."));
        entity.updateFrom(domain);
        return userJpaRepository.save(entity).toDomain();
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginId(loginId)
            .map(UserEntity::toDomain);
    }
}
