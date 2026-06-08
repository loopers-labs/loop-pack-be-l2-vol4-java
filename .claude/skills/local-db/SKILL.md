---
name: local-db
description: 로컬 개발용 MySQL/Redis 인프라를 정리하고 Docker 컨테이너로 띄운다. Spring 앱 실행 전 DB 연결 에러(Access denied, Connection refused)가 발생할 때, 또는 사용자가 "도커 mysql 띄워줘", "로컬 디비 시작해줘" 라고 할 때 사용한다.
---

## 목적

로컬에 설치된 Homebrew MySQL과 Docker MySQL이 같은 3306 포트를 두고 충돌하는 문제를 자동 해결한다.

## 배경 — 왜 충돌이 일어나는가

- Homebrew MySQL은 IPv4 `localhost:3306`에 바인딩됨
- Docker MySQL은 IPv6 `*:3306`(모든 인터페이스)에 바인딩됨
- 둘 다 켜져 있으면, JDBC가 `localhost`로 접속할 때 IPv4가 우선 매칭되어 **로컬 Homebrew MySQL**에 붙음
- 로컬 MySQL의 `application@localhost` 유저는 비밀번호가 달라서 `Access denied for user 'application'@'localhost'` 에러 발생

이 에러는 JDBC URL을 `127.0.0.1`로 바꿔도 같은 결과가 나올 수 있는데, MySQL이 클라이언트를 `localhost`로 식별하기 때문이다. **근본 해결책은 로컬 MySQL을 끄는 것**이다.

## 실행 순서

### 1. 현재 3306 포트 점유 상태 확인

```bash
lsof -i :3306
```

결과에 `mysqld`(로컬 Homebrew)와 `com.docke`(Docker)가 모두 있으면 충돌 상태.

### 2. 로컬 Homebrew MySQL 중지

```bash
brew services list | grep -i mysql
brew services stop mysql
```

`mysql@8.0` 같은 버전 명시 서비스가 켜져 있으면 해당 이름으로 중지.

### 3. Docker MySQL 컨테이너 확인 및 시작

기존 컨테이너 사용:
```bash
docker ps -a | grep mysql
docker start docker-mysql-1
```

컨테이너가 없으면 프로젝트의 docker-compose로 시작:
```bash
docker-compose -f ./docker/infra-compose.yml up -d
```

### 4. 포트 점유 재확인

```bash
lsof -i :3306
```

`com.docke`만 남아 있어야 정상.

### 5. 연결 테스트 (선택)

```bash
docker exec docker-mysql-1 mysql -u application -papplication -e "SELECT 1;"
```

## 자주 일어나는 에러와 대응

| 에러 메시지 | 원인 | 대응 |
|---|---|---|
| `Access denied for user 'application'@'localhost'` | 로컬 MySQL이 떠 있어 그쪽에 붙음 | `brew services stop mysql` |
| `Communications link failure` | Docker 컨테이너 미실행 | `docker start docker-mysql-1` |
| `Public Key Retrieval is not allowed` | MySQL 8 인증 플러그인 이슈 | JDBC URL에 `?allowPublicKeyRetrieval=true&useSSL=false` 추가 |

## 주의사항

- 로컬 MySQL을 끄면 다른 프로젝트에 영향이 갈 수 있다 — 작업 끝나면 사용자에게 다시 켤지 물어본다
- JDBC URL을 `127.0.0.1`로 바꿔도 IPv4 충돌 우선순위는 동일하므로 의미 없다. URL 수정으로 우회하려 하지 말 것
- `docker ps -a`로 정지된 컨테이너까지 확인 — 기존 컨테이너가 있으면 `docker start`가 새로 만드는 것보다 빠르고 데이터도 유지됨
