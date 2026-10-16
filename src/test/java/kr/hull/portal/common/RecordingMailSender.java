package kr.hull.portal.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 메일 발송기 - 보낸 메일을 콘솔 대신 목록에 기록한다.
 * B02(생성자 주입) 덕분에 서비스 코드를 고치지 않고 이 구현을 주입할 수 있다.
 */
public class RecordingMailSender implements MailSender {

    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> sentMails = new ArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sentMails.add(new SentMail(to, subject, body));
    }

    public List<SentMail> sentMails() {
        return sentMails;
    }

    /** 스프링 컨텍스트가 테스트 사이에 재사용되므로 테스트마다 비운다. */
    public void clear() {
        sentMails.clear();
    }
}
