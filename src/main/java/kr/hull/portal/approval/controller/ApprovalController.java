package kr.hull.portal.approval.controller;

import kr.hull.portal.approval.dto.ApprovalResponse;
import kr.hull.portal.approval.dto.CreateApprovalRequest;
import kr.hull.portal.approval.dto.ProcessRequest;
import kr.hull.portal.approval.service.ApprovalService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 결재 REST 컨트롤러.
 * [스멜] 컨트롤러가 Map 으로 파라미터를 받아 그대로 흘려보낸다(요청 DTO 부재, 검증 없음).
 * [스멜] 서비스의 매직넘버 action 을 그대로 노출한다.
 *
 * [리팩토링] 패키지 이동: kr.hull.portal.approval → kr.hull.portal.approval.controller (표현(Web/API) 계층)
 *  - 계층별 하위 패키지로 나눠 클래스의 역할이 패키지 이름에서 드러나게 했다 (Move Class)
 *  - [보존 대상] 클래스명·public 메서드·엔드포인트·DB 저장값은 그대로. package·import 줄만 바뀌었다
 *
 * [리팩토링] 레거시 Map&lt;String, Object&gt; 요청 · 엔티티 응답 → record DTO (Introduce Parameter Object · S07 · B08)
 *  - 요청: CreateApprovalRequest · ProcessRequest, 응답: ApprovalResponse (JPA 엔티티를 API 에 노출하지 않는다)
 *  - [보존 대상] URL·HTTP 메서드·요청/응답 JSON 필드명·순서·정수 status·생략 시 기본값은 레거시와 같다
 *    (ApprovalApiCharacterizationTest 8개가 고정)
 *  - 서비스의 public 시그니처는 그대로 - 컨트롤러가 DTO 를 풀어서 기존 메서드를 호출한다
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
        // [리팩토링·DTO] 레거시 (int) body.get("type") 등 캐스팅 8줄 → record 접근자.
        //               type·priority·drafterId·approverId 가 빠지면 레거시처럼 여기서 실패(500)한다 (자동 언박싱)
        return ApprovalResponse.from(service.create(
                request.title(),
                request.content(),
                request.type(),        // type: 1=지출 2=휴가 3=구매 4=기타
                request.priority(),    // priority: 1=낮음 2=보통 3=높음 — 범위 밖 값도 레거시처럼 그대로 통과
                request.drafterId(),
                request.approverId(),
                request.amount(),      // 생략 시 0 (CreateApprovalRequest compact 생성자)
                request.urgent()       // 생략 시 false
        ));
    }

    // action: 1=상신, 2=승인, 3=반려, 9=취소  ([스멜] 매직넘버를 API 가 그대로 강요 - enum 화는 다음 단계)
    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody ProcessRequest request) {
        service.processApproval(
                id,
                request.userId(),
                request.action(),
                request.reason()       // 생략 시 "" (ProcessRequest compact 생성자) - 레거시 getOrDefault("reason", "")
        );
    }

    @GetMapping("/drafts/{userId}")
    public List<ApprovalResponse> drafts(@PathVariable Long userId) {
        // [리팩토링·DTO] 엔티티 목록 → 응답 record 목록 (JSON 은 동일)
        return service.myDrafts(userId).stream().map(ApprovalResponse::from).toList();
    }

    @GetMapping("/inbox/{userId}")
    public List<ApprovalResponse> inbox(@PathVariable Long userId) {
        return service.myInbox(userId).stream().map(ApprovalResponse::from).toList();
    }
}
