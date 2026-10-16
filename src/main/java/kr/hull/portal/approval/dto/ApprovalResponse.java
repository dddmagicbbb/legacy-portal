package kr.hull.portal.approval.dto;

import kr.hull.portal.approval.domain.Approval;

import java.time.LocalDateTime;

/**
 * 결재 응답 - 생성·기안함·결재함 API 의 응답 본문.
 *
 * [리팩토링] 레거시: 컨트롤러가 JPA 엔티티 Approval 을 그대로 JSON 으로 반환 (엔티티 노출 · S07 · B08)
 *           → 여기서: 응답 전용 record 로 복사해서 반환. 엔티티에 필드·메서드가 늘어도 API 응답이 따라 바뀌지 않는다
 *  - [보존 대상] JSON 필드 12개의 이름·순서는 레거시 엔티티 직렬화 결과와 같다 (record 컴포넌트 순서 = JSON 순서)
 *  - [보존 대상] status·type·priority 는 정수 그대로다 (status 는 enum 이 아니라 getStatus() 의 정수 code)
 */
public record ApprovalResponse(
        Long id,
        String title,
        String content,
        int type,
        int status,
        int priority,
        Long drafterId,
        Long approverId,
        String rejectReason,
        long amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ApprovalResponse from(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTitle(),
                approval.getContent(),
                approval.getType(),
                approval.getStatus(),   // 정수 code (0/1/2/3/9)
                approval.getPriority(),
                approval.getDrafterId(),
                approval.getApproverId(),
                approval.getRejectReason(),
                approval.getAmount(),
                approval.getCreatedAt(),
                approval.getUpdatedAt());
    }
}
