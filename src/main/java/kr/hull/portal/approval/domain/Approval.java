package kr.hull.portal.approval.domain;

import jakarta.persistence.*;
import kr.hull.portal.user.User;

import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 빈약한 도메인 모델(Anemic Domain Model) — 데이터만 있고 행위가 없다.
 * [스멜] 원시 타입 집착(Primitive Obsession) — status, type, priority 가 모두 int.
 *        status: 0=임시저장, 1=상신, 2=승인, 3=반려, 9=취소  (의미가 코드 곳곳에 흩어짐)
 *        type:   1=지출, 2=휴가, 3=구매, 4=기타
 *        priority: 1=낮음, 2=보통, 3=높음
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter. 누구나 상태를 마음대로 바꿀 수 있다.
 *
 * [리팩토링] 레거시 int status → 여기서 ApprovalStatus enum 필드로 (Replace Magic Number with Enum · S03)
 *  - DB: @Convert(ApprovalStatusConverter) 로 기존 정수 code 그대로 저장 (컬럼 not null 도 레거시 int 와 동일하게 유지)
 *  - [보존 대상] getStatus()/setStatus(int) 는 정수 계약 그대로 - 응답 JSON 의 status 숫자와 특성화 테스트가 이 계약에 의존
 *  - type·priority 는 아직 int (다음 단계)
 *
 * [리팩토링] 패키지 이동: kr.hull.portal.approval → kr.hull.portal.approval.domain (도메인 계층 - 엔티티·값 타입·DB 매핑 변환기)
 *  - 계층별 하위 패키지로 나눠 클래스의 역할이 패키지 이름에서 드러나게 했다 (Move Class)
 *  - [보존 대상] 클래스명·public 메서드·엔드포인트·DB 저장값은 그대로. package·import 줄만 바뀌었다
 *
 * [리팩토링] 빈약한 도메인 → 상태 전이를 스스로 책임지는 엔티티 (Tell, Don't Ask · Move Method · S06 · 백로그 B06)
 *  - 레거시: ApprovalService 가 status·approverId·role 을 꺼내(ask) 판단하고 setStatus·setUpdatedAt 을 직접 호출했다
 *  - 여기서: 서비스는 approval.submit() / approve(user) / reject(user, reason) / cancel(userId) 로 "해라"고 말만 한다(tell).
 *    전이 가능 여부 판단 · 상태 변경 · updatedAt 갱신 · 상신 시 우선순위 상향까지 엔티티 안에서 한 번에 처리한다
 *  - 생성도 정적 팩토리 draft(...) 가 맡는다 - 시작 상태 DRAFT · 긴급 우선순위 · 생성 시각을 엔티티가 정한다
 *  - [보존 대상] 전이 규칙·조건은 레거시와 같다. 조건이 맞지 않으면 예외 없이 false 를 돌려주고 아무것도 바꾸지 않는다
 *    (레거시의 "조용한 무시" - 서비스는 false 면 저장·메일·로그 없이 return)
 *  - 서비스 전용이던 changeStatus(enum) 는 지웠다 - 상태는 이제 도메인 행위로만 바뀐다
 */
@Entity
public class Approval {

    private static final int EXPENSE_TYPE = 1;                       // type 1 = 지출 (enum 화는 다음 단계)
    private static final int HIGH_PRIORITY = 3;                      // priority 3 = 높음
    private static final long PRIORITY_RAISE_MIN_AMOUNT = 1_000_000L; // 지출 우선순위 상향 기준 - AmountGrade.A 하한과 값만 같은 별개 규칙
    private static final int APPROVAL_AUTHORITY_MIN_ROLE = 2;        // role 2 = 팀장 이상 (UserRole enum 화는 다음 단계)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    private int type;       // 1=지출 2=휴가 3=구매 4=기타 (의미를 주석으로만 설명 → enum 후보)
    // [리팩토링] int → ApprovalStatus. 기본값 DRAFT = 레거시 int 기본값 0(임시저장)과 같은 의미
    @Convert(converter = ApprovalStatusConverter.class)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.DRAFT;
    private int priority;   // 1=낮음 2=보통 3=높음
    private Long drafterId;     // 기안자
    private Long approverId;    // 결재자
    private String rejectReason;
    private long amount;        // 지출/구매 금액
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ================================================================================================
    // 생성 (정적 팩토리)
    // ================================================================================================

    /**
     * 새 결재 문서(임시저장)를 만든다.
     * [리팩토링] 레거시 ApprovalService.create 의 new Approval() + setter 10줄 → 엔티티가 시작 상태를 정한다
     * [보존 대상] status = DRAFT(0), 긴급이면 요청 우선순위와 관계없이 3(높음), createdAt·updatedAt = 생성 시각
     */
    public static Approval draft(String title, String content, int type, int priority,
                                 Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.title = title;
        approval.content = content;
        approval.type = type;
        approval.priority = urgent ? HIGH_PRIORITY : priority;
        approval.status = ApprovalStatus.DRAFT;
        approval.drafterId = drafterId;
        approval.approverId = approverId;
        approval.amount = amount;
        approval.createdAt = LocalDateTime.now();
        approval.updatedAt = LocalDateTime.now();
        return approval;
    }

    // ================================================================================================
    // 상태 전이 (도메인 행위) - 성공하면 true, 조건이 맞지 않으면 아무것도 바꾸지 않고 false
    // ================================================================================================

    /**
     * 상신: 임시저장일 때만. 요청자 확인은 하지 않는다 (레거시 동작 보존).
     * 지출이면서 100만원 이상이면 우선순위를 높음(3)으로 올린다.
     */
    public boolean submit() {
        if (status != ApprovalStatus.DRAFT) {
            return false;
        }
        if (type == EXPENSE_TYPE && amount >= PRIORITY_RAISE_MIN_AMOUNT) {
            priority = HIGH_PRIORITY;
        }
        changeStatusTo(ApprovalStatus.SUBMITTED);
        return true;
    }

    /** 승인: 상신 상태 · 요청자가 이 결재의 결재자 · 팀장 이상일 때만. */
    public boolean approve(User approver) {
        if (!canBeDecidedBy(approver)) {
            return false;
        }
        changeStatusTo(ApprovalStatus.APPROVED);
        return true;
    }

    /** 반려: 승인과 같은 조건 + 반려 사유 저장. */
    public boolean reject(User approver, String reason) {
        if (!canBeDecidedBy(approver)) {
            return false;
        }
        rejectReason = reason;
        changeStatusTo(ApprovalStatus.REJECTED);
        return true;
    }

    /** 취소: 임시저장 또는 상신 상태 · 요청자가 기안자 본인일 때만. */
    public boolean cancel(Long requesterId) {
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (!isDrafter(requesterId)) {
            return false;
        }
        changeStatusTo(ApprovalStatus.CANCELED);
        return true;
    }

    // [리팩토링·중복 제거] 레거시 approve·reject 에 똑같이 복붙돼 있던 가드 3줄 (중복 E5) → 한 메서드로
    private boolean canBeDecidedBy(User approver) {
        return status == ApprovalStatus.SUBMITTED
                && isApprover(approver.getId())
                && approver.getRole() >= APPROVAL_AUTHORITY_MIN_ROLE;
    }

    private boolean isApprover(Long userId) {
        return approverId != null && approverId.equals(userId);
    }

    private boolean isDrafter(Long userId) {
        return drafterId != null && drafterId.equals(userId);
    }

    // [리팩토링] 레거시: 상태를 바꿀 때마다 서비스가 setUpdatedAt(now) 를 따로 불러야 했다(4곳) → 전이와 시각 갱신을 한 곳에서
    private void changeStatusTo(ApprovalStatus nextStatus) {
        status = nextStatus;
        updatedAt = LocalDateTime.now();
    }

    // ================================================================================================
    // 접근자 - [보존 대상] 레거시 public getter/setter 는 계약이라 그대로 둔다 (setter 축소는 재설계 때)
    // ================================================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    // [보존 대상] 정수 계약 유지 - JSON 응답 "status": 0/1/2/3/9, 특성화 테스트의 getStatus() 정수 비교
    public int getStatus() { return status.getCode(); }
    public void setStatus(int status) { this.status = ApprovalStatus.fromCode(status); }

    // [리팩토링] enum 상태 조회 - get 접두를 쓰지 않아 Jackson 이 JSON 속성으로 잡지 않는다
    public ApprovalStatus approvalStatus() { return status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Long getDrafterId() { return drafterId; }
    public void setDrafterId(Long drafterId) { this.drafterId = drafterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
