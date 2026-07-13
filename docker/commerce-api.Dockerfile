# commerce-api 부하테스트용 런타임 이미지.
# 빌드 컨텍스트 = apps/commerce-api/build/libs (bootJar 를 app.jar 로 복사해 둔 곳)
#   docker build -f docker/commerce-api.Dockerfile -t commerce-api:loadtest apps/commerce-api/build/libs
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY app.jar app.jar

EXPOSE 8080 8081

# 컨테이너 메모리 limit 에 맞춰 힙을 잡는다(JVM 21 은 cgroup 인식).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "app.jar"]
