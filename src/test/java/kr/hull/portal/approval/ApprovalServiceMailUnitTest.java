package kr.hull.portal.approval;

import kr.hull.portal.approval.domain.Approval;
import kr.hull.portal.approval.repository.ApprovalRepository;
import kr.hull.portal.approval.service.ApprovalService;
import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.common.FakeMailSender;
import kr.hull.portal.common.FakeMailSender.SendCall;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * [단위 테스트] ApprovalService 의 메일 발송(send 호출)을 실제 발송 없이 검증한다.
 *
 * - 특성화 테스트(안전망)와 별개의 새 파일이다. 기존 테스트는 건드리지 않는다.
 * - 감사 로그는 mock 이라 포맷 문자열이 아니라 "어떤 행위로 write 가 불렸는지"를 검증한다 (포맷 자체는 ConsoleAuditLogger 가 담당).
 * - 서비스는 스프링 빈이 아니라 생성자로 직접 만든다: 저장소는 @DataJpaTest 의 실제 JPA 저장소,
 *   메일은 FakeMailSender(가짜), 감사 로그는 Mockito mock - 생성자 주입(B02) 덕분에 가능한 조립이다.
 * - 결재 데이터는 setter 로 만들지 않고 service.create · processApproval 로만 준비한다 (실제 흐름 그대로).
 *
 * action: 1=상신 2=승인 3=반려 9=취소 / role: 1=사원 2=팀장
 */
@DataJpaTest
class ApprovalServiceMailUnitTest {

    private static final int EXPENSE_TYPE = 1;
    private static final int NORMAL_PRIORITY = 2;
    private static final long AMOUNT = 500_000L;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private FakeMailSender fakeMailSender;
    private AuditLogger auditLogger;
    private ApprovalService approvalService;

    private User drafter;
    private User teamLeader;
    private User staffApprover;

    @BeforeEach
    void setUp() {
        fakeMailSender = new FakeMailSender();
        auditLogger = mock(AuditLogger.class);
        approvalService = new ApprovalService(approvalRepository, userRepository, fakeMailSender, auditLogger);

        drafter = userRepository.save(new User("drafter", "drafter@hull.kr", 1, "dev1"));
        teamLeader = userRepository.save(new User("teamLeader", "leader@hull.kr", 2, "dev1"));
        staffApprover = userRepository.save(new User("staffApprover", "staff@hull.kr", 1, "dev2"));
    }

    private Approval createDraft(User approver) {
        return approvalService.create("laptop", "for dev", EXPENSE_TYPE, NORMAL_PRIORITY,
                drafter.getId(), approver.getId(), AMOUNT, false);
    }

    private Approval createSubmitted(User approver) {
        Approval approval = createDraft(approver);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, "");
        return approval;
    }

    @Test
    void 생성만_하면_메일을_보내지_않는다() {
        createDraft(teamLeader);

        assertThat(fakeMailSender.sendCalls()).isEmpty();
        verify(auditLogger, times(1)).write(eq("APPROVAL CREATE"), any(), eq(drafter.getId()), eq("type=1"));
    }

    @Test
    void 상신하면_결재자에게_결재요청_메일을_한_번_보낸다() {
        createSubmitted(teamLeader);

        assertThat(fakeMailSender.sendCalls()).hasSize(1);
        SendCall sendCall = fakeMailSender.lastSendCall();
        assertThat(sendCall.to()).isEqualTo("leader@hull.kr");
        assertThat(sendCall.subject()).isEqualTo("[결재요청] laptop");
        verify(auditLogger).write(eq("APPROVAL SUBMIT"), any(), eq(drafter.getId()));
    }

    @Test
    void 승인하면_기안자에게_결재승인_메일을_보낸다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThat(fakeMailSender.sendCalls()).hasSize(2);
        SendCall sendCall = fakeMailSender.lastSendCall();
        assertThat(sendCall.to()).isEqualTo("drafter@hull.kr");
        assertThat(sendCall.subject()).isEqualTo("[결재승인] laptop");
        assertThat(sendCall.body()).isEqualTo("안녕하세요 drafter님,\n결재가 승인되었습니다.\n제목: laptop");
    }

    @Test
    void 반려하면_기안자에게_사유가_담긴_결재반려_메일을_보낸다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "budget exceeded");

        assertThat(fakeMailSender.sendCalls()).hasSize(2);
        SendCall sendCall = fakeMailSender.lastSendCall();
        assertThat(sendCall.to()).isEqualTo("drafter@hull.kr");
        assertThat(sendCall.subject()).isEqualTo("[결재반려] laptop");
        assertThat(sendCall.body()).isEqualTo("안녕하세요 drafter님,\n결재가 반려되었습니다.\n제목: laptop\n사유: budget exceeded");
    }

    @Test
    void 취소는_메일을_보내지_않고_감사로그만_남긴다() {
        Approval approval = createDraft(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, "");

        assertThat(fakeMailSender.sendCalls()).isEmpty();
        verify(auditLogger).write("APPROVAL CANCEL", approval.getId(), drafter.getId());
    }

    @Test
    void 권한없는_사원_결재자의_승인은_메일도_승인_감사로그도_없다() {
        Approval approval = createSubmitted(staffApprover);

        approvalService.processApproval(approval.getId(), staffApprover.getId(), 2, "");

        assertThat(fakeMailSender.sendCalls()).hasSize(1);   // 상신 때 보낸 1통뿐
        verify(auditLogger, never()).write(eq("APPROVAL APPROVE"), any(), any());
        verify(auditLogger).write(eq("APPROVAL CREATE"), any(), any(), any());   // CREATE · SUBMIT 만
        verify(auditLogger).write(eq("APPROVAL SUBMIT"), any(), any());
        verifyNoMoreInteractions(auditLogger);
    }
}
