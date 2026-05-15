package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class MySqlTestContainersConfig {

    private static final String LOCAL_JDBC_URL = "jdbc:mysql://localhost:3306/loopers_test";
    private static final String LOCAL_USERNAME = "application";
    private static final String LOCAL_PASSWORD = "application";

    private static final MySQLContainer<?> mySqlContainer;

    static {
        MySQLContainer<?> container = null;
        try {
            container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("loopers")
                .withUsername("test")
                .withPassword("test")
                .withExposedPorts(3306)
                .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--skip-character-set-client-handshake"
                );
            container.start();

            String mySqlJdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s",
                container.getHost(),
                container.getFirstMappedPort(),
                container.getDatabaseName()
            );

            System.setProperty("datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl);
            System.setProperty("datasource.mysql-jpa.main.username", container.getUsername());
            System.setProperty("datasource.mysql-jpa.main.password", container.getPassword());
        } catch (Exception e) {
            // Docker를 사용할 수 없는 경우 로컬 MySQL로 폴백
            System.out.println("[TestContainers] Docker 연결 실패 - 로컬 MySQL(" + LOCAL_JDBC_URL + ")로 대체합니다.");
            System.setProperty("datasource.mysql-jpa.main.jdbc-url", LOCAL_JDBC_URL);
            System.setProperty("datasource.mysql-jpa.main.username", LOCAL_USERNAME);
            System.setProperty("datasource.mysql-jpa.main.password", LOCAL_PASSWORD);
        }
        mySqlContainer = container;
    }
}
