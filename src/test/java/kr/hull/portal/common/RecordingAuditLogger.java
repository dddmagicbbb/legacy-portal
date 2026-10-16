package kr.hull.portal.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 감사 로거 - 기록한 줄을 콘솔 대신 목록에 모은다.
 */
public class RecordingAuditLogger implements AuditLogger {

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
