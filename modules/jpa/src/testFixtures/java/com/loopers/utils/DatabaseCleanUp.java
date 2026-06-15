package com.loopers.utils;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseCleanUp implements InitializingBean {

    @PersistenceContext
    private EntityManager entityManager;

    private final List<String> tableNames = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        entityManager.getMetamodel().getEntities().stream()
            .map(entity -> entity.getJavaType())
            .filter(type -> type.getAnnotation(Entity.class) != null)
            .forEach(type -> {
                tableNames.add(type.getAnnotation(Table.class).name());
                // @ElementCollection 등 컬렉션 테이블은 @Entity 가 아니라 메타모델 엔티티에 잡히지 않으므로
                // 각 엔티티 필드의 @CollectionTable 을 직접 수집해 truncate 대상에 포함한다.
                addCollectionTables(type);
            });
    }

    private void addCollectionTables(Class<?> entityType) {
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                CollectionTable collectionTable = field.getAnnotation(CollectionTable.class);
                if (collectionTable != null && !collectionTable.name().isBlank()) {
                    tableNames.add(collectionTable.name());
                }
            }
        }
    }

    @Transactional
    public void truncateAllTables() {
        entityManager.flush();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        for (String table : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE `" + table + "`").executeUpdate();
        }

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
