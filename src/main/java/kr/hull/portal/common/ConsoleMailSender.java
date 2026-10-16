package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기 - 콘솔 출력 구현.
 * [스멜] 구체 클래스를 서비스가 직접 new 한다(인터페이스 없음). 테스트에서 가짜로 교체 불가.
 * 실습에서는 실제 SMTP 대신 콘솔에 출력만 한다.
 *
 * [리팩토링] 레거시: 인터페이스 없는 구체 클래스, 서비스마다 new → MailSender 구현 + @Component 빈 (DIP · S04 · B02)
 *  - [보존 대상] 출력 형식(=== MAIL === / TO / SUBJECT / 본문 / ============)은 한 글자도 바꾸지 않았다.
 *  - 무상태 객체라 서비스마다 따로 만들던 인스턴스를 빈 하나로 공유해도 동작은 같다.
 *
 * [리팩토링] 클래스 이름: 레거시 SmtpMailSender → ConsoleMailSender (Rename Class)
 *  - 실제로는 SMTP 를 쓰지 않고 콘솔에 출력만 한다. 이름이 하는 일과 달라 오해를 부르므로 하는 일 그대로 이름 붙였다
 *  - 실제 SMTP 구현이 필요해지면 MailSender 를 구현하는 SmtpMailSender 를 새로 만들어 갈아끼운다 (서비스 코드는 그대로)
 */
@Component
public class ConsoleMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실습용 콘솔 출력. 실제 발송이 필요하면 JavaMailSender 를 쓰는 별도 구현으로 교체한다.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
