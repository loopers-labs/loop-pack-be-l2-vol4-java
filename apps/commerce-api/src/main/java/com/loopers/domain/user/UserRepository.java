package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    UserModel save(UserModel user);

    UserModel getActiveById(Long id);

    Optional<UserModel> findActiveByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
