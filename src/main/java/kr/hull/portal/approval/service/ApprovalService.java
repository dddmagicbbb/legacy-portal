package kr.hull.portal.approval.service;

import kr.hull.portal.approval.domain.AmountGrade;
import kr.hull.portal.approval.domain.Approval;
import kr.hull.portal.approval.domain.ApprovalStatus;
import kr.hull.portal.approval.repository.ApprovalRepository;
import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.common.MailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
 *  7. Primitive Obsession  : 모든 분기를 int 비교로 처리한다.
 *  8. Long Parameter List  : create() 파라미터 8개.
 *  9. Poor Naming          : d, u, proc, tmp, flag1 같은 약어.
 * 10. Comment Smell        : 나쁜 이름을 주석으로 변명한다.
 * 11. No Tests             : 테스트가 단 한 개도 없다(안전망 부재).
 * =================================================================================
 *
 * [리팩토링] 레거시 status 매직넘버 0/1/2/3/9 → ApprovalStatus enum 비교로 (Replace Magic Number with Enum · S03)
 *  - processApproval·create·statusLabel 의 status 숫자 비교·대입을 enum 상수로 바꿨다. 분기 구조·조건·동작은 그대로
 *  - [보존 대상] DB 는 Converter 로 정수 그대로, API 응답·특성화 테스트는 getStatus() 정수 그대로
 *  - action(1/2/3/9)·type·role·priority 숫자는 아직 그대로 (다음 단계)
 *
 * [리팩토링] statusLabel 의 임시변수와 5분기 if 제거 → ApprovalStatus.label() 위임 (라벨을 enum 이 소유 · S11)
 *
 * [리팩토링] processApproval 분해 - guard clause 조기 반환 + submit/approve/reject/cancel 추출 (Extract Method · S02 · B04)
 *
 * [리팩토링] 메일·감사 로거 직접 new → MailSender · AuditLogger 인터페이스 생성자 주입 (DIP · S04 · B02)
 *
 * [리팩토링] amountGrade 의 금액 등급 규칙 → AmountGrade enum 으로 Move Method (Feature Envy 해소 · B06). statusLabel 은 앞서 label() 위임 완료
 *
 * [리팩토링] 레거시 약어 지역변수·파라미터 → 의미 있는 이름으로 (Rename Variable · 스멜9 Poor Naming 해소)
 *  - d → approval, u → user, s → status (create · processApproval · statusLabel · amountGrade)
 *  - processApproval 의 임시변수 proc 제거 → 파라미터 action 을 직접 비교
 *  - tmp → label (statusLabel), a → amount (amountGrade), act·id → auditAction·approvalId (writeAudit), line → auditLine (create)
 *  - 필드 repo·userRepo·mail·audit → approvalRepository·userRepository·mailSender·auditLogger (생성자 파라미터 이름도 함께)
 *  - 이름만 바꿨다. 로직·분기·매직넘버·public 시그니처·동작은 그대로 (ApprovalServiceTest 6개 green 으로 확인)
 *
 * [리팩토링] 패키지 이동: kr.hull.portal.approval → kr.hull.portal.approval.service (애플리케이션(서비스) 계층)
 *  - 계층별 하위 패키지로 나눠 클래스의 역할이 패키지 이름에서 드러나게 했다 (Move Class)
 *  - [보존 대상] 클래스명·public 메서드·엔드포인트·DB 저장값은 그대로. package·import 줄만 바뀌었다
 *
 * [리팩토링] 감사 로그 포맷 직접 조립(create + private writeAudit) → AuditLogger.write(action, id, userId[, detail]) 위임 (E1·E2 · B05)
 */
@Service
public class ApprovalService {

    // [리팩토링·이름] 레거시 repo · userRepo → approvalRepository · userRepository (무엇의 저장소인지 드러냄)
    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;

    // [리팩토링·DIP] 레거시: private final SmtpMailSender mail = new SmtpMailSender(); (구체 클래스 직접 new · 스멜5)
    //              → 여기서: 인터페이스 MailSender · AuditLogger 타입 필드 + 생성자 주입 (B02)
    //                테스트·운영에서 구현을 갈아끼울 수 있고, 의존성이 생성자에 드러난다
    private final MailSender mailSender;
    private final AuditLogger auditLogger;

    public ApprovalService(ApprovalRepository approvalRepository, UserRepository userRepository,
                           MailSender mailSender, AuditLogger auditLogger) {
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.auditLogger = auditLogger;
    }

