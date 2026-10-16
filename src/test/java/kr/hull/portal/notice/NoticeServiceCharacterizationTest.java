package kr.hull.portal.notice;

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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * [특성화 테스트] NoticeService 의 현재 동작 고정 - 공지 리팩토링(enum·중복 제거·중첩 평탄화) 전 안전망.
 *
 * - 옳고 그름을 판단하지 않고 "지금 이렇게 동작한다"를 기록한다. 이상해 보이는 동작도 그대로 고정한다
 *   (예: 작성자가 아니어도 팀장이면 게시, 긴급 공지 메일은 게시자·작성자를 포함한 전 직원에게).
 * - 메일·감사 로그는 가짜(RecordingMailSender·RecordingAuditLogger)를 주입해 관찰한다. 감사 로그의 시각은 정규식으로 형식만.
 * - 한번 만든 본문(호출·기대값)은 리팩토링 중 수정하지 않는다. 기대값은 정수 그대로.
 *
 * status: 0=임시 1=게시 9=내림 / category: 1=일반 2=긴급 3=인사 / role: 1=사원 2=팀장 3=임원
 */
@DataJpaTest
@Import({NoticeService.class, RecordingMailSender.class, RecordingAuditLogger.class})
class NoticeServiceCharacterizationTest {

    private static final int GENERAL_CATEGORY = 1;
    private static final int URGENT_CATEGORY = 2;
    private static final int PERSONNEL_CATEGORY = 3;
    private static final long MISSING_ID = 999_999L;
    private static final String TIMESTAMP_PATTERN = "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] ";

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private RecordingAuditLogger auditLogger;

