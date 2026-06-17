name: "📐 Tech Note — Design Doc 스타일"
description: "설계 의사결정 중심. '왜 이 구조인가'를 풀어쓰는 포맷."
title: "[Design Doc] 키워드 (N주차 · K팀 · 이름)"
labels: ["tech-note", "format:design-doc"]
body:
  - type: markdown
    attributes:
      value: |
        > **언제 고르나** — 새 시스템·모듈을 설계했고, 구조와 의사결정을 면접관/시니어 리뷰어에게 설명하고 싶을 때.
        > **AI 채점 PR과 별개** — 본 이슈는 피드백용입니다.
        >
        > 제목의 `키워드`, `N`, `K`, `이름` 부분을 본인 내용으로 바꿔주세요.
        > 제목 예시: `[Design Doc] 분산 락 기반 랭킹 시스템 (3주차 · B팀 · 김루퍼)`
        > 본문 골격은 가이드입니다. **안 쓰는 섹션은 통째로 지워도 됩니다.**

  - type: input
    id: tldr
    attributes:
      label: "TL;DR"
      description: "5초 안에 핵심을 전하는 1~2줄."
      placeholder: "예) Redis Sorted Set + 분산 락으로 동시성 안전한 실시간 랭킹을 구현했고, p99 80ms → 25ms로 개선."
    validations:
      required: true

  - type: textarea
    id: body
    attributes:
      label: "본문"
      description: |
        아래 골격은 가이드입니다. 본인의 강조점에 맞춰 자유롭게 변형하세요.

        **작성 팁**
        - 수치로 증명하라 — "잘 됐다"가 아니라 "p99 200ms → 25ms".
        - 왜 그 선택을 했는가를 함께 적으라 — 기술 나열은 약하다.
        - 솔직한 한계도 적으라 — "이건 못 풀었다"가 시니어 시그널이다.
      value: |
        ## Introduction & Goals

        - **Context / Background**:
        - **Goals**:

        ## Detailed Design

        ### System Architecture

        ### Data Models

        ### API Design

        ### Constraints

        ## Alternatives Considered

        | 옵션 | Pros | Cons |
        |------|------|------|
        | A    |      |      |
        | B    |      |      |
        | **선택: C** |      |      |

        **선택 근거:**
    validations:
      required: true

  - type: textarea
    id: cross-cutting
    attributes:
      label: "Cross-cutting Concerns"
      description: "비기능적 요구사항. 해당되는 항목만 적으면 됩니다. 비워둬도 OK."
      placeholder: |
        - Scalability: 트래픽 증가 시 어떻게 대응할 것인가?
        - Latency: 응답 속도에 미치는 영향은?
        - Security & Privacy: PII, 권한 제어, 암호화
        - Observability: 로깅·메트릭·알람 계획
    validations:
      required: false

  - type: textarea
    id: reference
    attributes:
      label: "Reference"
      description: "참고한 공식 문서, 인용한 글, 영상, 본인 코드 블록 링크 등을 자유롭게 정리하세요."
      placeholder: |
        - Redis 공식 문서: ...
        - 참고 블로그: ...
        - 본인 코드: https://github.com/.../blob/...
    validations:
      required: false


----------------------------------------------------------------------------------

