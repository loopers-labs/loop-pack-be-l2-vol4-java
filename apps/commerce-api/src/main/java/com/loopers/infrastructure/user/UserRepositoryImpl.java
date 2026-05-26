package com.loopers.infrastructure.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
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

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태(비밀번호)만 복사 → dirty checking으로 UPDATE.
     *   (BaseEntity의 id가 final이라 도메인을 그대로 새 엔티티로 만들면 INSERT로 오인되므로 이 경로가 필요하다.)
     */
    @Override
    public UserModel save(UserModel userModel) {
        if (userModel.getId() == null) {
            UserEntity saved = userJpaRepository.save(UserEntityMapper.toEntity(userModel));
            return UserEntityMapper.toDomain(saved);
        }
        UserEntity entity = userJpaRepository.findById(userModel.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
        entity.applyState(userModel.getPassword());
        return UserEntityMapper.toDomain(userJpaRepository.save(entity));
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return userJpaRepository.existsByLoginId(loginId.getValue());
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return userJpaRepository.findById(id).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<UserModel> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId.getValue()).map(UserEntityMapper::toDomain);
    }
}
