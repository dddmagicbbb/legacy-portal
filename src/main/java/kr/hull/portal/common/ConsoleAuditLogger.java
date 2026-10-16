package kr.hull.portal.common;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
 *
 * [리팩토링] 감사 로그 포맷을 여기 한 곳으로 모았다 (중복 제거 E1 · Extract Method / Move Method · B05)
 *  - 레거시: 결재·공지·일정 서비스 6곳에 복붙된
 *      String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
 *      audit.write("[" + now + "] NOTICE PUBLISH id=" + n.getId() + " by=" + userId);
 *  - 여기서: write(action, id, userId[, detail]) 가 같은 문자열을 만들어 write(line) 으로 넘긴다
 *  - [보존 대상] 결과 문자열은 레거시와 한 글자도 같다: "[yyyy-MM-dd HH:mm:ss] ACTION id=… by=…"(+ " " + detail)
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void write(String line) {
        // 실습용 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }

    @Override
    public void write(String action, Long id, Long userId, String detail) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String line = "[" + timestamp + "] " + action + " id=" + id + " by=" + userId;
        write(detail == null ? line : line + " " + detail);
    }
}
