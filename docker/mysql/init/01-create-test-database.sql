CREATE DATABASE IF NOT EXISTS loopers_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

GRANT ALL PRIVILEGES ON loopers_test.* TO 'application'@'%';
