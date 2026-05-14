package com.loopers.testcontainers;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class MySqlTestContainersConfig {

    static {
        try {
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
        } catch (Exception e) {
            // Docker 미사용 환경에서는 컨테이너 기동을 건너뛴다.
            // 대체 datasource(@TestPropertySource 등)로 동작하도록 위임한다.
            LoggerFactory.getLogger(MySqlTestContainersConfig.class)
                .warn("Testcontainers MySQL 기동 실패 → fallback datasource로 동작. cause={}", e.toString());
        }
    }
}
