package kr.hull.portal.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 code 변환기 (AttributeConverter · DB 저장값 보존)
 *  - 엔티티는 enum 으로 다루고, DB 컬럼에는 레거시와 같은 정수(0/1/2/3/9)를 그대로 저장·조회한다.
 *  - @Enumerated(STRING/ORDINAL) 대신 쓰는 이유: 문자 저장은 DB 값이 바뀌고, 순번 저장은 9 와 어긋난다.
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
