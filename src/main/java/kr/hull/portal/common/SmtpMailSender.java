package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기.
 * [스멜] 구체 클래스를 서비스가 직접 new 한다(인터페이스 없음). 테스트에서 가짜로 교체 불가.
 * 실습에서는 실제 SMTP 대신 콘솔에 출력만 한다.
 *
 * [리팩토링] 레거시: 인터페이스 없는 구체 클래스, 서비스마다 new → 여기서: MailSender 구현 + @Component 빈 (DIP · S04 · B02)
 *  - [보존 대상] 출력 형식(=== MAIL === / TO / SUBJECT / 본문 / ============)은 한 글자도 바꾸지 않았다.
 *  - 무상태 객체라 서비스마다 따로 만들던 인스턴스를 빈 하나로 공유해도 동작은 같다.
 */
@Component
public class SmtpMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제로는 JavaMailSender 등을 사용. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
