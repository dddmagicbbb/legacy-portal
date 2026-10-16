package kr.hull.portal.common;

/**
 * 메일 발송 추상화.
 *
 * [리팩토링] 레거시: 서비스가 구체 클래스 SmtpMailSender 를 필드에서 직접 new (스멜5 강결합 · S04)
 *           → 여기서: 서비스는 이 인터페이스에만 의존하고, 구현은 스프링이 생성자로 주입한다 (DIP · 백로그 B02)
 *  - 메서드 시그니처는 레거시 SmtpMailSender.send(to, subject, body) 그대로다.
 *  - 실제 SMTP·가짜(테스트) 구현으로 바꿔도 서비스 코드는 고치지 않는다.
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
