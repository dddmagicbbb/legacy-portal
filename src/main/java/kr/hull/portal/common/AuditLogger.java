package kr.hull.portal.common;

/**
 * 감사 로그 기록 추상화.
 *
 * [리팩토링] 레거시: 서비스가 구체 클래스 FileAuditLogger 를 필드에서 직접 new (스멜5 강결합 · S04)
 *           → 여기서: 서비스는 이 인터페이스에만 의존하고, 구현은 스프링이 생성자로 주입한다 (DIP · 백로그 B02)
 *  - 메서드 시그니처는 레거시 FileAuditLogger.write(line) 그대로다.
 *  - 출력 대상(파일·DB·콘솔)을 바꿔도 서비스 코드는 고치지 않는다.
 *
 * [리팩토링] 감사 로그 포맷 한 곳으로 (중복 제거 E1 · 백로그 B05)
 *  - 레거시: 결재·공지·일정 서비스 6곳이 각자 시각 포맷 + "[시각] 행위 id=… by=…" 문자열을 조립했다
 *  - 여기서: 서비스는 write(action, id, userId) 로 "무엇을·누가" 만 넘기고, 포맷은 구현체(ConsoleAuditLogger)가 한 번만 만든다
 */
public interface AuditLogger {

    /** 이미 완성된 한 줄을 기록한다 (출력 대상만 다룬다). */
    void write(String line);

    /** 행위 로그 - 포맷은 구현체가 만든다. 예: "[2026-10-16 13:30:00] NOTICE PUBLISH id=3 by=2" */
    default void write(String action, Long id, Long userId) {
        write(action, id, userId, null);
    }

    /**
     * 행위 로그 + 꼬리 정보. 꼬리가 있으면 "by=…" 뒤에 공백 하나를 두고 붙는다.
     * [보존 대상] 결재 생성 로그만 레거시부터 " type=…" 꼬리가 있었다 → detail 로 그대로 유지한다.
     */
    void write(String action, Long id, Long userId, String detail);
}
