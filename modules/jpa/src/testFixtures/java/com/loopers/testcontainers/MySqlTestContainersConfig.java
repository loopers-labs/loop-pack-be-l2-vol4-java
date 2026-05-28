package com.loopers.testcontainers;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL-only 동작 검증이 필요한 테스트에서만 {@code @Import(MySqlTestContainersConfig.class)}로 옵트인한다.
 * 기본 통합 테스트는 H2 인메모리(jpa.yml test profile)로 동작한다.
 */
public class MySqlTestContainersConfig {

    private static final MySQLContainer<?> mySqlContainer;

    static {
        mySqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("loopers")
            .withUsername("test")
            .withPassword("test")
            .withExposedPorts(3306)
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_general_ci",
                "--skip-character-set-client-handshake"
            );
        mySqlContainer.start();

        String mySqlJdbcUrl = String.format(
            "jdbc:mysql://%s:%d/%s",
            mySqlContainer.getHost(),
            mySqlContainer.getFirstMappedPort(),
            mySqlContainer.getDatabaseName()
        );

        System.setProperty("datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl);
        System.setProperty("datasource.mysql-jpa.main.username", mySqlContainer.getUsername());
        System.setProperty("datasource.mysql-jpa.main.password", mySqlContainer.getPassword());
    }
}
