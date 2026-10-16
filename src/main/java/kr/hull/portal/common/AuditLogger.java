package kr.hull.portal.common;

/**
 * 감사 로그 기록 추상화.
 *
 * [리팩토링] 레거시: 서비스가 구체 클래스 FileAuditLogger 를 필드에서 직접 new (스멜5 강결합 · S04)
 *           → 여기서: 서비스는 이 인터페이스에만 의존하고, 구현은 스프링이 생성자로 주입한다 (DIP · 백로그 B02)
 *  - 메서드 시그니처는 레거시 FileAuditLogger.write(line) 그대로다.
 *  - 출력 대상(파일·DB·콘솔)을 바꿔도 서비스 코드는 고치지 않는다.
 */
public interface AuditLogger {

    void write(String line);
}
