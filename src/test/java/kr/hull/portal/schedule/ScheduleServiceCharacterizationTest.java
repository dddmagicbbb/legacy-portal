package kr.hull.portal.schedule;

import kr.hull.portal.common.RecordingAuditLogger;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [특성화 테스트] ScheduleService 의 현재 동작 고정 - 일정 리팩토링(enum·겹침 검사 추출·중첩 평탄화) 전 안전망.
 *
 * - 옳고 그름을 판단하지 않고 "지금 이렇게 동작한다"를 기록한다. 이상해 보이는 동작도 그대로 고정한다
 *   (예: 공백 제목도 생성, 존재하지 않는 소유자 id 로도 생성, 실패는 원인 구분 없이 null, 소유자 없는 일정 확정은 NPE).
 * - 감사 로그는 가짜(RecordingAuditLogger)를 주입해 관찰한다. 일정 서비스는 메일을 쓰지 않는다.
 * - 한번 만든 본문(호출·기대값)은 리팩토링 중 수정하지 않는다. 기대값은 정수 그대로.
 *
 * status: 0=예정 1=확정 9=취소
 */
@DataJpaTest
@Import({ScheduleService.class, RecordingAuditLogger.class})
class ScheduleServiceCharacterizationTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 20, 9, 0);
    private static final LocalDateTime TEN = NINE.plusHours(1);
    private static final LocalDateTime ELEVEN = NINE.plusHours(2);
    private static final LocalDateTime NINE_THIRTY = NINE.plusMinutes(30);
    private static final long MISSING_ID = 999_999L;
    private static final String TIMESTAMP_PATTERN = "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] ";

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingAuditLogger auditLogger;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        auditLogger.clear();
        owner = userRepository.save(new User("owner", "owner@hull.kr", 1, "dev1"));
        otherUser = userRepository.save(new User("otherUser", "other@hull.kr", 2, "dev1"));
    }

    private Schedule createSchedule(Long ownerId, LocalDateTime startAt, LocalDateTime endAt) {
        return scheduleService.create("standup", ownerId, startAt, endAt, "room A");
    }

    private Schedule reload(Schedule schedule) {
        return scheduleRepository.findById(schedule.getId()).orElseThrow();
    }

    // ---- 생성: 정상 ----

    @Test
    void 일정을_생성하면_예정_상태로_입력값이_그대로_저장되고_생성_감사로그가_남는다() {
        Schedule schedule = createSchedule(owner.getId(), NINE, TEN);

        Schedule saved = reload(schedule);
        assertThat(saved.getStatus()).isEqualTo(0);
        assertThat(saved.getTitle()).isEqualTo("standup");
        assertThat(saved.getOwnerId()).isEqualTo(owner.getId());
        assertThat(saved.getStartAt()).isEqualTo(NINE);
        assertThat(saved.getEndAt()).isEqualTo(TEN);
        assertThat(saved.getLocation()).isEqualTo("room A");
        assertThat(auditLogger.writtenLines()).hasSize(1);
        assertThat(auditLogger.writtenLines().get(0))
                .matches(TIMESTAMP_PATTERN + "SCHEDULE CREATE id=" + schedule.getId() + " by=" + owner.getId());
    }

    @Test
    void 시작과_종료가_같은_시각이면_생성된다() {
        assertThat(createSchedule(owner.getId(), NINE, NINE)).isNotNull();
    }

    @Test
    void 공백_제목은_빈_문자열이_아니라서_생성된다() {
        Schedule schedule = scheduleService.create(" ", owner.getId(), NINE, TEN, "room A");

        assertThat(schedule).isNotNull();
        assertThat(reload(schedule).getTitle()).isEqualTo(" ");
    }

    @Test
    void 존재하지_않는_소유자_id로도_생성된다() {
        Schedule schedule = createSchedule(MISSING_ID, NINE, TEN);

        assertThat(schedule).isNotNull();
        assertThat(reload(schedule).getOwnerId()).isEqualTo(MISSING_ID);
    }

    // ---- 생성: 검증 실패는 원인 구분 없이 null ----

    @Test
    void 제목이_null이거나_빈_문자열이면_null을_반환하고_저장도_로그도_없다() {
        assertThat(scheduleService.create(null, owner.getId(), NINE, TEN, "room A")).isNull();
        assertThat(scheduleService.create("", owner.getId(), NINE, TEN, "room A")).isNull();

        assertThat(scheduleRepository.count()).isZero();
        assertThat(auditLogger.writtenLines()).isEmpty();
    }

    @Test
    void 시작이나_종료가_null이면_null을_반환한다() {
        assertThat(createSchedule(owner.getId(), null, TEN)).isNull();
        assertThat(createSchedule(owner.getId(), NINE, null)).isNull();

        assertThat(scheduleRepository.count()).isZero();
    }

    @Test
    void 종료가_시작보다_이르면_null을_반환한다() {
        assertThat(createSchedule(owner.getId(), TEN, NINE)).isNull();

        assertThat(scheduleRepository.count()).isZero();
    }

    // ---- 생성: 시간 겹침 ----

    @Test
    void 같은_소유자의_일정과_시간이_겹치면_null을_반환한다() {
        createSchedule(owner.getId(), NINE, TEN);

        assertThat(createSchedule(owner.getId(), NINE_THIRTY, ELEVEN)).isNull();

        assertThat(scheduleRepository.count()).isEqualTo(1);
        assertThat(auditLogger.writtenLines()).hasSize(1);
    }

    @Test
    void 기존_일정의_끝과_새_일정의_시작이_맞닿으면_겹치지_않아_생성된다() {
        createSchedule(owner.getId(), NINE, TEN);

        assertThat(createSchedule(owner.getId(), TEN, ELEVEN)).isNotNull();
    }

    @Test
    void 다른_소유자의_일정과는_시간이_겹쳐도_생성된다() {
        createSchedule(otherUser.getId(), NINE, TEN);

        assertThat(createSchedule(owner.getId(), NINE, TEN)).isNotNull();
    }

    @Test
    void 취소된_일정과는_시간이_겹쳐도_생성된다() {
        // 일정을 취소(9)하는 서비스 메서드가 없어, 저장된 일정의 상태를 엔티티 값으로 바꿔 준비한다
        Schedule canceled = createSchedule(owner.getId(), NINE, TEN);
        canceled.setStatus(9);
        scheduleRepository.save(canceled);

        assertThat(createSchedule(owner.getId(), NINE, TEN)).isNotNull();
    }

    @Test
    void 확정된_일정과_시간이_겹쳐도_null을_반환한다() {
        Schedule confirmed = createSchedule(owner.getId(), NINE, TEN);
        scheduleService.confirm(confirmed.getId(), owner.getId());

        assertThat(createSchedule(owner.getId(), NINE_THIRTY, ELEVEN)).isNull();
    }

    // ---- 확정 ----

    @Test
    void 소유자가_예정_일정을_확정하면_확정_상태가_되고_확정_감사로그가_남는다() {
        Schedule schedule = createSchedule(owner.getId(), NINE, TEN);

        scheduleService.confirm(schedule.getId(), owner.getId());

        assertThat(reload(schedule).getStatus()).isEqualTo(1);
        assertThat(auditLogger.writtenLines()).hasSize(2);
        assertThat(auditLogger.writtenLines().get(1))
                .matches(TIMESTAMP_PATTERN + "SCHEDULE CONFIRM id=" + schedule.getId() + " by=" + owner.getId());
    }

    @Test
    void 소유자가_아닌_사용자가_확정하면_조용히_무시된다() {
        Schedule schedule = createSchedule(owner.getId(), NINE, TEN);

        assertThatCode(() -> scheduleService.confirm(schedule.getId(), otherUser.getId())).doesNotThrowAnyException();

        assertThat(reload(schedule).getStatus()).isEqualTo(0);
        assertThat(auditLogger.writtenLines()).hasSize(1);
    }

    @Test
    void 이미_확정된_일정을_다시_확정하면_조용히_무시된다() {
        Schedule schedule = createSchedule(owner.getId(), NINE, TEN);
        scheduleService.confirm(schedule.getId(), owner.getId());

        scheduleService.confirm(schedule.getId(), owner.getId());

        assertThat(reload(schedule).getStatus()).isEqualTo(1);
        assertThat(auditLogger.writtenLines()).hasSize(2);   // CREATE · CONFIRM 1회
    }

    @Test
    void 없는_일정_id나_없는_사용자로_확정하면_예외_없이_무시된다() {
        Schedule schedule = createSchedule(MISSING_ID, NINE, TEN);   // 소유자 id 가 사용자 테이블에 없다

        assertThatCode(() -> scheduleService.confirm(MISSING_ID, owner.getId())).doesNotThrowAnyException();
        assertThatCode(() -> scheduleService.confirm(schedule.getId(), MISSING_ID)).doesNotThrowAnyException();

        assertThat(reload(schedule).getStatus()).isEqualTo(0);
    }

    @Test
    void 소유자가_없는_일정을_확정하면_NullPointerException이_난다() {
        Schedule schedule = createSchedule(null, NINE, TEN);

        assertThatThrownBy(() -> scheduleService.confirm(schedule.getId(), owner.getId()))
                .isInstanceOf(NullPointerException.class);
    }
}
