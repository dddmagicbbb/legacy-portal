package kr.hull.portal.approval.domain;

/**
 * 결재 금액 등급.
 *
 * [리팩토링] 레거시: ApprovalService.amountGrade 가 금액 하한 숫자(10000000·1000000·100000)를 if 사슬로 비교
 *           (Feature Envy · 매직넘버 · S06/S03) → 여기서: 등급과 하한을 enum 이 소유 (Move Method · 백로그 B06)
 *  - 등급 기준이 바뀌면 이 enum 상수 한 줄만 고친다. 서비스는 위임만 한다
 *  - [보존 대상] 경계값은 레거시와 같다: 1,000만원 이상 S · 100만원 이상 A · 10만원 이상 B · 그 외 C
 *    (경계는 "이상(>=)" - ApprovalServiceBranchCharacterizationTest 가 99,999 / 100,000 / … / 10,000,000 을 고정)
 *  - [보존 대상] 등급 문자열은 상수 이름 그대로 "S"·"A"·"B"·"C" (ApprovalService.amountGrade 의 반환값)
 */
public enum AmountGrade {

    // 위에서부터 차례로 비교하므로 하한이 큰 등급부터 선언한다
    S(10_000_000L),   // 1,000만원 이상
    A(1_000_000L),    // 100만원 이상 - 지출 우선순위 상향 기준(100만원)과 값은 같지만 다른 규칙이다
    B(100_000L),      // 10만원 이상
    C(Long.MIN_VALUE); // 그 외 전부 - 레거시 else 분기와 같이 하한이 없다 (0·음수 포함)

    private final long minimumAmount;

    AmountGrade(long minimumAmount) {
        this.minimumAmount = minimumAmount;
    }

    /** 금액(원)에 해당하는 등급. 하한 이상인 첫 등급을 돌려준다. */
    public static AmountGrade of(long amount) {
        for (AmountGrade grade : values()) {
            if (amount >= grade.minimumAmount) {
                return grade;
            }
        }
        return C;   // 도달하지 않는다 - C 의 하한이 Long.MIN_VALUE
    }
}
