package com.loopers.user.domain;

import com.loopers.user.domain.vo.EncodedPassword;
import com.loopers.user.domain.vo.PlainPassword;

public interface PasswordHasher {
    EncodedPassword hash(PlainPassword raw);
    boolean matches(PlainPassword raw, EncodedPassword encoded);
}