package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    UserModel save(UserModel user);

    Optional<UserModel> findById(Long id);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
