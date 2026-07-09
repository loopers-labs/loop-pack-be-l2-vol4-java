package com.loopers.infrastructure.useractionlog;

import com.loopers.domain.useractionlog.UserActionLogModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActionLogJpaRepository extends JpaRepository<UserActionLogModel, Long> {
}
