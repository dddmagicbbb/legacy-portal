package kr.hull.portal.approval.dto;

/**
 * 결재 처리 요청 - POST /api/approvals/{id}/process 의 요청 본문.
 *
 * [리팩토링] 레거시: Map&lt;String, Object&gt; 에서 userId·action·reason 을 꺼내 캐스팅 (S07 · B08)
 *           → 여기서: record. JSON 필드명은 레거시와 같다
 *  - [보존 대상] reason 을 보내지 않으면 레거시처럼 빈 문자열("")로 채운다 (compact 생성자).
 *    반려 사유가 빈 문자열로 저장되는 동작은 ApprovalApiCharacterizationTest 가 고정하고 있다
 *
 * @param action 1=상신 2=승인 3=반려 9=취소 (숫자 그대로 - 정의 밖 값은 서비스가 조용히 무시)
 */
public record ProcessRequest(Long userId, Integer action, String reason) {

    public ProcessRequest {
        // [보존 대상] 레거시 body.getOrDefault("reason", "")
        if (reason == null) {
            reason = "";
        }
    }
}
