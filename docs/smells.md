# 코드 스멜 종합 목록 (smells.md)

> **이 문서는 무엇인가** - legacy-portal 에서 진단한 코드 스멜 16종을 ID 별로 정리한 **종합 목록(체크리스트)** 이다. 각 스멜의 위치, 고칠 기법, 리팩토링 시 지킬 보존 대상과 진행 상태를 한곳에서 관리한다.
> **왜 보는가** - 진단 문서(docs/진단/01~06)는 관점별 분석이라 흩어져 있다. 리팩토링을 진행하면서 "무엇이 남았고, 무엇을 끝냈는가"를 추적하려면 스멜 단위의 단일 목록이 필요하다. 커밋 메시지·주석에서 `S03` 처럼 ID 로 참조하면 변경 이유를 바로 연결할 수 있다.

> 기준 커밋: `f55e294` · 심각도 산정 근거와 권장 순서는 [진단/06-심각도-표.md](진단/06-심각도-표.md) 참고.
> 등급: 🔴 치명 · 🟠 높음 · 🟡 중간 · 🟢 낮음 · ⚪ 보존 대상(재설계 단계) · 상태: ⬜ 미착수 · 🔄 진행 중 · ✅ 완료

---

## 요약

| ID | 등급 | 스멜 | 상태 |
|---|:---:|---|:---:|
| S01 | 🔴 | 테스트 부재 | ⬜ |
| S02 | 🔴 | God Class · Long Method | ⬜ |
| S03 | 🔴 | 매직넘버 · Primitive Obsession | ⬜ |
| S04 | 🔴 | 강결합 (직접 `new`) | ⬜ |
| S05 | 🟠 | 중복 코드 | ⬜ |
| S06 | 🟠 | 빈약한 도메인 · Feature Envy | ⬜ |
| S07 | 🟠 | 요청/응답 DTO 부재 | ⬜ |
| S08 | 🟡 | 깊은 중첩 | ⬜ |
| S09 | 🟡 | 나쁜 이름 | ⬜ |
| S10 | 🟡 | 긴 파라미터 목록 | ⬜ |
| S11 | 🟢 | 표현 로직이 서비스에 | ⬜ |
| S12 | ⚪ | 조용한 실패 · `null` 반환 | 보존 |
| S13 | 🟢 | 주석 스멜 | ⬜ |
| S14 | 🟢 | 숨은 정적 의존 (`LocalDateTime.now()`) | ⬜ |
| S15 | 🟢 | 매직 문자열 · 하드코딩 설정 | ⬜ |
| S16 | 🟢 | 계층 건너뛰기 | ⬜ |

---

## 상세

### S01 🔴 테스트 부재
- **위치:** `src/test` - `application.properties` 만 있고 테스트 0개
- **영향:** 리팩토링 후 동작이 같다는 것을 증명할 수 없다
- **기법:** 특성화 테스트(Characterization Test) - 현재 동작을 옳고 그름 판단 없이 그대로 고정
- **우선 대상:** `processApproval` 의 상신·승인·반려·취소 × (성공 / 조용한 무시) 케이스, 금액 기준 우선순위 상향, `statusLabel`·`amountGrade`, 공지 게시, 일정 겹침
- **규칙:** 테스트는 정수 리터럴(0/1/2/3/9)로 호출·검증. 한번 만든 본문은 수정 금지
- **근거:** 01

### S02 🔴 God Class · Long Method
- **위치:** `approval/ApprovalService.java:75-160` `processApproval` - 86줄, 중첩 5, 책임 7가지 (분기·전이 검증·권한·도메인 규칙·저장·메일·로그)
- **기법:** 메서드 추출(submit/approve/reject/cancel), 가드 절, 책임 분리
- **보존:** public `processApproval(Long id, Long userId, int action, String reason)` 시그니처 유지, 내부는 위임만. `/api/approvals/{id}/process` 유지
- **근거:** 01, 03

