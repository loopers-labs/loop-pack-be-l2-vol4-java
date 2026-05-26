package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    UserModel save(UserModel user);

    UserModel getById(Long id);

    Optional<UserModel> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