    // [스멜8] 파라미터 8개. [스멜9] 이름이 모호하다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();   // [리팩토링·이름] 레거시 d → approval (약어 제거, 의미 있는 이름)
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
        approval.changeStatus(ApprovalStatus.DRAFT);    // [리팩토링·enum] 레거시 setStatus(0) → DRAFT (DB 엔 0 그대로)
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        // [리팩토링·중복 제거] 레거시: 시각 포맷 + "[시각] APPROVAL CREATE id=… by=… type=…" 를 여기서 직접 조립 (E1·E2)
        //                    → 포맷은 AuditLogger 구현(ConsoleAuditLogger) 한 곳에. 생성 로그만 있던 " type=…" 꼬리는 detail 로 보존
        auditLogger.write("APPROVAL CREATE", approval.getId(), drafterId, "type=" + approval.getType());
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     *
     * [리팩토링] 레거시: 86줄 · 중첩 5단계의 if-지옥 한 덩어리 (스멜2 Long Method · S02 · 백로그 B04)
     *           → 여기서: ① 조회 실패를 guard clause 로 조기 반환 ② action 별 분기를 submit/approve/reject/cancel
     *             private 메서드로 추출(Extract Method) ③ 각 메서드 안의 중첩 조건도 guard clause 로 평탄화 (중첩 ≤ 1)
     * [보존 대상] public 시그니처 processApproval(Long id, Long userId, int action, String reason) 그대로.
     *            조건이 맞지 않으면 예외 없이 조용히 무시(return) - 상태·메일·로그 모두 변화 없음 (레거시 동작).
     *            정의 밖 action(예: 5)은 어느 분기에도 걸리지 않아 아무 일도 하지 않는다.
     *            메일·감사 로그의 문구·순서(상태 변경 → updatedAt → save → 메일 → 로그)도 그대로.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소 (action 숫자의 enum 화는 다음 단계)
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        // [리팩토링·guard clause] 조회 실패 시 조기 반환 - 레거시와 같이 예외 없이 조용히 종료
        Approval approval = approvalRepository.findById(id).orElse(null);
        if (approval == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        // [리팩토링·Extract Method] 분기 본문은 각 private 메서드로 - 여기는 action 별 위임만 한다
        if (action == 1) {
            submit(approval, userId);
        } else if (action == 2) {
            approve(approval, user, userId);
        } else if (action == 3) {
            reject(approval, user, userId, reason);
        } else if (action == 9) {
            cancel(approval, userId);
        }
    }

