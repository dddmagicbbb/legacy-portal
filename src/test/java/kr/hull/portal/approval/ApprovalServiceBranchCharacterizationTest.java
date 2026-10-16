package kr.hull.portal.approval;

import kr.hull.portal.approval.domain.Approval;
import kr.hull.portal.approval.repository.ApprovalRepository;
import kr.hull.portal.approval.service.ApprovalService;
import kr.hull.portal.common.ConsoleAuditLogger;
import kr.hull.portal.common.ConsoleMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [특성화 테스트 - 추가분] ApprovalServiceTest 6개가 지나가지 않은 분기를 고정한다.
 *
 * - 근거: docs/특성화 테스트 결과.md 의 안 덮인 분기 U01~U19 중 위험도 상·중 (권장 테스트 1~12)
 *   + 테스트가 없던 public 메서드 statusLabel · amountGrade.
 * - 기존 ApprovalServiceTest 의 본문은 건드리지 않고 별도 클래스로 추가했다.
 * - 리팩토링 전 레거시 코드(커밋 53a532a)에서도 같은 기대값으로 green 임을 확인한 "현재 동작"이다.
 * - 한번 만든 본문(호출·기대값)은 리팩토링 중 수정하지 않는다. 기대값은 정수 그대로(0/1/2/3/9).
 *
 * action: 1=상신 2=승인 3=반려 9=취소 / status: 0=임시저장 1=상신 2=승인 3=반려 9=취소
 * type: 1=지출 3=구매 / priority: 2=보통 3=높음 / role: 1=사원 2=팀장
 */
@DataJpaTest
@Import({ApprovalService.class, ConsoleMailSender.class, ConsoleAuditLogger.class})
class ApprovalServiceBranchCharacterizationTest {

    private static final int EXPENSE_TYPE = 1;
    private static final int PURCHASE_TYPE = 3;
    private static final int NORMAL_PRIORITY = 2;
    private static final long SMALL_AMOUNT = 500_000L;
    private static final long PRIORITY_RAISE_THRESHOLD_AMOUNT = 1_000_000L;
    private static final long LARGE_AMOUNT = 5_000_000L;
    private static final long MISSING_USER_ID = 999_999L;
    private static final int UNDEFINED_ACTION = 5;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private User drafter;
    private User teamLeader;
    private User otherTeamLeader;
    private User staffApprover;

    @BeforeEach
    void setUp() {
        drafter = userRepository.save(new User("drafter", "drafter@hull.kr", 1, "dev1"));
        teamLeader = userRepository.save(new User("teamLeader", "leader@hull.kr", 2, "dev1"));
        otherTeamLeader = userRepository.save(new User("otherTeamLeader", "other@hull.kr", 2, "dev2"));
        staffApprover = userRepository.save(new User("staffApprover", "staff@hull.kr", 1, "dev2"));
    }

    private Approval createDraft(User approver, int type, long amount) {
        return approvalService.create("laptop", "for dev", type, NORMAL_PRIORITY,
                drafter.getId(), approver.getId(), amount, false);
    }

    private Approval createDraft(User approver) {
        return createDraft(approver, EXPENSE_TYPE, SMALL_AMOUNT);
    }

    private Approval createSubmitted(User approver) {
        Approval approval = createDraft(approver);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");
        return approval;
    }

    private Approval reload(Approval approval) {
        return approvalRepository.findById(approval.getId()).orElseThrow();
    }

    // ---- 금액 규칙 (U03 · U04) ----

