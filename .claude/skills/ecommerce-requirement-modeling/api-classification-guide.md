# API 분류 가이드

요구사항에서 API 후보를 도출할 때 user/admin API 를 분리하고, 기능 ID 와 API 계약의 연결을 점검한다.

## 분류 기준

- 대고객 기능은 `/api/v1` 하위에 둔다.
- 관리자 기능은 `/api-admin/v1` 하위에 둔다.
- 사용자 API 와 관리자 API 는 Controller, Request/Response DTO 를 분리한다.
- 동일 도메인 기능이라도 노출 정보와 권한이 다르면 API 요구사항을 분리한다.

## 식별 정책

- 유저 로그인이 필요한 기능은 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 유저를 식별한다.
- 관리자 기능은 `X-Loopers-Ldap: loopers.admin` 헤더로 관리자를 식별한다.
- 인증/인가는 주요 스코프가 아니므로 별도 보안 체계로 확장하지 않는다.
- 유저는 타 유저의 정보에 직접 접근할 수 없다.

## 기능 ID 규칙

- 기능 ID 는 유비쿼터스 언어의 영어명을 기반으로 `F-{EnglishName}-{Num}` 형식을 사용한다.
- 예: `F-Member-1`, `F-Product-2`, `F-Order-5`
- 관리자 기능도 대상 도메인의 영어명을 사용한다. 예: 관리자 상품 등록은 `F-Product-*`

## API 요구사항 점검

- API 표에는 Method, URI, 인증 여부, 기능 ID 를 포함한다.
- 상품 목록 조회는 `brandId`, `sort`, `page`, `size` 쿼리 파라미터를 명시한다.
- 정렬 기준은 `latest` 를 필수로 두고, `price_asc`, `likes_desc` 는 선택 구현으로 둘 수 있다.
