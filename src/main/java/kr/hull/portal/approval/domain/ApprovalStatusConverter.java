package kr.hull.portal.approval.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 code 변환기 (AttributeConverter · DB 저장값 보존)
 *  - 엔티티는 enum 으로 다루고, DB 컬럼에는 레거시와 같은 정수(0/1/2/3/9)를 그대로 저장·조회한다.
 *  - @Enumerated(STRING/ORDINAL) 대신 쓰는 이유: 문자 저장은 DB 값이 바뀌고, 순번 저장은 9 와 어긋난다.
 *
 * [리팩토링] 패키지 이동: kr.hull.portal.approval → kr.hull.portal.approval.domain (도메인 계층 - 엔티티·값 타입·DB 매핑 변환기)
 *  - 계층별 하위 패키지로 나눠 클래스의 역할이 패키지 이름에서 드러나게 했다 (Move Class)
 *  - [보존 대상] 클래스명·public 메서드·엔드포인트·DB 저장값은 그대로. package·import 줄만 바뀌었다
 */
@Converter
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalStatus.fromCode(code);
    }
}
