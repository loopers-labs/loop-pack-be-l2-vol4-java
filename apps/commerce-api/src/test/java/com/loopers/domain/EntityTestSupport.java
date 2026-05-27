package com.loopers.domain;

import java.lang.reflect.Field;

public final class EntityTestSupport {

    private EntityTestSupport() {
    }

    public static void setId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