name: "🪞 Tech Note — Retrospective 스타일"
description: "과제 회고 중심. 기술 결정 + 트러블슈팅 + 배운 점을 정리. 가장 진입장벽이 낮은 포맷."
title: "[Retrospective] 키워드 (N주차 · K팀 · 이름)"
labels: ["tech-note", "format:retrospective"]
body:
  - type: markdown
    attributes:
      value: |
        > **언제 고르나** — 과제를 끝낸 직후, 결정 과정과 배운 점을 회고하고 싶을 때.
        > **AI 채점 PR과 별개** — 본 이슈는 피드백용입니다.
        >
        > 제목의 `키워드`, `N`, `K`, `이름` 부분을 본인 내용으로 바꿔주세요.
        > 제목 예시: `[Retrospective] Cache Stampede 대응기 (5주차 · A팀 · 박코딩)`
        > 본문 골격은 가이드입니다. **안 쓰는 섹션은 통째로 지워도 됩니다.**

  - type: input
    id: tldr
    attributes:
      label: "TL;DR"
      description: "5초 안에 핵심을 전하는 1~2줄."
      placeholder: "예) Redisson 분산 락으로 동시성 문제를 해결했고, 정합성 실패율 3% → 0%로 개선."
    validations:
      required: true

  - type: textarea
    id: body
    attributes:
      label: "본문"
      description: |
        아래 골격은 가이드입니다. 본인의 강조점에 맞춰 자유롭게 변형하세요.

        **작성 팁**
        - 기술 결정은 "왜 그 선택을 했는가"가 핵심.
        - 트러블슈팅엔 수치를 곁들이면 강력해진다.
      value: |
        ## 개요 (Overview)

        - **목표**:
        - **중점 사항**:

        ## 핵심 기술 및 결정 (Technical Decisions)

        | 기술/설계 항목 | 선택한 대안 | 선택 이유 (Rationale) |
        |---------------|------------|----------------------|
        |               |            |                      |

        ## 트러블슈팅 (Troubleshooting)

        ### 문제 현상

        ### 원인 분석
        -

        ### 해결 방안
        -

        ## 회고 (Retrospective)

        - **Keep**:
        - **Problem**:
        - **Try**:
    validations:
      required: true

  - type: textarea
    id: deep-dive
    attributes:
      label: "Deep Dive"
      description: "새롭게 알게 된 원리를 본인 과제에 어떻게 적용했는지까지 연결. 위키 복붙은 지양. 비워둬도 OK."
      placeholder: |
        - 개념/원리: 예) Redis 싱글 스레드 아키텍처와 원자적 연산
        - 본인 과제에 어떻게 작용했나: 예) 그래서 INCR/ZADD가 race condition 없이 동작
    validations:
      required: false

  - type: textarea
    id: reference
    attributes:
      label: "Reference"
      description: "참고한 공식 문서, 인용한 글, 영상, 본인 코드 블록 링크 등을 자유롭게 정리하세요."
      placeholder: |
        - Redisson 공식 문서: ...
        - 본인 코드: https://github.com/.../blob/...
    validations:
      required: false


----------------------------------------------------------------------------------

name: "⚔️ Tech Note — Challenge Story 스타일"
description: "'도전 → 해결 → 통찰' 압축 서사. 짧고 강력하게 본인 역량을 보여주고 싶을 때."
title: "[Challenge Story] 키워드 (N주차 · K팀 · 이름)"
labels: ["tech-note", "format:challenge-story"]
body:
  - type: markdown
    attributes:
      value: |
        > **언제 고르나** — 과제의 가장 어려웠던 도전 하나에 집중해 서사로 풀고 싶을 때. 짧고 임팩트 있게.
        > **AI 채점 PR과 별개** — 본 이슈는 피드백용입니다.
        >
        > 제목의 `키워드`, `N`, `K`, `이름` 부분을 본인 내용으로 바꿔주세요.
        > 제목 예시: `[Challenge Story] Kafka 이벤트 유실 추적 (7주차 · C팀 · 이학습)`
        > 본문 골격은 가이드입니다. **안 쓰는 섹션은 통째로 지워도 됩니다.**

  - type: input
    id: tldr
    attributes:
      label: "TL;DR"
      description: "5초 안에 핵심을 전하는 1~2줄."
      placeholder: "예) Kafka 이벤트 유실의 원인을 컨슈머 리밸런싱으로 추적, idempotent producer로 해결."
    validations:
      required: true

  - type: textarea
    id: body
    attributes:
      label: "본문"
      description: |
        아래 골격은 가이드입니다. 본인의 강조점에 맞춰 자유롭게 변형하세요.

        **작성 팁**
        - 기술을 나열하지 말고, *도전 과제와 묶어* 서술하라.
        - 결과는 숫자로. "잘 됐다"가 아니라 "p99 200ms → 25ms".
      value: |
        ## Context (배경 및 목표)

        - **어떤 시스템을 만드는가?**:
        - **가장 큰 기술적 도전 과제는?**:

        ## Design & Implementation (설계 및 구현)

        ### 핵심 기술 선택

        ### Logic Flow

        ## Engineering Challenges (트러블슈팅 및 최적화)

        - **예상치 못한 현상**:
        - **추론 및 검증**:
        - **최종 해결**:

        ## Verification & Insight (검증)

        - 결과 지표 (예: p99 200ms → 25ms, 에러율 3% → 0.01%):
    validations:
      required: true

  - type: textarea
    id: lessons-learned
    attributes:
      label: "Lessons Learned"
      description: "이 과정에서 얻은 아키텍처적 교훈 1~3가지. 비워둬도 OK."
      placeholder: |
        1. 이벤트 기반 시스템에선 idempotency가 기본 가정이어야 한다.
        2. 컨슈머 리밸런싱 동작을 모르면 유실의 원인을 디버깅 못한다.
    validations:
      required: false

  - type: textarea
    id: reference
    attributes:
      label: "Reference"
      description: "참고한 공식 문서, 인용한 글, 영상, 본인 코드 블록 링크 등을 자유롭게 정리하세요."
      placeholder: |
        - Kafka 공식 문서: ...
        - 본인 코드: https://github.com/.../blob/...
    validations:
      required: false




