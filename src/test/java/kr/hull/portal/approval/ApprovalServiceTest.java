package kr.hull.portal.approval;

import kr.hull.portal.common.FileAuditLogger;
import kr.hull.portal.common.SmtpMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * [특성화 테스트] ApprovalService.processApproval(id, userId, action, reason) 의 현재 동작 고정.
 *
 * - 리팩토링 전 안전망이다. 옳고 그름을 판단하지 않고 "레거시가 지금 이렇게 동작한다"는 사실만 고정한다.
 * - 한번 만든 본문(호출·기대값)은 리팩토링 중 절대 수정하지 않는다. 기대값은 DB 정수 그대로(0/1/2/3/9).
 * - 조건이 맞지 않으면 예외 없이 조용히 무시하는 레거시 동작(4·5·6번)도 보존 대상으로 고정한다.
 *
 * action: 1=상신 2=승인 3=반려 9=취소 / status: 0=임시저장 1=상신 2=승인 3=반려 9=취소 / role: 1=사원 2=팀장
 */
@DataJpaTest
@Import({ApprovalService.class, SmtpMailSender.class, FileAuditLogger.class})
class ApprovalServiceTest {

    private static final int EXPENSE_TYPE = 1;
    private static final int NORMAL_PRIORITY = 2;
    private static final long AMOUNT = 500_000L;
    private static final long MISSING_APPROVAL_ID = 999_999L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private User drafter;
    private User teamLeader;
    private User staffApprover;

    @BeforeEach
    void setUp() {
        drafter = userRepository.save(new User("drafter", "drafter@hull.kr", 1, "dev1"));
        teamLeader = userRepository.save(new User("teamLeader", "leader@hull.kr", 2, "dev1"));
        staffApprover = userRepository.save(new User("staffApprover", "staff@hull.kr", 1, "dev2"));
    }

    private Approval createDraft(User approver) {
        return approvalService.create("laptop", "for dev", EXPENSE_TYPE, NORMAL_PRIORITY,
                drafter.getId(), approver.getId(), AMOUNT, false);
    }

    private int statusOf(Long approvalId) {
        return approvalRepository.findById(approvalId).orElseThrow().getStatus();
    }

    @Test
    void 기안자가_상신하고_팀장_결재자가_승인하면_승인_상태가_된다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");
        assertThat(statusOf(approval.getId())).isEqualTo(1);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");
        assertThat(statusOf(approval.getId())).isEqualTo(2);
    }

    @Test
    void 상신된_결재를_팀장_결재자가_반려하면_반려_상태와_사유가_저장된다() {
        Approval approval = createDraft(teamLeader);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "budget exceeded");

        Approval rejected = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(3);
        assertThat(rejected.getRejectReason()).isEqualTo("budget exceeded");
    }

    @Test
    void 기안자가_임시저장_결재를_취소하면_취소_상태가_된다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, "");

        assertThat(statusOf(approval.getId())).isEqualTo(9);
    }

    @Test
    void 권한없는_사원_결재자가_승인하면_조용히_무시되고_상신_상태로_남는다() {
        Approval approval = createDraft(staffApprover);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        assertThatCode(() -> approvalService.processApproval(approval.getId(), staffApprover.getId(), 2, ""))
                .doesNotThrowAnyException();

        assertThat(statusOf(approval.getId())).isEqualTo(1);
    }

    @Test
    void 없는_결재_id로_처리하면_예외_없이_아무것도_바뀌지_않는다() {
        Approval approval = createDraft(teamLeader);
        long approvalCountBefore = approvalRepository.count();

        assertThatCode(() -> approvalService.processApproval(MISSING_APPROVAL_ID, teamLeader.getId(), 2, ""))
                .doesNotThrowAnyException();

        assertThat(approvalRepository.count()).isEqualTo(approvalCountBefore);
        assertThat(statusOf(approval.getId())).isEqualTo(0);
    }

    @Test
    void 이미_승인된_결재를_다시_승인하면_조용히_무시되고_승인_상태로_남는다() {
        Approval approval = createDraft(teamLeader);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThatCode(() -> approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, ""))
                .doesNotThrowAnyException();

        assertThat(statusOf(approval.getId())).isEqualTo(2);
    }
}
