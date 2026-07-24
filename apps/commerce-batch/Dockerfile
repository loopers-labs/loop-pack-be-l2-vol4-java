# commerce-batch 이미지.
# Gradle 멀티 모듈 구조라서 빌드 컨텍스트는 반드시 저장소 루트여야 한다.
#   docker build -f apps/commerce-batch/Dockerfile -t loopers/commerce-batch:local .

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# 의존성 캐시가 먼저 재사용되도록 Gradle 설정 파일을 소스보다 먼저 복사한다.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY apps ./apps
COPY modules ./modules
COPY supports ./supports

RUN ./gradlew :apps:commerce-batch:bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system loopers && useradd --system --gid loopers loopers

COPY --from=build /workspace/apps/commerce-batch/build/libs/*.jar app.jar
RUN chown loopers:loopers app.jar

USER loopers

ENV TZ=Asia/Seoul
# 컨테이너 메모리 한도에 맞춰 힙을 잡기 위한 기본값. k8s manifest의 JAVA_OPTS로 덮어쓸 수 있다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

# requestDate 등 실행 시점에 결정되는 인자는 k8s CronJob의 command에서 채워 넣는다
# (예: requestDate=$(date +%F)). Job Parameter는 "--" 없이 key=value로 넘겨야
# Spring Boot의 getNonOptionArgs()에 잡힌다 — "--"를 붙이면 Spring Boot 옵션으로
# 먼저 소비돼 jobParameters에서 빠진다(01-design.md #6.3). 로컬 실행 시에는 CMD 뒤에
# 직접 인자를 붙이면 된다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar \"$@\"", "--"]
