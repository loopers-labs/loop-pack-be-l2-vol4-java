package com.loopers.testcontainers;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class MySqlTestContainersConfig {

    private static final Logger logger = Logger.getLogger(MySqlTestContainersConfig.class.getName());

    private static final String LOCAL_JDBC_URL = "jdbc:mysql://localhost:3306/loopers_test";
    private static final String LOCAL_USERNAME = "application";
    private static final String LOCAL_PASSWORD = "application";
    private static final String FALLBACK_FLAG = "TESTCONTAINERS_FALLBACK_ENABLED";

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
            String fallbackEnabled = System.getenv(FALLBACK_FLAG);

            if ("true".equalsIgnoreCase(fallbackEnabled)) {
                // 명시적 폴백 플래그가 활성화된 경우만 로컬 MySQL 사용
                logger.log(Level.WARNING, "Docker/Testcontainers 연결 실패. 로컬 MySQL로 폴백합니다. (JDBC: " + LOCAL_JDBC_URL + ")", e);
                System.setProperty("datasource.mysql-jpa.main.jdbc-url", LOCAL_JDBC_URL);
                System.setProperty("datasource.mysql-jpa.main.username", LOCAL_USERNAME);
                System.setProperty("datasource.mysql-jpa.main.password", LOCAL_PASSWORD);
            } else {
                // 기본 동작: fail-fast
                logger.log(Level.SEVERE, "Docker/Testcontainers 시작 실패. 폴백을 사용하려면 " + FALLBACK_FLAG + "=true 환경변수를 설정하세요.", e);
                throw new RuntimeException("Testcontainers 초기화 실패 - Docker를 사용할 수 없습니다.", e);
            }
        }
        mySqlContainer = container;
    }
}