    /** 상신(action 1): 임시저장일 때만. 요청자 확인은 하지 않는다 (레거시 동작 보존). */
    private void submit(Approval approval, Long userId) {
        // [리팩토링·guard clause] 레거시 if (s == 0) { … } 중첩 → 조건 불충족 시 조기 반환
        if (approval.approvalStatus() != ApprovalStatus.DRAFT) {
            return;
        }
        // [스멜6] 금액 기준 우선순위 자동 상향 — 도메인 규칙이 서비스에 박혀 있다 (B06 에서 도메인으로 이동)
        if (approval.getType() == 1 && approval.getAmount() >= 1000000) {   // type 1=지출 && 100만원↑
            approval.setPriority(3);   // 3 = 높음
        }
        approval.changeStatus(ApprovalStatus.SUBMITTED);
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);
        // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙 (B05 에서 공통화)
        User approver = userRepository.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            // [리팩토링·Extract Method] 레거시 본문 문자열 조립 3줄 → submittedBody (문구는 그대로)
            mailSender.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), submittedBody(approver, approval));
        }
        auditLogger.write("APPROVAL SUBMIT", approval.getId(), userId);
    }

    /** 승인(action 2): 상신 상태 · 요청자 = 결재자 · 팀장 이상일 때만. */
    private void approve(Approval approval, User user, Long userId) {
        // [리팩토링·guard clause] 레거시 3단 중첩 if(상태 → 결재자 → 권한) → 각각 조기 반환
        if (approval.approvalStatus() != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
            return;
        }
        if (user.getRole() < 2) {   // 레거시 role >= 2(팀장 이상) 의 반대 - role 1=사원·2=팀장·3=임원
            return;
        }
        approval.changeStatus(ApprovalStatus.APPROVED);
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);
        // [스멜4] 또 복붙된 메일 발송
        User drafter = userRepository.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            // [리팩토링·Extract Method] 레거시 본문 문자열 조립 2줄 → approvedBody (문구는 그대로)
            mailSender.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), approvedBody(drafter, approval));
        }
        auditLogger.write("APPROVAL APPROVE", approval.getId(), userId);
    }

    /** 반려(action 3): 승인과 같은 조건 + 반려 사유 저장. */
    private void reject(Approval approval, User user, Long userId, String reason) {
        // [리팩토링·guard clause] 레거시 3단 중첩 if(상태 → 결재자 → 권한) → 각각 조기 반환
        //                        (승인과 같은 판정의 중복은 B05 에서 공통 가드로 추출)
        if (approval.approvalStatus() != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
            return;
        }
        if (user.getRole() < 2) {
            return;
        }
        approval.changeStatus(ApprovalStatus.REJECTED);
        approval.setRejectReason(reason);
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);
        User drafter = userRepository.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            // [리팩토링·Extract Method] 레거시 본문 문자열 조립 3줄 → rejectedBody (문구는 그대로)
            mailSender.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), rejectedBody(drafter, approval, reason));
        }
        auditLogger.write("APPROVAL REJECT", approval.getId(), userId);
    }

    // [리팩토링·Extract Method] submit·approve·reject 안의 긴 메일 본문 조립을 이름 있는 private 메서드로 추출.
    //   - 메서드 이름이 "무슨 메일인지"를 말해 주고, submit·approve·reject 는 처리 흐름만 읽히게 된다
    //   - [보존 대상] 본문 문구·줄바꿈은 레거시와 한 글자도 같다 (서비스 private 유지 - 공통 템플릿화는 B05)

    /** 결재 요청 메일 본문 - 결재자에게, 기안자 id 포함. */
    private String submittedBody(User approver, Approval approval) {
        return "안녕하세요 " + approver.getName() + "님,\n"
                + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                + "\n기안자ID: " + approval.getDrafterId();
    }

    /** 승인 메일 본문 - 기안자에게. */
    private String approvedBody(User drafter, Approval approval) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
    }

    /** 반려 메일 본문 - 기안자에게, 반려 사유 포함. */
    private String rejectedBody(User drafter, Approval approval, String reason) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                + "\n사유: " + reason;
    }

    /** 취소(action 9): 임시저장 또는 상신 상태 · 요청자 = 기안자일 때만. 메일은 보내지 않는다. */
    private void cancel(Approval approval, Long userId) {
        // [리팩토링·guard clause] 레거시 2단 중첩 if(상태 → 기안자) → 각각 조기 반환
        ApprovalStatus status = approval.approvalStatus();
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getDrafterId() == null || !approval.getDrafterId().equals(userId)) {
            return;
        }
        approval.changeStatus(ApprovalStatus.CANCELED);
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);
        auditLogger.write("APPROVAL CANCEL", approval.getId(), userId);
    }

    // [리팩토링·중복 제거] 레거시 private writeAudit(act, id, userId) 삭제 - 시각 포맷·문자열 조립이 AuditLogger 구현으로 옮겨 가
    //                    서비스에 남길 일이 없다. 호출부는 auditLogger.write(action, id, userId) 를 직접 부른다 (B05)

    // [리팩토링] 레거시: 임시변수 tmp(→label) + status 5분기 if 사슬로 라벨을 서비스가 직접 번역
    //           → 여기서: 라벨은 ApprovalStatus 가 소유하고 statusLabel 은 enum.label() 에 위임만 한다
    //             (Replace Conditional with Polymorphism 의 enum 판 · 표현 로직을 상태 타입으로 이동 · S11)
    // [보존 대상] public 시그니처 statusLabel(Approval) 와 반환 문자열은 그대로.
    //            레거시의 "알수없음" 은 상태가 없을 때(null)만 남는다 - 정의 밖 code 는 이제 Converter/fromCode 단계에서 막힌다.
    public String statusLabel(Approval approval) {
        ApprovalStatus status = approval.approvalStatus();
        return status == null ? "알수없음" : status.label();
    }

    // [리팩토링] 레거시: 서비스가 Approval 의 금액을 꺼내 if 사슬 + 매직넘버(10000000·1000000·100000)로 등급 계산
    //           (스멜6 Feature Envy · S03) → 여기서: 금액 등급 도메인 규칙을 AmountGrade enum 으로 옮기고 위임만 한다
    //           (Move Method · 백로그 B06)
    // [보존 대상] public 시그니처 amountGrade(Approval) 와 반환 문자열 "S"·"A"·"B"·"C", 경계(이상 >=)는 그대로
    public String amountGrade(Approval approval) {
        return AmountGrade.of(approval.getAmount()).name();
    }

    public List<Approval> myDrafts(Long userId) {
        return approvalRepository.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return approvalRepository.findByApproverId(userId);
    }
}