----------------------------------------------------------------------------------





name: "📊 Tech Note — Benchmark Report 스타일"
description: "두 구현/기술의 직접 측정 비교. 수치 기반으로 선택 근거를 증명하고 싶을 때."
title: "[Benchmark] 키워드 (N주차 · K팀 · 이름)"
labels: ["tech-note", "format:benchmark"]
body:
  - type: markdown
    attributes:
      value: |
        > **언제 고르나** — 후보 기술 A vs B(vs C)를 본인이 직접 측정해 선택한 경우. 가장 강력한 자기 증명 자료.
        > **AI 채점 PR과 별개** — 본 이슈는 피드백용입니다.
        >
        > 제목의 `키워드`, `N`, `K`, `이름` 부분을 본인 내용으로 바꿔주세요.
        > 제목 예시: `[Benchmark] Redisson vs Lettuce 분산 락 측정 (4주차 · A팀 · 정개발)`
        > 본문 골격은 가이드입니다. **안 쓰는 섹션은 통째로 지워도 됩니다.**

  - type: input
    id: tldr
    attributes:
      label: "TL;DR"
      description: "1~2줄로 핵심을 전한다."
      placeholder: "예) Redisson과 Lettuce 분산 락을 직접 측정한 결과 Redisson이 p99 3배 빠름. 운영 복잡도까지 고려해 Redisson 채택."
    validations:
      required: true

  - type: textarea
    id: body
    attributes:
      label: "본문"
      description: |
        아래 골격은 가이드입니다. 본인의 강조점에 맞춰 자유롭게 변형하세요.

        **작성 팁**
        - Setup이 약하면 결과 신뢰도가 떨어진다. 재현 가능하게 적으라.
        - 선택 이유엔 성능 외 요소(운영 복잡도·의존성·학습 곡선)도 함께 적으라.
      value: |
        ## Question (무엇을 비교하는가)

        - **결정해야 했던 것**:
        - **후보**: A vs B (vs C)

        ## Setup (측정 환경)

        - **환경**: (CPU/메모리/컨테이너 구성)
        - **부하 도구**: (k6 / JMeter / Locust / 자체 스크립트)
        - **시나리오**: RPS, 동시 사용자 수, 데이터 규모

        ## Results (측정 결과)

        | 항목 | A | B |
        |------|---|---|
        | p99 레이턴시 |  |  |
        | Throughput (RPS) |  |  |
        | 에러율 |  |  |

        ## Decision

        - **선택**:
        - **선택 이유**: (수치 해석 + 운영 복잡도·의존성·학습 곡선 종합)
        - **포기한 것**:
    validations:
      required: true

  - type: textarea
    id: future-work
    attributes:
      label: "Future Work"
      description: "운영 도입 시 고려사항, 추가 측정 계획, 재평가 시점 등. 비워둬도 OK."
      placeholder: |
        - 운영 도입 시: 모니터링/롤백 전략은 어떻게 설계할 것인가
        - 추가 측정: 부하 패턴이 X로 바뀌면 재측정 필요
        - 재평가 시점: RPS가 10배 증가하면 다시 비교
    validations:
      required: false

  - type: textarea
    id: reference
    attributes:
      label: "Reference"
      description: "참고한 공식 문서, 인용한 글, 영상, 벤치마크 코드 등을 자유롭게 정리하세요."
      placeholder: |
        - 벤치마크 코드: https://github.com/.../...
        - 참고 자료: ...
    validations:
      required: false