package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기 - 콘솔 출력 구현.
 * [스멜] 역시 구체 클래스 직접 new. 출력 대상(파일/DB/콘솔)을 바꾸려면 호출부를 다 고쳐야 한다.
 *
 * [리팩토링] 레거시: 인터페이스 없는 구체 클래스, 서비스마다 new → AuditLogger 구현 + @Component 빈 (DIP · S04 · B02)
 *  - [보존 대상] 출력 형식("[AUDIT] " + line)은 그대로다.
 *
 * [리팩토링] 클래스 이름: 레거시 FileAuditLogger → ConsoleAuditLogger (Rename Class)
 *  - 실제로는 파일에 쓰지 않고 콘솔에 출력만 한다. 하는 일 그대로 이름 붙였다
 *  - 파일·DB 기록이 필요해지면 AuditLogger 를 구현하는 별도 클래스로 갈아끼운다 (서비스 코드는 그대로)
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        // 실습용 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
