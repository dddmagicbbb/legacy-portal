package kr.hull.portal.approval;

import kr.hull.portal.common.RecordingAuditLogger;
import kr.hull.portal.common.RecordingMailSender;
import kr.hull.portal.common.RecordingMailSender.SentMail;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [특성화 테스트 - 3차] 메일·감사 로그·우선순위 경계처럼 아직 고정하지 않은 "현재 동작"을 고정한다.
 *
 * - 기존 20개는 status·rejectReason·priority 만 검증했다. 이 클래스는 가짜 MailSender·AuditLogger 를 주입해
 *   메일 수신자·제목·본문과 감사 로그 문자열까지 관찰한 그대로 고정한다 (B02 생성자 주입 덕분에 가능).
 * - 감사 로그의 시각은 실행마다 달라 정규식으로 형식만 고정한다.
 * - 한번 만든 본문(호출·기대값)은 리팩토링 중 수정하지 않는다. 기대값은 정수 그대로.
 */
@DataJpaTest
@Import({ApprovalService.class, RecordingMailSender.class, RecordingAuditLogger.class})
class ApprovalSideEffectCharacterizationTest {

    private static final int EXPENSE_TYPE = 1;
    private static final int NORMAL_PRIORITY = 2;
    private static final long SMALL_AMOUNT = 500_000L;
    private static final long JUST_BELOW_RAISE_AMOUNT = 999_999L;
    private static final String TIMESTAMP_PATTERN = "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] ";

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private RecordingAuditLogger auditLogger;

    private User drafter;
    private User teamLeader;
    private User otherTeamLeader;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        auditLogger.clear();
        drafter = userRepository.save(new User("drafter", "drafter@hull.kr", 1, "dev1"));
        teamLeader = userRepository.save(new User("teamLeader", "leader@hull.kr", 2, "dev1"));
        otherTeamLeader = userRepository.save(new User("otherTeamLeader", "other@hull.kr", 2, "dev2"));
    }

    private Approval createDraft(long amount, boolean urgent) {
        return approvalService.create("laptop", "for dev", EXPENSE_TYPE, NORMAL_PRIORITY,
                drafter.getId(), teamLeader.getId(), amount, urgent);
    }

    private Approval reload(Approval approval) {
        return approvalRepository.findById(approval.getId()).orElseThrow();
    }

    // ---- 상신 ----

    @Test
    void 상신하면_결재자에게_결재요청_메일이_가고_생성과_상신_감사로그가_남는다() {
        Approval approval = createDraft(SMALL_AMOUNT, false);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        assertThat(mailSender.sentMails()).containsExactly(new SentMail(
                "leader@hull.kr",
                "[결재요청] laptop",
                "안녕하세요 teamLeader님,\n결재 요청이 도착했습니다.\n제목: laptop\n기안자ID: " + drafter.getId()));
        assertThat(auditLogger.writtenLines()).hasSize(2);
        assertThat(auditLogger.writtenLines().get(0))
                .matches(TIMESTAMP_PATTERN + "APPROVAL CREATE id=" + approval.getId() + " by=" + drafter.getId() + " type=1");
        assertThat(auditLogger.writtenLines().get(1))
                .matches(TIMESTAMP_PATTERN + "APPROVAL SUBMIT id=" + approval.getId() + " by=" + drafter.getId());
    }

    // ---- 고액 지출 우선순위 상향 (경계 아래 · 긴급 생성) ----

    @Test
    void 지출_999999원을_상신하면_우선순위가_그대로다() {
        Approval approval = createDraft(JUST_BELOW_RAISE_AMOUNT, false);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getPriority()).isEqualTo(2);
    }

    @Test
    void 긴급으로_생성하면_요청한_우선순위와_관계없이_높음이다() {
        Approval approval = createDraft(SMALL_AMOUNT, true);

        assertThat(reload(approval).getStatus()).isEqualTo(0);
        assertThat(reload(approval).getPriority()).isEqualTo(3);
    }

    // ---- 결재자 아닌 사람의 반려 ----

    @Test
    void 결재자가_아닌_팀장이_반려하면_메일도_반려_감사로그도_없다() {
        Approval approval = createDraft(SMALL_AMOUNT, false);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");
        int mailCountBefore = mailSender.sentMails().size();
        int auditCountBefore = auditLogger.writtenLines().size();

        approvalService.processApproval(approval.getId(), otherTeamLeader.getId(), 3, "not mine");

        assertThat(mailSender.sentMails()).hasSize(mailCountBefore);
        assertThat(auditLogger.writtenLines()).hasSize(auditCountBefore);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    // ---- 상신 전 취소 ----

    @Test
    void 상신_전_취소는_메일_없이_취소_감사로그만_남는다() {
        Approval approval = createDraft(SMALL_AMOUNT, false);

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, "");

        assertThat(reload(approval).getStatus()).isEqualTo(9);
        assertThat(mailSender.sentMails()).isEmpty();
        assertThat(auditLogger.writtenLines()).hasSize(2);
        assertThat(auditLogger.writtenLines().get(1))
                .matches(TIMESTAMP_PATTERN + "APPROVAL CANCEL id=" + approval.getId() + " by=" + drafter.getId());
    }
}
