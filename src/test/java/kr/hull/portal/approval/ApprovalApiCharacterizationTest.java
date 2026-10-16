package kr.hull.portal.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [특성화 테스트 - 3차] 결재 API 의 요청/응답 JSON 계약을 관찰한 그대로 고정한다 (MockMvc).
 *
 * - 백로그 B01-5. DTO(record) 분리(B08) 전에 반드시 있어야 하는 안전망 - JSON 형식이 유일한 판정 기준이다.
 * - 전체 컨텍스트(@SpringBootTest)로 띄우고 테스트마다 롤백한다. 사용자는 DataSeeder 가 넣은 4명을 이름으로 찾는다.
 * - 고정하는 것: 응답 필드 이름·순서, 숫자 코드(status·type·priority), 생략 시 기본값, 처리 API 의 200 + 빈 본문,
 *   조건 불충족 처리도 200 (조용한 무시).
 * - 한번 만든 본문(호출·기대값)은 리팩토링 중 수정하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalApiCharacterizationTest {

    private static final List<String> APPROVAL_JSON_FIELDS = List.of(
            "id", "title", "content", "type", "status", "priority",
            "drafterId", "approverId", "rejectReason", "amount", "createdAt", "updatedAt");
    private static final long MISSING_APPROVAL_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private long staffId;
    private long teamLeaderId;

    @BeforeEach
    void setUp() {
        staffId = findUserIdByName("김사원");
        teamLeaderId = findUserIdByName("박팀장");
    }

    private long findUserIdByName(String name) {
        return userRepository.findAll().stream()
                .filter(user -> user.getName().equals(name))
                .map(User::getId)
                .findFirst()
                .orElseThrow();
    }

    private JsonNode createApproval(String requestJson) throws Exception {
        String response = mockMvc.perform(post("/api/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String createRequest(long amount) {
        return """
                {"title":"Laptop","content":"for dev","type":1,"priority":2,
                 "drafterId":%d,"approverId":%d,"amount":%d}
                """.formatted(staffId, teamLeaderId, amount);
    }

    private String processAndReturnBody(long approvalId, String requestJson) throws Exception {
        return mockMvc.perform(post("/api/approvals/" + approvalId + "/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode findInList(String url, long approvalId) throws Exception {
        String response = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : objectMapper.readTree(response)) {
            if (node.get("id").asLong() == approvalId) {
                return node;
            }
        }
        throw new AssertionError("목록에 결재 " + approvalId + " 가 없습니다: " + url);
    }

    private List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void 결재_생성_응답은_12개_필드를_이_순서로_담고_숫자_코드는_정수다() throws Exception {
        JsonNode created = createApproval(createRequest(500_000L));

        assertThat(fieldNamesOf(created)).containsExactlyElementsOf(APPROVAL_JSON_FIELDS);
        assertThat(created.get("status").isInt()).isTrue();
        assertThat(created.get("status").asInt()).isEqualTo(0);
        assertThat(created.get("type").asInt()).isEqualTo(1);
        assertThat(created.get("priority").asInt()).isEqualTo(2);
        assertThat(created.get("drafterId").asLong()).isEqualTo(staffId);
        assertThat(created.get("approverId").asLong()).isEqualTo(teamLeaderId);
        assertThat(created.get("rejectReason").isNull()).isTrue();
        assertThat(created.get("amount").asLong()).isEqualTo(500_000L);
    }

    @Test
    void amount와_urgent를_생략하면_amount는_0이고_priority는_요청값_그대로다() throws Exception {
        JsonNode created = createApproval("""
                {"title":"Leave","content":"family","type":2,"priority":1,"drafterId":%d,"approverId":%d}
                """.formatted(staffId, teamLeaderId));

        assertThat(created.get("amount").asLong()).isEqualTo(0L);
        assertThat(created.get("priority").asInt()).isEqualTo(1);
        assertThat(created.get("type").asInt()).isEqualTo(2);
    }

    @Test
    void urgent가_true면_요청한_priority와_관계없이_응답_priority는_3이다() throws Exception {
        JsonNode created = createApproval("""
                {"title":"Urgent","content":"now","type":4,"priority":1,
                 "drafterId":%d,"approverId":%d,"amount":10,"urgent":true}
                """.formatted(staffId, teamLeaderId));

        assertThat(created.get("priority").asInt()).isEqualTo(3);
    }

    @Test
    void 상신_처리는_200과_빈_본문이고_100만원_지출은_기안함에서_priority_3으로_보인다() throws Exception {
        long approvalId = createApproval(createRequest(1_000_000L)).get("id").asLong();

        String body = processAndReturnBody(approvalId, "{\"userId\":%d,\"action\":1}".formatted(staffId));

        assertThat(body).isEmpty();
        JsonNode draft = findInList("/api/approvals/drafts/" + staffId, approvalId);
        assertThat(draft.get("status").asInt()).isEqualTo(1);
        assertThat(draft.get("priority").asInt()).isEqualTo(3);
        assertThat(fieldNamesOf(draft)).containsExactlyElementsOf(APPROVAL_JSON_FIELDS);
    }

    @Test
    void 결재자가_아닌_사용자의_승인_요청도_200이고_결재함에서_상신_상태로_남는다() throws Exception {
        long approvalId = createApproval(createRequest(500_000L)).get("id").asLong();
        processAndReturnBody(approvalId, "{\"userId\":%d,\"action\":1}".formatted(staffId));

        String body = processAndReturnBody(approvalId, "{\"userId\":%d,\"action\":2}".formatted(staffId));

        assertThat(body).isEmpty();
        assertThat(findInList("/api/approvals/inbox/" + teamLeaderId, approvalId).get("status").asInt()).isEqualTo(1);
    }

    @Test
    void 없는_결재_id를_처리해도_200과_빈_본문이다() throws Exception {
        String body = processAndReturnBody(MISSING_APPROVAL_ID, "{\"userId\":%d,\"action\":2}".formatted(teamLeaderId));

        assertThat(body).isEmpty();
    }

    @Test
    void reason을_생략하고_반려하면_rejectReason은_빈_문자열이다() throws Exception {
        long approvalId = createApproval(createRequest(500_000L)).get("id").asLong();
        processAndReturnBody(approvalId, "{\"userId\":%d,\"action\":1}".formatted(staffId));

        processAndReturnBody(approvalId, "{\"userId\":%d,\"action\":3}".formatted(teamLeaderId));

        JsonNode rejected = findInList("/api/approvals/inbox/" + teamLeaderId, approvalId);
        assertThat(rejected.get("status").asInt()).isEqualTo(3);
        assertThat(rejected.get("rejectReason").isTextual()).isTrue();
        assertThat(rejected.get("rejectReason").asText()).isEmpty();
    }

    @Test
    void 결재함은_결재자_기준이라_기안자의_결재함에는_나오지_않는다() throws Exception {
        long approvalId = createApproval(createRequest(500_000L)).get("id").asLong();

        String staffInbox = mockMvc.perform(get("/api/approvals/inbox/" + staffId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        for (JsonNode node : objectMapper.readTree(staffInbox)) {
            assertThat(node.get("id").asLong()).isNotEqualTo(approvalId);
        }
        assertThat(findInList("/api/approvals/inbox/" + teamLeaderId, approvalId).get("status").asInt()).isEqualTo(0);
    }
}
