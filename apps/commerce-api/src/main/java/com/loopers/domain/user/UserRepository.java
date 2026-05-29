package com.loopers.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    boolean existsByLoginId(LoginId loginId);

    Optional<UserModel> findByLoginId(LoginId loginId);

    Optional<UserModel> findById(Long id);

    List<UserModel> findAllByIdIn(Collection<Long> ids);

    UserModel save(UserModel user);
}
