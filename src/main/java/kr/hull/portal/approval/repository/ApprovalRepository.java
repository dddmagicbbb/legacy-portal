package kr.hull.portal.approval.repository;

import kr.hull.portal.approval.domain.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 결재 저장소.
 *
 * [리팩토링] 패키지 이동: kr.hull.portal.approval → kr.hull.portal.approval.repository (영속성(저장소) 계층)
 *  - 계층별 하위 패키지로 나눠 클래스의 역할이 패키지 이름에서 드러나게 했다 (Move Class)
 *  - [보존 대상] 인터페이스명·쿼리 메서드는 그대로. package·import 줄만 바뀌었다
 */
public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByDrafterId(Long drafterId);
    List<Approval> findByApproverId(Long approverId);
}
