package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 환경변수 USE_LOCAL_MYSQL=true 인 경우, testcontainers 없이 로컬 docker-compose로 띄운 MySQL을 사용한다.
 * (Docker 29.x 등 testcontainers 호환 문제 회피용)
 */
@Configuration
public class MySqlTestContainersConfig {

    private static final boolean USE_LOCAL_MYSQL = "true".equalsIgnoreCase(System.getenv("USE_LOCAL_MYSQL"));

    static {
        if (USE_LOCAL_MYSQL) {
            System.setProperty("datasource.mysql-jpa.main.jdbc-url", "jdbc:mysql://localhost:3306/loopers");
            System.setProperty("datasource.mysql-jpa.main.username", "application");
            System.setProperty("datasource.mysql-jpa.main.password", "application");
        } else {
            MySQLContainer<?> mySqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
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
}
