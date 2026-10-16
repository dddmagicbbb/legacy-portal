package kr.hull.portal.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 감사 로거 - 기록한 줄을 콘솔 대신 목록에 모은다.
 *
 * ConsoleAuditLogger 를 상속해 "[시각] ACTION id=… by=…" 포맷은 운영 코드의 것을 그대로 쓰고,
 * 출력(write(line))만 목록 저장으로 바꾼다. 그래서 특성화 테스트가 검증하는 문자열은 운영 포맷 그 자체다.
 */
public class RecordingAuditLogger extends ConsoleAuditLogger {

    private final List<String> writtenLines = new ArrayList<>();

    @Override
    public void write(String line) {
        writtenLines.add(line);
    }

    public List<String> writtenLines() {
        return writtenLines;
    }

    /** 스프링 컨텍스트가 테스트 사이에 재사용되므로 테스트마다 비운다. */
    public void clear() {
        writtenLines.clear();
    }
}
