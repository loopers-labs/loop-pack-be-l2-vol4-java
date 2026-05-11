# Testcontainers Docker 연결 트러블슈팅

## 문제 요약

통합 테스트 실행 시 Testcontainers가 Docker에 연결하지 못해 `IllegalStateException: Could not find a valid Docker environment` 에러 발생.

---

## 환경

| 항목 | 버전 |
|------|------|
| Docker Desktop | 4.52.0 (210994) |
| Docker Engine | 29.0.1 |
| Docker API version | 1.52 (minimum 1.44) |
| Testcontainers | 1.20.6 (Spring Boot 3.4.4 BOM) |
| docker-java | 3.4.1 (Testcontainers 내부 의존성) |
| Java | 21 |
| OS | Windows 11 Pro |

---

## 근본 원인

**Docker Desktop 4.52.0의 최소 API 버전(1.44)과 docker-java 기본 API 버전(1.43) 불일치**

```
Docker Desktop 4.52.0 → MinAPIVersion: 1.44
docker-java 3.4.1    → 기본 요청 버전: 1.43
```

docker-java는 요청 시 URL에 API 버전을 포함한다:
- `GET /v1.43/info` → Docker가 **400 Bad Request** 반환
- `GET /v1.44/info` → Docker가 **200 OK** 반환

---

## 진단 과정

### 1. 에러 로그 확인

```
EnvironmentAndSystemPropertyClientProviderStrategy: failed with exception BadRequestException (Status 400: {"ID":"","Containers":0,...})
```

- Docker가 정상 가동 중이지만 400 응답
- 에러 메시지에 "API 버전이 낮다"는 정보가 없어 원인 파악이 어려웠음

### 2. API 버전별 curl 테스트로 원인 특정

```shell
curl http://localhost:2375/v1.44/info  # 200 OK ✅
curl http://localhost:2375/v1.43/info  # 400 Bad Request ❌
```

### 3. Docker 최소 API 버전 확인 방법

```shell
docker version
# Server:
#   API version: 1.52 (minimum version 1.44)  ← 여기서 확인
```

---

## 해결 방법

`apps/commerce-api/src/test/resources/docker-java.properties`:

```properties
api.version=1.44
```

docker-java가 초기화 시점에 클래스패스에서 이 파일을 직접 로딩하므로 확실히 적용된다.

---

## 향후 대응

- Docker Desktop 업데이트 후 MinAPIVersion이 변경되면 `docker-java.properties`의 `api.version` 값을 조정
- `docker version` 명령으로 `minimum version` 확인 후 맞추면 됨
- Testcontainers/docker-java가 충분히 업그레이드되면 이 설정은 불필요해질 수 있음