### S03 🔴 매직넘버 · Primitive Obsession
- **위치:**
  - 결재 상태 0/1/2/3/9 · action 1/2/3/9 · 유형 1~4 · 우선순위 1~3 (`ApprovalService`, `Approval`, `ApprovalController`)
  - 사용자 역할 1~3, `role >= 2` (`ApprovalService.java:114·133`, `NoticeService.java:56`, `DataSeeder`)
  - 공지 상태 0/1/9 · 분류 1~3, 일정 상태 0/1/9
  - 금액 기준 `1000000`(우선순위 상향) · `10000000`/`1000000`/`100000`(금액 등급)
- **기법:** 도메인 이름 enum(`ApprovalStatus`, `ApprovalAction`, `ApprovalType`, `ApprovalPriority`, `UserRole`, `NoticeStatus`, `NoticeCategory`, `ScheduleStatus`) + `AttributeConverter`, 의미별 상수
- **보존:** DB 정수값 그대로 (`@Enumerated(STRING/ORDINAL)` 금지), API 숫자 그대로, 알 수 없는 코드의 처리(저장/무시/"알수없음") 그대로
- **주의:** action ≠ status (별도 enum), `1000000` 두 의미는 별도 상수, `role >= 2` 는 ordinal 비교 대신 권한 메서드
- **근거:** 04

### S04 🔴 강결합 (직접 `new`)
- **위치:** `ApprovalService.java:37-38`, `NoticeService.java:24-25`, `ScheduleService.java:20`
- **기법:** `MailSender`·`AuditLogger` 인터페이스 추출 + `@Component` + 생성자 주입 (DIP)
- **보존:** 메일 수신자·제목·본문, 감사 로그 문자열, 발송 조건·횟수
- **근거:** 05

### S05 🟠 중복 코드
- **위치 (9유형):** 감사 로그 6곳(D1) · 메일 본문 4곳(D2) · 직접 `new` 5곳(D3) · 조회 후 null 종료 3곳(D4) · `role >= 2` 3곳(D5) · `statusLabel` 2곳(D6) · 승인/반려 블록(D7) · 당사자 확인 4곳(D8) · 상태 변경+`updatedAt`+저장 4곳(D9)
- **기법:** 공통 모듈 추출(감사 로그·메일 템플릿), 메서드 추출, 도메인 메서드
- **보존:** 미묘한 차이 - `APPROVAL CREATE` 로그의 ` type=…` 꼬리, 상신 메일 `기안자ID`·반려 메일 `사유` 줄, 결재는 null 검사·일정은 미검사
- **근거:** 02

### S06 🟠 빈약한 도메인 · Feature Envy
- **위치:** `Approval`·`Notice`·`Schedule`·`User` - 필드 + public getter/setter 뿐. 서비스가 상태 전이, 금액 기준 우선순위 상향(`ApprovalService.java:94`), 금액 등급(`:182-188`), 당사자 판정, 일정 겹침 계산
- **기법:** 상태 전이 메서드(`submit()`·`approve()`…)와 판정 메서드(`isApprover()`·`overlaps()`…)를 엔티티로 이동 (Rich Domain)
- **보존:** 전이 결과, `updatedAt` 갱신 시점
- **근거:** 01, 02

### S07 🟠 요청/응답 DTO 부재
- **위치:** `approval/ApprovalController.java:23-45` - `Map<String, Object>` 캐스팅, `Approval` 엔티티 그대로 반환. `UserController` 도 `User` 엔티티 반환
- **기법:** 요청/응답 `record` DTO
- **보존:** JSON 필드명·구조·숫자 코드 동일, 기본값(`amount`=0, `urgent`=false, `reason`="")
- **근거:** 01, 04

### S08 🟡 깊은 중첩
- **위치:** `NoticeService.publish` (중첩 4), `ScheduleService.create` (중첩 3)
- **기법:** 가드 절, 메서드 추출 (긴급 공지 메일, 겹침 검사)
- **근거:** 03

