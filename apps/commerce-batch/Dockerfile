FROM eclipse-temurin:21-jre-jammy

ARG JAR_FILE=apps/commerce-batch/build/libs/commerce-batch.jar

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --home-dir /app --no-create-home spring

WORKDIR /app

COPY --chown=spring:spring ${JAR_FILE} application.jar

USER spring

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