    private User staffWriter;
    private User teamLeader;
    private User executive;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        auditLogger.clear();
        staffWriter = userRepository.save(new User("staffWriter", "staff@hull.kr", 1, "dev1"));
        teamLeader = userRepository.save(new User("teamLeader", "leader@hull.kr", 2, "dev1"));
        executive = userRepository.save(new User("executive", "exec@hull.kr", 3, "board"));
    }

    private Notice createNotice(int category) {
        return noticeService.create("office move", "moving on friday", category, staffWriter.getId(), false);
    }

    private Notice reload(Notice notice) {
        return noticeRepository.findById(notice.getId()).orElseThrow();
    }

    // ---- 생성 ----

    @Test
    void 공지를_생성하면_임시_상태로_입력값이_그대로_저장되고_생성_감사로그가_남는다() {
        Notice notice = noticeService.create("office move", "moving on friday", PERSONNEL_CATEGORY, staffWriter.getId(), true);

        Notice saved = reload(notice);
        assertThat(saved.getStatus()).isEqualTo(0);
        assertThat(saved.getTitle()).isEqualTo("office move");
        assertThat(saved.getContent()).isEqualTo("moving on friday");
        assertThat(saved.getCategory()).isEqualTo(3);
        assertThat(saved.getWriterId()).isEqualTo(staffWriter.getId());
        assertThat(saved.isPinned()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(mailSender.sentMails()).isEmpty();
        assertThat(auditLogger.writtenLines()).hasSize(1);
        assertThat(auditLogger.writtenLines().get(0))
                .matches(TIMESTAMP_PATTERN + "NOTICE CREATE id=" + notice.getId() + " by=" + staffWriter.getId());
    }

    // ---- 게시: 권한 ----

    @Test
    void 팀장이_일반_공지를_게시하면_게시_상태가_되고_메일은_없다() {
        Notice notice = createNotice(GENERAL_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reload(notice).getStatus()).isEqualTo(1);
        assertThat(mailSender.sentMails()).isEmpty();
        assertThat(auditLogger.writtenLines()).hasSize(2);
        assertThat(auditLogger.writtenLines().get(1))
                .matches(TIMESTAMP_PATTERN + "NOTICE PUBLISH id=" + notice.getId() + " by=" + teamLeader.getId());
    }

    @Test
    void 임원도_게시할_수_있다() {
        Notice notice = createNotice(GENERAL_CATEGORY);

        noticeService.publish(notice.getId(), executive.getId());

        assertThat(reload(notice).getStatus()).isEqualTo(1);
    }

    @Test
    void 권한없는_사원이_게시하면_조용히_무시된다() {
        Notice notice = createNotice(URGENT_CATEGORY);

        assertThatCode(() -> noticeService.publish(notice.getId(), staffWriter.getId())).doesNotThrowAnyException();

        assertThat(reload(notice).getStatus()).isEqualTo(0);
        assertThat(mailSender.sentMails()).isEmpty();
        assertThat(auditLogger.writtenLines()).hasSize(1);   // CREATE 만
    }

    @Test
    void 작성자가_아니어도_팀장이면_남의_공지를_게시할_수_있다() {
        Notice notice = createNotice(GENERAL_CATEGORY);   // 작성자는 사원 staffWriter

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reload(notice).getStatus()).isEqualTo(1);
    }

    // ---- 게시: 긴급 공지 메일 ----

    @Test
    void 긴급_공지를_게시하면_게시자와_작성자를_포함한_전_직원에게_메일이_간다() {
        Notice notice = createNotice(URGENT_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reload(notice).getStatus()).isEqualTo(1);
        assertThat(mailSender.sentMails()).hasSize((int) userRepository.count());
        assertThat(mailSender.sentMails()).extracting(SentMail::to)
                .containsExactlyInAnyOrder("staff@hull.kr", "leader@hull.kr", "exec@hull.kr");
        assertThat(mailSender.sentMails()).contains(new SentMail(
                "staff@hull.kr",
                "[긴급공지] office move",
                "안녕하세요 staffWriter님,\n긴급 공지가 게시되었습니다.\n제목: office move"));
    }

    @Test
    void 인사_공지는_게시해도_메일이_없다() {
        Notice notice = createNotice(PERSONNEL_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reload(notice).getStatus()).isEqualTo(1);
        assertThat(mailSender.sentMails()).isEmpty();
    }

    // ---- 게시: 상태 가드 · 존재 확인 ----

    @Test
    void 이미_게시된_긴급_공지를_다시_게시하면_메일도_로그도_추가되지_않는다() {
        Notice notice = createNotice(URGENT_CATEGORY);
        noticeService.publish(notice.getId(), teamLeader.getId());
        int mailCountAfterFirstPublish = mailSender.sentMails().size();

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reload(notice).getStatus()).isEqualTo(1);
        assertThat(mailSender.sentMails()).hasSize(mailCountAfterFirstPublish);
        assertThat(auditLogger.writtenLines()).hasSize(2);   // CREATE · PUBLISH 1회
    }

    @Test
    void 없는_공지_id나_없는_사용자로_게시하면_예외_없이_아무것도_바뀌지_않는다() {
        Notice notice = createNotice(URGENT_CATEGORY);

        assertThatCode(() -> noticeService.publish(MISSING_ID, teamLeader.getId())).doesNotThrowAnyException();
        assertThatCode(() -> noticeService.publish(notice.getId(), MISSING_ID)).doesNotThrowAnyException();

        assertThat(reload(notice).getStatus()).isEqualTo(0);
        assertThat(mailSender.sentMails()).isEmpty();
    }

    // ---- 상태 라벨 ----

    @Test
    void 상태_라벨은_0_임시_1_게시_9_내림_그_외는_알수없음이다() {
        // statusLabel 은 엔티티를 받는 public 메서드다. 내림(9)·정의 밖 값은 서비스로 만들 방법이 없어 엔티티 값으로 넣는다
        Notice notice = createNotice(GENERAL_CATEGORY);

        assertThat(noticeService.statusLabel(notice)).isEqualTo("임시");
        notice.setStatus(1);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("게시");
        notice.setStatus(9);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("내림");
        notice.setStatus(5);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("알수없음");
    }
}