### S09 🟡 나쁜 이름
- **위치:** `d`·`u`·`s`·`proc`·`tmp`·`a` (`ApprovalService`), `n` (`NoticeService`), `sc`·`s`·`flag1` (`ScheduleService`), `act` (`writeAudit` 파라미터)
- **기법:** 의미 있는 완전한 이름 (`approval`, `user`, `status`, `action`, `label`, `isOverlapping` …)
- **근거:** 01, 03

### S10 🟡 긴 파라미터 목록
- **위치:** `ApprovalService.create(title, content, type, priority, drafterId, approverId, amount, urgent)` - 8개
- **기법:** 내부에서 매개변수 객체 도입. **public 시그니처는 유지** (필요 시 오버로드 추가)
- **근거:** 01

### S11 🟢 표현 로직이 서비스에
- **위치:** `ApprovalService.statusLabel`·`amountGrade`, `NoticeService.statusLabel`
- **기법:** 라벨은 상태 enum, 등급은 `Approval` 또는 등급 enum 으로 이동. public 메서드는 위임으로 유지
- **보존:** 반환 문자열(`"임시저장"`, `"알수없음"`, `"S"` …) 동일
- **근거:** 02, 04

### S12 ⚪ 조용한 실패 · `null` 반환 (보존 대상)
- **위치:** `processApproval`·`publish`·`confirm` 의 조건 불충족 시 `return`, `ScheduleService.create` 의 `null` 반환
- **처리:** **리팩토링 단계에서는 그대로 보존** (특성화 테스트로 고정). 예외·결과 타입으로 바꾸는 것은 리팩토링 완료 후 **재설계 단계**
- **근거:** 01, 02, 03

### S13 🟢 주석 스멜
- **위치:** 나쁜 이름·매직넘버의 의미를 설명하는 주석 전반 (예: `// d = 결재 문서`, `// 3 = 높음`)
- **기법:** 이름과 enum 으로 의도를 드러내면 주석 불필요 - S03·S09 해결 시 함께 정리. 단 `[리팩토링]` 학습 주석은 CLAUDE.md 규칙대로 남긴다
- **근거:** 04

### S14 🟢 숨은 정적 의존
- **위치:** `LocalDateTime.now()` 13곳 (`ApprovalService` 8, `NoticeService` 3, `ScheduleService` 2)
- **기법:** (선택) `Clock` 빈 주입. 감사 로그 시각 계산은 S05 공통화 시 이동
- **근거:** 05

### S15 🟢 매직 문자열 · 하드코딩 설정
- **위치:** 날짜 포맷 `"yyyy-MM-dd HH:mm:ss"` 6곳, DB 계정 `DataSourceConfig.java:23-25`, 연결 타임아웃 `2`
- **기법:** 상수화 (날짜 포맷은 감사 로그 모듈로), 설정값 외부화(선택)
- **근거:** 02, 04

### S16 🟢 계층 건너뛰기
- **위치:** `user/UserController.java` → `UserRepository` 직접 호출
- **기법:** (선택) `UserService` 도입
- **보존:** `/api/users` 응답 동일
- **근거:** 05

---

## 진단 문서

| 문서 | 내용 |
|---|---|
| [진단/01-구조-파악.md](진단/01-구조-파악.md) | 패키지 구조, 외부 계약(API·DB값), 서비스별 책임 |
| [진단/02-중복-코드.md](진단/02-중복-코드.md) | 중복 9유형 D1~D9 |
| [진단/03-긴-메서드.md](진단/03-긴-메서드.md) | 메서드 길이·중첩 깊이 측정 |
| [진단/04-매직넘버.md](진단/04-매직넘버.md) | 숫자 리터럴 의미 추정, enum 이름 제안 |
| [진단/05-강결합.md](진단/05-강결합.md) | 직접 `new` 의존성, DIP 목표 구조 |
| [진단/06-심각도-표.md](진단/06-심각도-표.md) | 심각도 산정, 권장 리팩토링 순서 |
