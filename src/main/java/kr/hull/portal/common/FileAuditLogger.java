package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기.
 * [스멜] 역시 구체 클래스 직접 new. 출력 대상(파일/DB/콘솔)을 바꾸려면 호출부를 다 고쳐야 한다.
 *
 * [리팩토링] 레거시: 인터페이스 없는 구체 클래스, 서비스마다 new → 여기서: AuditLogger 구현 + @Component 빈 (DIP · S04 · B02)
 *  - [보존 대상] 출력 형식("[AUDIT] " + line)은 그대로다.
 */
@Component
public class FileAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
