package com.loopers.support.fake;

import com.loopers.domain.BaseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/** 단위 테스트에서 BaseEntity 의 식별자(JPA 가 채워주는 final 필드)를 강제 주입한다. */
public final class IdFixtures {

    private IdFixtures() {}

    public static <T extends BaseEntity> T assignId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
