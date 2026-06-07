# task 단계 — 실행 체크박스 단위

plan.md를 읽고 implement에서 하나씩 실행할 체크박스 task로 분해한다. 각 task는 추가 설명 없이 바로 실행 가능할 만큼 구체적이어야 한다(정확한 파일 경로 포함).

## 절차

1. 시나리오 폴더의 `plan.md`를 읽는다.
2. `templates/task-template.md`를 같은 폴더에 `task.md`로 복사한다.
3. 페이즈와 task를 채운다.
4. 다 채우면 task 목록을 요약 보고하고 gate.

## 체크박스 포맷

```
- [ ] T001 설명 + 정확한 파일 경로
```

- `T001`: 실행 순서 번호.
- 설명에 항상 구체 경로를 포함한다.
- 완료 시 implement 단계에서 `- [X]`로 체크.

## 페이즈 구성

- **Foundational (도메인 첫 시나리오만)**: 이 도메인의 여러 시나리오가 공유할 aggregate 골격 — Model, VO, Repository 인터페이스 + RepositoryImpl + JpaRepository. 이 페이즈가 끝나야 use case 구현 가능. 이미 도메인 골격이 있으면 생략.
- **구현**: 레이어 순서대로 — 도메인(Model/VO/Service) → 인프라(RepositoryImpl/JpaRepository) → 애플리케이션(Facade/Info) → 인터페이스(Controller/Dto/ApiSpec). 각 레이어마다 해당 테스트 작성 task를 함께 둔다(spec의 테스트 계획과 매핑).
- **마무리**: spec 테스트 계획 대비 누락 점검, .http 파일 등 시나리오가 요구하는 부수 산출물.

## 주의

- 테스트 task는 포함하되 RED-first 순서를 강제하지 않는다(구현과 같은 묶음으로 둬도 된다).
- 한 task가 너무 커지면 쪼갠다(파일 하나·관심사 하나 기준).
- cross-cutting하게 여러 시나리오에 걸치는 일은 만들지 않는다 — 이 시나리오 범위만.
