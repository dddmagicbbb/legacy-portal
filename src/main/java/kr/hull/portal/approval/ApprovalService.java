package kr.hull.portal.approval;

import kr.hull.portal.common.FileAuditLogger;
import kr.hull.portal.common.SmtpMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * [리팩토링] 레거시 약어 지역변수·파라미터 → 의미 있는 이름으로 (Rename Variable · 스멜9 Poor Naming 해소)
 *  - d → approval, u → user, s → status (create · processApproval · statusLabel · amountGrade)
 *  - processApproval 의 임시변수 proc 제거 → 파라미터 action 을 직접 비교
 *  - tmp → label (statusLabel), a → amount (amountGrade), act·id → auditAction·approvalId (writeAudit), line → auditLine (create)
 *  - 필드 repo·userRepo·mail·audit → approvalRepository·userRepository·mailSender·auditLogger (생성자 파라미터 이름도 함께)
 *  - 이름만 바꿨다. 로직·분기·매직넘버·public 시그니처·동작은 그대로 (ApprovalServiceTest 6개 green 으로 확인)
 */
@Service
public class ApprovalService {

    // [리팩토링·이름] 레거시 repo · userRepo → approvalRepository · userRepository (무엇의 저장소인지 드러냄)
    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다.
    // [리팩토링·이름] 레거시 mail · audit → mailSender · auditLogger (역할이 드러나는 이름. 직접 new 는 B02 에서 생성자 주입으로)
    private final SmtpMailSender mailSender = new SmtpMailSender();
    private final FileAuditLogger auditLogger = new FileAuditLogger();

    public ApprovalService(ApprovalRepository approvalRepository, UserRepository userRepository) {
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
    }

    // [스멜8] 파라미터 8개. [스멜9] 이름이 모호하다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();   // [리팩토링·이름] 레거시 d → approval (약어 제거, 의미 있는 이름)
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
        approval.setStatus(0);                          // status(상태): 0 임시저장·1 상신·2 승인·3 반려·9 취소  [스멜3: 0=임시저장, DRAFT 대신 숫자 0]
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String auditLine = "[" + now + "] APPROVAL CREATE id=" + approval.getId()
                + " by=" + drafterId + " type=" + approval.getType();
        auditLogger.write(auditLine);
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     * [스멜2] 이 메서드 하나가 모든 일을 한다. [스멜1][스멜6] 규칙 계산을 서비스가 떠안는다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        Approval approval = approvalRepository.findById(id).orElse(null);   // [리팩토링·이름] 레거시 d → approval
        if (approval == null) {
            // [스멜] 예외 대신 조용히 리턴 — 호출자는 실패를 알 수 없다.
            return;
        }
        User user = userRepository.findById(userId).orElse(null);   // [리팩토링·이름] 레거시 u → user
        if (user == null) {
            return;
        }

        int status = approval.getStatus();     // [리팩토링·이름] 레거시 s → status. 값: 0 임시저장·1 상신·2 승인·3 반려·9 취소 (매직넘버는 B03 에서 enum 화)
        // [리팩토링·이름] 레거시 int proc = action; 임시변수 제거 → 파라미터 action 을 직접 사용 (같은 값을 다른 약어로 복사할 이유 없음)