    @Test
    void 지출_100만원_이상을_상신하면_우선순위가_높음으로_오른다() {
        Approval approval = createDraft(teamLeader, EXPENSE_TYPE, PRIORITY_RAISE_THRESHOLD_AMOUNT);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getPriority()).isEqualTo(3);
    }

    @Test
    void 지출이_아닌_결재는_금액이_커도_우선순위가_그대로다() {
        Approval approval = createDraft(teamLeader, PURCHASE_TYPE, LARGE_AMOUNT);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getPriority()).isEqualTo(2);
    }

    // ---- 취소 (U15 · U16 · U17 · U19) ----

    @Test
    void 상신된_결재를_기안자가_취소하면_취소_상태가_된다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, "");

        assertThat(reload(approval).getStatus()).isEqualTo(9);
    }

    @Test
    void 이미_취소된_결재를_다시_취소하면_조용히_무시된다() {
        Approval approval = createDraft(teamLeader);
        approvalService.processApproval(approval.getId(), drafter.getId(), 9, "");

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, "");

        assertThat(reload(approval).getStatus()).isEqualTo(9);
    }

    @Test
    void 기안자가_아닌_사용자가_취소하면_조용히_무시된다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 9, "");

        assertThat(reload(approval).getStatus()).isEqualTo(0);
    }

    // ---- 당사자 · 권한 (U07 · U11 · U12) ----

    @Test
    void 결재자가_아닌_팀장이_승인하면_조용히_무시된다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), otherTeamLeader.getId(), 2, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    @Test
    void 결재자가_아닌_팀장이_반려하면_조용히_무시된다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), otherTeamLeader.getId(), 3, "not mine");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    @Test
    void 권한없는_사원_결재자가_반려하면_조용히_무시된다() {
        Approval approval = createSubmitted(staffApprover);

        approvalService.processApproval(approval.getId(), staffApprover.getId(), 3, "no authority");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    // ---- action · 상태 가드 · 존재 확인 (U14 · U09 · U02 · U01) ----

    @Test
    void 정의되지_않은_action은_조용히_무시된다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), UNDEFINED_ACTION, "");

        assertThat(reload(approval).getStatus()).isEqualTo(0);
    }

    @Test
    void 임시저장_결재를_반려하면_조용히_무시된다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "too early");

        assertThat(reload(approval).getStatus()).isEqualTo(0);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    @Test
    void 이미_상신된_결재를_다시_상신하면_조용히_무시된다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    @Test
    void 없는_사용자가_처리하면_조용히_무시된다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), MISSING_USER_ID, 1, "");

        assertThat(reload(approval).getStatus()).isEqualTo(0);
    }

    // ---- 테스트가 없던 public 메서드 ----

    @Test
    void 상태별_라벨은_임시저장_상신_승인_반려_취소다() {
        Approval draft = createDraft(teamLeader);
        Approval approved = createSubmitted(teamLeader);
        approvalService.processApproval(approved.getId(), teamLeader.getId(), 2, "");
        Approval rejected = createSubmitted(teamLeader);
        approvalService.processApproval(rejected.getId(), teamLeader.getId(), 3, "reason");
        Approval submitted = createSubmitted(teamLeader);
        Approval canceled = createDraft(teamLeader);
        approvalService.processApproval(canceled.getId(), drafter.getId(), 9, "");

        assertThat(approvalService.statusLabel(reload(draft))).isEqualTo("임시저장");
        assertThat(approvalService.statusLabel(reload(submitted))).isEqualTo("상신");
        assertThat(approvalService.statusLabel(reload(approved))).isEqualTo("승인");
        assertThat(approvalService.statusLabel(reload(rejected))).isEqualTo("반려");
        assertThat(approvalService.statusLabel(reload(canceled))).isEqualTo("취소");
    }

    @Test
    void 금액_등급은_10만_100만_1000만원_경계로_C_B_A_S다() {
        assertThat(approvalService.amountGrade(createDraft(teamLeader, EXPENSE_TYPE, 99_999L))).isEqualTo("C");
        assertThat(approvalService.amountGrade(createDraft(teamLeader, EXPENSE_TYPE, 100_000L))).isEqualTo("B");
        assertThat(approvalService.amountGrade(createDraft(teamLeader, EXPENSE_TYPE, 999_999L))).isEqualTo("B");
        assertThat(approvalService.amountGrade(createDraft(teamLeader, EXPENSE_TYPE, 1_000_000L))).isEqualTo("A");
        assertThat(approvalService.amountGrade(createDraft(teamLeader, EXPENSE_TYPE, 9_999_999L))).isEqualTo("A");
        assertThat(approvalService.amountGrade(createDraft(teamLeader, EXPENSE_TYPE, 10_000_000L))).isEqualTo("S");
    }
}
