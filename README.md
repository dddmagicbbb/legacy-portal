# 사내 업무 포털 (Legacy) - 클린코드 & 리팩토링 실습

「클린코드 & 리팩토링 실전」 과정의 **관통 실습 코드베이스**입니다.
결재 / 공지 / 일정 세 도메인을 가진 Spring Boot 애플리케이션이며, **의도적으로 나쁜 코드(코드 스멜)** 를 심어 두었습니다.
Claude Code 와 함께 **진단 → 안전망(특성화 테스트) → 작은 단계 리팩토링** 순서로 개선해 나가는 과정을 커밋 이력과 문서로 남깁니다.

> 리팩토링 = **내부 구조만 개선하고, 겉보기 동작·계약은 100% 보존**한다.
> API URL · public 메서드 시그니처 · 요청/응답 JSON · DB 저장값 · "조용히 무시" 같은 레거시 동작까지 그대로 둔다. 자세한 규칙은 [`CLAUDE.md`](CLAUDE.md).

---

## 목차

1. [진행 현황](#1-진행-현황)
2. [기술 스택 · 실행](#2-기술-스택--실행)
3. [시스템 개요](#3-시스템-개요)
4. [진단 문서](#4-진단-문서)
5. [안전망 - 특성화 테스트](#5-안전망---특성화-테스트)
6. [리팩토링 이력](#6-리팩토링-이력)
7. [정적 분석 - SonarQube](#7-정적-분석---sonarqube)
8. [작업 방식 · 규칙](#8-작업-방식--규칙)
9. [다음 단계](#9-다음-단계)

---

## 1. 진행 현황

```
[진단 01~08] ──▶ [안전망 20개] ──▶ [리팩토링 ①이름 ②enum ③라벨 위임] ──▶ (다음) B02 생성자 주입 · B04 processApproval 분해
     완료              완료                  완료 (결재 도메인)
```

| 구분 | 상태 | 내용 |
|---|:---:|---|
| 진단 | ✅ | 구조 · 중복 · 긴 메서드 · 매직넘버 · 강결합 · 심각도 2종 · 백로그 ([docs/진단](docs/진단)) |
| 스멜 목록 | ✅ | 16종 S01~S16 ([docs/smells.md](docs/smells.md)) |
| 특성화 테스트 | ✅ | `ApprovalService` **20개 green** · `processApproval` 분기 커버리지 **87.5%** |
| 이름 개선 (S09) | ✅ | `ApprovalService` 약어 전부 제거 · Notice/Schedule 일부 |
| status enum (S03) | ✅ | 결재 status → `ApprovalStatus` + `AttributeConverter` (DB 정수 그대로) |
| 라벨 위임 (S11) | ✅ | `statusLabel` 5분기 if → `ApprovalStatus.label()` |
| 생성자 주입 (S04) | ⬜ | 다음 단계 (B02) |
| `processApproval` 분해 (S02) | ⬜ | B04 |

---

## 2. 기술 스택 · 실행

| 항목 | 내용 |
|---|---|
| 언어 · 프레임워크 | Java 21 · Spring Boot 3.2 · Spring Data JPA |
| 테스트 | JUnit 5 · AssertJ · `@DataJpaTest` · JaCoCo |
| DB | MariaDB(localhost:3306, db=portal) - 없으면 **H2 인메모리로 자동 전환** |
| 빌드 | Maven Wrapper (`mvnw`) - Maven 설치 불필요 |
| 정적 분석 | SonarQube Community Build 26.6 (로컬, 도커 없이) |
| 화면 | `src/main/resources/static/index.html` 한 장 (Tailwind + 순수 JS) |

### 사전 준비
- JDK 21 이상
- (선택) MariaDB - 계정 `portal` / `portal1234`. 없어도 H2 로 자동 실행된다
- Claude Code CLI

### 실행

```bat
.\mvnw.cmd spring-boot:run
```

- `DataSourceConfig` 가 시작할 때 DB 를 고른다. 로그에 `>> DB = MariaDB` 또는 `>> MariaDB 연결 불가 → H2 인메모리로 실행` 이 찍힌다.
- 화면: http://localhost:8080 (비밀번호 없이 사용자만 선택해 로그인)
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:portal`, 사용자 `sa`)

### 테스트

```bat
.\mvnw.cmd test
```

- H2 인메모리로 돌며, JaCoCo 리포트가 `target/site/jacoco/` 에 생긴다.

### API 빠른 확인

```bash
# 1) 결재 생성 (기안자=1 김사원, 결재자=2 박팀장, 지출 120만원)
#    Windows Git Bash 는 한글 JSON 이 깨져 400 이 나므로 예시 값은 영문
curl -X POST localhost:8080/api/approvals -H "Content-Type: application/json" \
  -d '{"title":"Laptop","content":"for dev","type":1,"priority":2,"drafterId":1,"approverId":2,"amount":1200000}'

# 2) 상신 (action=1) - 지출 100만원 이상이라 priority 가 3(높음)으로 오른다
curl -X POST localhost:8080/api/approvals/1/process -H "Content-Type: application/json" \
  -d '{"userId":1,"action":1}'

# 3) 승인 (action=2, 박팀장)
curl -X POST localhost:8080/api/approvals/1/process -H "Content-Type: application/json" \
  -d '{"userId":2,"action":2}'

# 4) 목록
curl localhost:8080/api/approvals/drafts/1
curl localhost:8080/api/approvals/inbox/2
```

### 초기 사용자 (`DataSeeder`)

| id | 이름 | role | 의미 |
|:---:|---|:---:|---|
| 1 | 김사원 | 1 | 사원 |
| 2 | 박팀장 | 2 | 팀장 (승인 권한) |
| 3 | 이임원 | 3 | 임원 |
| 4 | 최사원 | 1 | 사원 |

---

## 3. 시스템 개요

### 패키지 구조

```
kr.hull.portal
├── approval/   결재 - Approval · ApprovalStatus(enum) · ApprovalStatusConverter · Repository · Service · Controller
├── notice/     공지 - Entity · Repository · Service (컨트롤러 없음, API 미노출)
├── schedule/   일정 - Entity · Repository · Service (컨트롤러 없음, API 미노출)
├── user/       사용자 - Entity · Repository · Controller
├── common/     SmtpMailSender · FileAuditLogger (실습용 콘솔 출력)
├── DataSourceConfig  MariaDB ↔ H2 자동 선택
└── DataSeeder        초기 사용자 4명
```

### REST API (보존 대상 - 리팩토링 중 변경 금지)

| 메서드 | URL | 설명 |
|---|---|---|
| POST | `/api/approvals` | 결재 생성 (status 0 임시저장) |
| POST | `/api/approvals/{id}/process` | 처리 - `action` 1 상신 · 2 승인 · 3 반려 · 9 취소 |
| GET | `/api/approvals/drafts/{userId}` | 내가 기안한 결재 |
| GET | `/api/approvals/inbox/{userId}` | 내가 결재자인 결재 |
| GET | `/api/users` | 사용자 목록 |

### 결재 상태 전이 (`processApproval`)

```
                 1 상신 (임시저장일 때)            2 승인 (결재자 본인 · 팀장 이상)
  [0 임시저장] ─────────────────────▶ [1 상신] ──────────────────────────▶ [2 승인]
       │                                │  └──── 3 반려 (결재자 본인 · 팀장 이상) ──▶ [3 반려]
       │ 9 취소 (기안자 본인)            │ 9 취소 (기안자 본인)
       └────────────────────────────────┴───────────────────────────────▶ [9 취소]
```

| action | 전제조건 | 결과 | 부수 효과 |
|:---:|---|:---:|---|
| 1 상신 | status 0 (요청자 확인 없음) | 1 | 지출(type 1) & 100만원 이상이면 priority 3 · 결재자에게 메일 · 감사 로그 |
| 2 승인 | status 1 · 요청자 = 결재자 · role ≥ 2 | 2 | 기안자에게 메일 · 감사 로그 |
| 3 반려 | status 1 · 요청자 = 결재자 · role ≥ 2 | 3 | rejectReason 저장 · 기안자에게 메일 · 감사 로그 |
| 9 취소 | status 0 또는 1 · 요청자 = 기안자 | 9 | 감사 로그 (메일 없음) |

- 조건이 하나라도 맞지 않으면 **예외 없이 조용히 무시**한다 (응답 200, 변화 없음). 레거시의 설계 결함이지만 **리팩토링 단계에서는 보존**하고, 리팩토링 완료 후 재설계 단계에서 다룬다.
- 승인(2) · 반려(3) · 취소(9)는 종료 상태 - 어떤 action 도 효과가 없다.

---

## 4. 진단 문서

리팩토링 전에 레거시를 관점별로 진단했다. 각 문서 맨 위에 "무엇을 · 왜 보는지"를 적어 두었다.

| 문서 | 내용 | 핵심 결과 |
|---|---|---|
| [01 구조 파악](docs/진단/01-구조-파악.md) | 패키지 · 외부 계약(API·DB값) · 서비스별 책임 | `ApprovalService` 가 7가지 책임을 떠안은 God Class |
| [02 중복 코드](docs/진단/02-중복-코드.md) | 세 서비스의 중복 9유형 (D1~D9) | 감사 로그 6곳 · 메일 본문 4곳 · 합칠 때 사라지기 쉬운 미묘한 차이 표시 |
| [03 긴 메서드](docs/진단/03-긴-메서드.md) | 메서드 길이 · 중첩 깊이 측정 | `processApproval` 86줄 · 중첩 5 |
| [04 매직넘버](docs/진단/04-매직넘버.md) | 숫자 리터럴 의미 추정 · enum 이름 제안 | 도메인 코드값 8종 · action ≠ status · `1000000` 두 의미 |
| [05 강결합](docs/진단/05-강결합.md) | 직접 `new` 의존성 | 메일·로거 직접 생성 5곳 → DIP 목표 구조 |
| [06 심각도 표](docs/진단/06-심각도-표.md) | 영향 × 범위 → 처리 순서 | 치명 4건: S01 · S02 · S03 · S04 |
| [07 심각도 점수](docs/진단/07-심각도-점수.md) | 영향 × 수정난이도 → ★ | ★5 S02 · 빠른 성과 S04 |
| [08 리팩토링 백로그](docs/진단/08-리팩토링-백로그.md) | 우선순위 순 작업 B01~B10 · 완료 조건(DoD) | B01 테스트 → B02 DIP → B03 enum → B04 분해 … |
| [smells.md](docs/smells.md) | 스멜 16종 종합 체크리스트 | ID(S01~S16)로 커밋·주석에서 참조 |

### 스멜 심각도 요약

| 등급 | 스멜 |
|---|---|
| 🔴 치명 | S01 테스트 부재 · S02 God Class/Long Method · S03 매직넘버 · S04 강결합 |
| 🟠 높음 | S05 중복 코드 · S06 빈약한 도메인 · S07 DTO 부재 |
| 🟡 중간 | S08 깊은 중첩 · S09 나쁜 이름 · S10 긴 파라미터 목록 |
| 🟢 낮음 | S11 표현 로직 · S13 주석 스멜 · S14 `LocalDateTime.now()` · S15 매직 문자열 · S16 계층 건너뛰기 |
| ⚪ 보존 | S12 조용한 실패 · `null` 반환 - 동작 변경이므로 재설계 단계로 |

---

## 5. 안전망 - 특성화 테스트

리팩토링 전에 **현재 동작을 옳고 그름 판단 없이 그대로 고정**했다. 결과 상세는 [docs/특성화 테스트 결과.md](docs/특성화%20테스트%20결과.md).

| 테스트 클래스 | 개수 | 고정한 동작 |
|---|:---:|---|
| `ApprovalServiceTest` | 6 | 상신→승인 · 반려 · 취소 · 권한 없는 승인 무시 · 없는 id 무시 · 재승인 무시 |
| `ApprovalServiceBranchCharacterizationTest` | 14 | 금액 기준 우선순위 상향 · 당사자/권한 확인 · 상태 가드 · 정의 밖 action · 없는 사용자 · `statusLabel` 라벨 · `amountGrade` 경계값 |

| 지표 (`processApproval`) | 1차 (6개) | 2차 (20개) |
|---|:---:|:---:|
| 분기 커버리지 | 29/48 (60.4%) | **42/48 (87.5%)** |
| 라인 커버리지 | 58/60 | **59/59 (100%)** |

- **작성 규칙:** `@DataJpaTest` + `@Import(ApprovalService.class)` 로 서비스를 주입받는다 (생성자가 바뀌어도 테스트 본문 무수정). 데이터는 `service.create` 로 만든다. 기대값은 **정수 리터럴(0/1/2/3/9)**. 필드·변수는 영어, 메서드명만 한글 시나리오.
- **불변 규칙:** 한번 만든 테스트의 본문(호출·기대값)은 리팩토링 중 **절대 수정하지 않는다.** `ApprovalServiceTest.java` 는 생성 커밋(`a93dd56`) 이후 한 글자도 바뀌지 않았다.
- **레거시 검증:** 2차 14개는 리팩토링 전 코드(`53a532a`)에서도 같은 기대값으로 green - "레거시의 원래 동작"을 고정했다는 증거다.
- **남은 빈틈:** 결재자·기안자가 null 인 분기 6개, 메일·감사 로그 문구 (B02 이후 가짜 구현 주입으로 추가 예정).

---

## 6. 리팩토링 이력

각 단계는 **기법 하나 → 테스트 green → 커밋** 단위로 진행했다. 바꾼 코드에는 `[리팩토링] 레거시 X → Y (기법 · 이유)` 주석을 남겼다.

| 커밋 | 단계 | 기법 | 내용 |
|---|---|---|---|
| `a93dd56` | 안전망 | Characterization Test | 특성화 테스트 6개 |
| `ea258b9` | ① 이름 | Rename Variable (S09) | `d`→`approval`, `u`→`user`, `s`→`status`, 임시변수 `proc` 제거 |
| `352c127` | ① 이름 | Rename Variable/Field (S09) | `repo`·`mail`·`audit`·`tmp`·`a`·`act` 등 `ApprovalService` 약어 전부 |
| `20b9d34` | ② enum | Replace Magic Number with Enum (S03) | `ApprovalStatus`(DRAFT=0 · SUBMITTED=1 · APPROVED=2 · REJECTED=3 · CANCELED=9) + `AttributeConverter` |
| `8d7a0c6` | ③ 라벨 | 조건문 → enum 소유 (S11) | `statusLabel` 의 임시변수 + 5분기 if → `ApprovalStatus.label()` 위임 |
| `34dd3b9` | 안전망 보강 | Characterization Test | 분기 특성화 테스트 14개 · 커버리지 87.5% |

### enum 전환에서 지킨 계약

```java
// Approval.java - DB 엔 정수 그대로, 서비스는 enum 으로
@Convert(converter = ApprovalStatusConverter.class)
@Column(nullable = false)
private ApprovalStatus status = ApprovalStatus.DRAFT;

public int getStatus() { return status.getCode(); }          // [보존] 응답 JSON "status": 0/1/2/3/9
public ApprovalStatus approvalStatus() { return status; }    // 서비스용 - get 접두가 없어 JSON 에 안 나온다
```

| 확인 항목 | 결과 |
|---|---|
| DB 저장값 | `STATUS = 0, 1, 2, 3, 9` · 컬럼 `INTEGER NOT NULL` (레거시 `int` 와 동일) |
| API 응답 JSON | 필드·순서 동일, `"status"` 는 정수 - 화면(index.html) 무수정 동작 |
| 특성화 테스트 | 본문 무수정 20개 green |
| `@Enumerated` | 사용하지 않음 - `ORDINAL` 은 CANCELED(9)를 4로, `STRING` 은 문자로 저장해 DB 값이 깨진다 |

> **알려진 차이 1가지:** 정의 밖 code(예: `setStatus(5)`)는 레거시에선 그대로 저장됐지만 이제 `IllegalArgumentException` 이 난다. API·서비스는 0/1/2/3/9 만 쓰므로 실제 흐름에선 생기지 않는 경우다.

---

## 7. 정적 분석 - SonarQube

로컬 SonarQube Community Build 26.6 (Java 21, 도커 없이 · 한글팩 적용)로 **리팩토링 전 베이스라인**을 측정했다.

```bat
.\mvnw.cmd sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<토큰> ^
  -Dsonar.projectKey=legacy-portal -Dsonar.scanner.skipJreProvisioning=true
```

| 지표 | 베이스라인 |
|---|---|
| 코드 줄 수 | 653 |
| 버그 | 0 (신뢰성 A) |
| 취약점 | 3 (보안 E) - DB 비밀번호 하드코딩 (`DataSourceConfig`) |
| 코드 스멜 | 37 (기술 부채 290분) |
| 인지 복잡도 | 100 - `processApproval` 하나가 **52** (허용 15) |
| 중복 | 1.3% |
| 커버리지 | 0% (테스트 작성 전) |

| 규칙 | 건수 | 대응 스멜 |
|---|:---:|---|
| S8688 `now()` 시간대 미지정 | 13 | S14 |
| S1066 합칠 수 있는 중첩 if | 9 | S02 · S08 |
| S106 `System.out` | 9 | S04 주변 |
| S6437 · S2068 비밀번호 하드코딩 | 3 | S15 |
| S3776 · S6541 인지 복잡도 · Brain Method | 2 | **S02** |
| S6880 if 사슬 → switch | 2 | S11 |
| S107 파라미터 8개 · S1192 리터럴 중복 | 2 | S10 · S05 |

> SonarQube 는 매직넘버(S03)·직접 `new`(S04)·빈약한 도메인(S06)·DTO 부재(S07) 같은 **설계 수준 스멜은 잡지 못했다** - 진단 문서가 따로 필요한 이유.
> 첫 분석에서 스캐너의 JRE 자동 다운로드가 실패해 `-Dsonar.scanner.skipJreProvisioning=true` 로 로컬 Java 21 을 쓰게 했다.

---

## 8. 작업 방식 · 규칙

### 리팩토링 불변 규칙 ([CLAUDE.md](CLAUDE.md))

| 보존 대상 (절대 안 바꿈) | 개선 대상 (내부 구조) |
|---|---|
| API URL·HTTP 메서드 (`/api/approvals/{id}/process` 하나 유지) | 매직넘버 → 도메인 이름 enum |
| public 메서드 시그니처 (`processApproval(id, userId, action, reason)`) | 직접 `new` → 생성자 주입 (DIP) |
| 요청/응답 JSON 구조·숫자 코드 | Entity 노출 → 요청/응답 DTO(record) |
| DB 저장값 (enum 이어도 정수, `AttributeConverter`) | 긴 메서드 분리 · 중복 제거 · Rich Domain |
| 조용한 무시 · `null` 반환 같은 레거시 동작 | 약어 → 의미 있는 이름 |

- 특성화 테스트 본문(호출·기대값)은 **절대 수정 금지**. 예외는 패키지 이동에 따른 import·`@Import` 같은 컴파일용 기계적 수정뿐이며, 그 diff 는 따로 확인받는다.
- 이름: 약어·한 글자 금지 (지역변수·파라미터 포함), boolean 은 `is`/`has`, enum 은 `ApprovalStatus` 처럼 도메인을 드러내는 이름.

### 커밋 · 푸시

- 커밋 메시지에 스멜/백로그 ID 표기 - 예: `refactor: 결재 status int → ApprovalStatus enum + AttributeConverter [S03]`
- 이 PC 의 `.git/hooks/post-commit` 훅이 **커밋 직후 `origin` 으로 자동 push** 한다 (훅은 레포에 포함되지 않음).
- 셸 스크립트는 `.gitattributes` 로 LF 고정 (훅이 CRLF 로 깨지지 않게).

### todo-guard (작업 누락 방지)

- 지시를 받으면 훅이 [`TODO.md`](TODO.md) 에 `#번호` 로 적고, 끝나지 않은 항목이 남으면 턴 종료를 막는다.
- 훅 껍데기는 `.claude/hooks/`, 등록은 `.claude/settings.json`. 사용법은 [`투두가드설명서.md`](투두가드설명서.md).

---

## 9. 다음 단계

[08 리팩토링 백로그](docs/진단/08-리팩토링-백로그.md) 순서:

| 순서 | 항목 | 내용 |
|:---:|---|---|
| 1 | **B02** | `MailSender`·`AuditLogger` 인터페이스 추출 + `@Component` + 생성자 주입 (S04) |
| 2 | B03 나머지 | action · type · priority · `UserRole` · 공지/일정 상태 enum, 금액 기준 상수 |
| 3 | **B04** | `processApproval` 분해 - submit/approve/reject/cancel 추출 · 가드 절 (public 시그니처 유지) |
| 4 | B05 · B06 | 감사 로그·메일 공통화 · 규칙을 도메인으로 (Rich Domain) |
| 5 | B07 ~ B10 | 공지·일정 중첩 · DTO(record) · 매개변수 객체 · 남은 정리 |
| 이후 | 재설계 | S12 조용한 실패 → 예외/결과 타입 (동작 변경 - 리팩토링 완료 후 별도 단계) |
