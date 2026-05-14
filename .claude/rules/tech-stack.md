# 기술 스택 및 버전

`gradle.properties`, `build.gradle.kts`, `settings.gradle.kts`, `docker/infra-compose.yml` 기준입니다.
버전 변경이 필요할 때는 본 문서와 `gradle.properties` 를 함께 갱신합니다.

## 언어 / 빌드
| 항목 | 버전 | 출처 |
| --- | --- | --- |
| Java toolchain | **21** | `build.gradle.kts` (`JavaLanguageVersion.of(21)`) |
| Gradle wrapper | 프로젝트 동봉 (`./gradlew`) | `gradle/wrapper` |
| Kotlin (gradle script 용) | `2.0.20` | `gradle.properties` (`kotlinVersion`) |
| ktlint plugin | `12.1.2` | `gradle.properties` (`ktLintPluginVersion`) |
| ktlint | `1.0.1` | `gradle.properties` (`ktLintVersion`) |

## 핵심 프레임워크
| 항목 | 버전 | 비고 |
| --- | --- | --- |
| Spring Boot | **3.4.4** | `gradle.properties` (`springBootVersion`) — 모든 subproject 에 플러그인 적용 |
| Spring Dependency Management | `1.1.7` | `gradle.properties` (`springDependencyManagementVersion`) |
| Spring Cloud BOM | `2024.0.1` | `gradle.properties` (`springCloudDependenciesVersion`) — `dependencyManagement` BOM 으로 import |
| Springdoc OpenAPI (webmvc-ui) | `2.7.0` | `gradle.properties` (`springDocOpenApiVersion`) — `commerce-api` 에서 Swagger UI 노출 |
| Logback Slack Appender | `1.6.1` | `gradle.properties` (`slackAppenderVersion`) — `supports:logging` 에서 사용 |

## 테스트 / 도구
| 항목 | 버전 | 출처 |
| --- | --- | --- |
| JUnit | JUnit 5 (Spring Boot BOM) + `junit-platform-launcher` | Spring Boot BOM |
| Mockito | `5.14.0` | `gradle.properties` (`mockitoVersion`) |
| springmockk | `4.0.2` | `gradle.properties` (`springMockkVersion`) |
| Instancio JUnit | `5.0.2` | `gradle.properties` (`instancioJUnitVersion`) |
| Testcontainers | Spring Boot BOM | MySQL / Redis / Kafka 컨테이너 사용 |
| Jacoco | 모든 subproject 활성화, XML 리포트 생성 | `build.gradle.kts` |
| Lombok | Spring Boot BOM 관리 버전 | 모든 subproject 공통 |
| QueryDSL | `querydsl-jpa::jakarta` + `querydsl-apt::jakarta` annotationProcessor | `modules:jpa`, `apps/*` |

## 인프라 / 미들웨어 (로컬 docker)
`docker/infra-compose.yml` / `docker/monitoring-compose.yml` 참고.

| 컴포넌트 | 버전/이미지 | 포트 | 비고 |
| --- | --- | --- | --- |
| MySQL | `mysql:8.0` | `3306` | DB: `loopers`, user/pw: `application/application` |
| Redis master | `redis:7.0` | `6379` | AOF 활성, latency monitor 100ms |
| Redis readonly replica | `redis:7.0` | `6380` | `redis-master` 의 replica, read-only |
| Kafka | `bitnamilegacy/kafka:3.5.1` | broker `9092` / host `19092` | KRaft 단일 노드 |
| kafka-ui | `provectuslabs/kafka-ui:latest` | `9099` | 로컬 카프카 UI |
| Prometheus + Grafana | (`docker/monitoring-compose.yml`) | `3000` (Grafana) | `admin/admin` |
