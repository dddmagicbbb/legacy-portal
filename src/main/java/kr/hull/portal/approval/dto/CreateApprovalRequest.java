package kr.hull.portal.approval.dto;

/**
 * 결재 생성 요청 - POST /api/approvals 의 요청 본문.
 *
 * [리팩토링] 레거시: 컨트롤러가 Map&lt;String, Object&gt; 로 받아 (int)·(Number) 캐스팅 (요청 DTO 부재 · S07 · 백로그 B08)
 *           → 여기서: 필드가 타입으로 드러나는 record. 키 철자가 틀리면 컴파일러가 잡는다
 *  - [보존 대상] JSON 필드명(title·content·type·priority·drafterId·approverId·amount·urgent)은 레거시와 같다
 *  - [보존 대상] 생략 시 기본값도 레거시 getOrDefault 와 같다: amount → 0, urgent → false (compact 생성자)
 *  - type·priority·drafterId·approverId 는 레거시처럼 필수다. 래퍼 타입으로 받아, 빠지면 레거시와 같이
 *    서비스 호출 시점에 실패(500)하게 둔다 - 원시 타입이면 0 으로 조용히 바뀌어 동작이 달라진다
 *
 * @param type     1=지출 2=휴가 3=구매 4=기타 (숫자 그대로 - enum 화는 다음 단계)
 * @param priority 1=낮음 2=보통 3=높음
 */
public record CreateApprovalRequest(
        String title,
        String content,
        Integer type,
        Integer priority,
        Long drafterId,
        Long approverId,
        Long amount,
        Boolean urgent) {

    public CreateApprovalRequest {
        // [보존 대상] 레거시 body.getOrDefault("amount", 0) · getOrDefault("urgent", false)
        if (amount == null) {
            amount = 0L;
        }
        if (urgent == null) {
            urgent = false;
        }
    }
}
