package kr.hull.portal.approval;

/**
 * 결재 상태.
 *
 * [리팩토링] 레거시 int status 매직넘버(0/1/2/3/9) → 여기서 의미 있는 enum 으로 (Replace Magic Number with Enum · 스멜 S03)
 *  - 코드만 봐도 상태 의미가 드러나고, 상태 비교를 컴파일러가 검사한다.
 *  - [보존 대상] DB 저장값은 기존 정수 code 그대로다. ApprovalStatusConverter 가 enum ↔ code 를 매핑한다.
 *    @Enumerated(ORDINAL) 를 쓰면 CANCELED(9) 가 순번 4 로 저장되어 데이터가 깨지므로 code 를 명시한다.
 *  - [보존 대상] API 응답 JSON 의 status 는 계속 정수다 (Approval.getStatus() 가 code 를 반환).
 */
public enum ApprovalStatus {

    DRAFT(0),       // 임시저장
    SUBMITTED(1),   // 상신
    APPROVED(2),    // 승인
    REJECTED(3),    // 반려
    CANCELED(9);    // 취소 - 4~8 은 비어 있는 레거시 번호 그대로

    private final int code;

    ApprovalStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** DB·API 의 정수 code → enum. 정의되지 않은 code 는 레거시에서 만들어지지 않는 값이라 예외로 알린다. */
    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("정의되지 않은 결재 상태 code: " + code);
    }
}
