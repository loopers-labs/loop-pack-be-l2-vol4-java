package com.loopers.domain;

import java.lang.reflect.Field;

public final class EntityTestSupport {

    private EntityTestSupport() {
    }

    public static void setId(Object entity, Long id) {
        try {
            Field idField = getField(entity.getClass(), "id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field getField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
