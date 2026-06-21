package com.loopers.testcontainers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class MySqlTestContainersConfig implements EnvironmentPostProcessor {

    private static final MySQLContainer<?> mySqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("loopers")
        .withUsername("test")
        .withPassword("test")
        .withExposedPorts(3306)
        .withCommand(
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_general_ci",
            "--skip-character-set-client-handshake"
        );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isTestcontainersEnabled()) {
            return;
        }

        startContainer();

        String mySqlJdbcUrl = mySqlContainer.getJdbcUrl();
        Map<String, Object> properties = Map.of(
            "datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl,
            "datasource.mysql-jpa.main.username", mySqlContainer.getUsername(),
            "datasource.mysql-jpa.main.password", mySqlContainer.getPassword()
        );
        environment.getPropertySources().addFirst(new MapPropertySource("testcontainers-mysql", properties));

        System.setProperty("datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl);
        System.setProperty("datasource.mysql-jpa.main.username", mySqlContainer.getUsername());
        System.setProperty("datasource.mysql-jpa.main.password", mySqlContainer.getPassword());
    }

    private static synchronized void startContainer() {
        if (mySqlContainer.isRunning()) {
            return;
        }
        try {
            mySqlContainer.start();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Failed to start MySQL Testcontainer. "
                    + "Set LOOPERS_TESTCONTAINERS_ENABLED=false to use external local infrastructure.",
                e
            );
        }
    }

    private static boolean isTestcontainersEnabled() {
        String value = System.getProperty("loopers.testcontainers.enabled");
        if (value == null) {
            value = System.getenv("LOOPERS_TESTCONTAINERS_ENABLED");
        }
        return value == null || !value.equalsIgnoreCase("false");
    }
}
