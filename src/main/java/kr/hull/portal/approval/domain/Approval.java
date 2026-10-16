package kr.hull.portal.approval.domain;

import jakarta.persistence.*;
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
 *  - 서비스는 approvalStatus()/changeStatus(enum) 로 enum 비교. get 접두가 없어 JSON 에 필드가 추가되지 않는다
 *  - type·priority 는 아직 int (다음 단계)
 *
 * [리팩토링] 패키지 이동: kr.hull.portal.approval → kr.hull.portal.approval.domain (도메인 계층 - 엔티티·값 타입·DB 매핑 변환기)
 *  - 계층별 하위 패키지로 나눠 클래스의 역할이 패키지 이름에서 드러나게 했다 (Move Class)
 *  - [보존 대상] 클래스명·public 메서드·엔드포인트·DB 저장값은 그대로. package·import 줄만 바뀌었다
 */
@Entity
public class Approval {

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

    // [리팩토링] 프로덕션 코드용 enum 접근자 - get/set 접두를 쓰지 않아 Jackson 이 JSON 속성으로 잡지 않는다
    public ApprovalStatus approvalStatus() { return status; }
    public void changeStatus(ApprovalStatus status) { this.status = status; }
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
