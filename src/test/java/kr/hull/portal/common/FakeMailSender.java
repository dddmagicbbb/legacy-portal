package kr.hull.portal.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 단위 테스트용 가짜 메일 발송기 (Fake).
 * 실제로 보내지 않고 send 호출만 목록에 남긴다 - 몇 번, 누구에게, 어떤 제목·본문으로 불렸는지 검증용.
 * MailSender 인터페이스 + 생성자 주입(B02) 덕분에 서비스 코드를 고치지 않고 끼워 넣을 수 있다.
 */
public class FakeMailSender implements MailSender {

    public record SendCall(String to, String subject, String body) {
    }

    private final List<SendCall> sendCalls = new ArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sendCalls.add(new SendCall(to, subject, body));
    }

    public List<SendCall> sendCalls() {
        return sendCalls;
    }

    public SendCall lastSendCall() {
        return sendCalls.get(sendCalls.size() - 1);
    }
}
