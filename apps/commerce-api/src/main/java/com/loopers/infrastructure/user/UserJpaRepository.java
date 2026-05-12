package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    @Query("select u from UserModel u where u.loginId.value = :loginId")
    Optional<UserModel> findByLoginId(@Param("loginId") String loginId);

    @Query("select case when count(u) > 0 then true else false end from UserModel u where u.loginId.value = :loginId")
    boolean existsByLoginId(@Param("loginId") String loginId);
}