        // [스멜2][스멜3] 거대한 if-지옥. 상태 전이 규칙이 숫자 비교로 흩어져 있다.
        if (action == 1) {            // action==1 → 상신 (숫자 1을 외워야 의미를 앎)
            // 상신: 임시저장(0)일 때만 가능
            if (status == 0) {           // status==0 → 임시저장 상태일 때만
                // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
                if (approval.getType() == 1 && approval.getAmount() >= 1000000) {   // type 1=지출·2=휴가·3=구매·4=기타 → type==1(지출) && 100만원↑
                    approval.setPriority(3);   // 3 = 높음
                }
                approval.setStatus(1);   // 1 = 상신 (SUBMITTED)
                approval.setUpdatedAt(LocalDateTime.now());
                approvalRepository.save(approval);
                // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙.
                User approver = userRepository.findById(approval.getApproverId()).orElse(null);
                if (approver != null) {
                    String body = "안녕하세요 " + approver.getName() + "님,\n"
                            + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                            + "\n기안자ID: " + approval.getDrafterId();
                    mailSender.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
                }
                writeAudit("APPROVAL SUBMIT", approval.getId(), userId);
            }
        } else if (action == 2) {     // action==2 → 승인
            // 승인: 상신(1) 상태 + 본인이 결재자 + 권한(role>=2) 일 때만
            if (status == 1) {           // status==1 → 상신 상태일 때만 승인 가능
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (user.getRole() >= 2) {   // role 1=사원·2=팀장·3=임원 (role>=2 승인권한)  [스멜3: 숫자로 권한 판정]
                        approval.setStatus(2);        // 2 = 승인 (APPROVED)
                        approval.setUpdatedAt(LocalDateTime.now());
                        approvalRepository.save(approval);
                        // [스멜4] 또 복붙된 메일 발송
                        User drafter = userRepository.findById(approval.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
                            mailSender.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
                        }
                        writeAudit("APPROVAL APPROVE", approval.getId(), userId);
                    }
                }
            }
        } else if (action == 3) {     // action==3 → 반려
            // 반려
            if (status == 1) {           // status==1 → 상신 상태만 반려 가능
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (user.getRole() >= 2) {   // role>=2 → 팀장 이상 (위 승인 분기와 똑같은 판정 복붙)
                        approval.setStatus(3);          // 3 = 반려 (REJECTED)
                        approval.setRejectReason(reason);
                        approval.setUpdatedAt(LocalDateTime.now());
                        approvalRepository.save(approval);
                        User drafter = userRepository.findById(approval.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                                    + "\n사유: " + reason;
                            mailSender.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), body);
                        }
                        writeAudit("APPROVAL REJECT", approval.getId(), userId);
                    }
                }
            }
        } else if (action == 9) {     // action==9 → 취소 (왜 9? 4~8 은 비워둔 규칙 없는 번호)
            // 취소: 기안자 본인 + 아직 승인 전(0 또는 1)
            if (status == 0 || status == 1) {     // status==0(임시저장) 또는 status==1(상신)일 때만
                if (approval.getDrafterId() != null && approval.getDrafterId().equals(userId)) {
                    approval.setStatus(9);   // 9 = 취소 (CANCELED)
                    approval.setUpdatedAt(LocalDateTime.now());
                    approvalRepository.save(approval);
                    writeAudit("APPROVAL CANCEL", approval.getId(), userId);
                }
            }
        }
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거).
    // [리팩토링·이름] 레거시 파라미터 act · id → auditAction · approvalId (감사 로그 문자열 형식은 그대로)
    private void writeAudit(String auditAction, Long approvalId, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        auditLogger.write("[" + now + "] " + auditAction + " id=" + approvalId + " by=" + userId);
    }

    // [스멜1][스멜10] 화면 표시용 문자열까지 서비스가 만든다. 주석으로 매직넘버를 변명한다.
    public String statusLabel(Approval approval) {
        int status = approval.getStatus();   // [리팩토링·이름] 레거시 s → status. 값: 0 임시저장·1 상신·2 승인·3 반려·9 취소
        String label;          // [리팩토링·이름] 레거시 tmp → label (반환할 상태 표시 문자열)
        if (status == 0) label = "임시저장";       // status 0~9 를 라벨로 번역 — 이 표가 곳곳에 중복
        else if (status == 1) label = "상신";
        else if (status == 2) label = "승인";
        else if (status == 3) label = "반려";
        else if (status == 9) label = "취소";
        else label = "알수없음";              // enum 이면 컴파일러가 막아줄 분기
        return label;
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산.
    public String amountGrade(Approval approval) {
        long amount = approval.getAmount();   // [리팩토링·이름] 레거시 a → amount (금액, 원)
        if (amount >= 10000000) return "S";   // [스멜3] 1000만원=S — 기준 숫자의 의미가 코드에 없음
        else if (amount >= 1000000) return "A";   // 100만원=A
        else if (amount >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return approvalRepository.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return approvalRepository.findByApproverId(userId);
    }
}
