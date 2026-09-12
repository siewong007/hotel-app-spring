package com.hotelapp.support;

import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.core.web.ClientIp;
import com.hotelapp.support.SupportModels.SupportActionRequest;
import com.hotelapp.support.SupportModels.SupportAgent;
import com.hotelapp.support.SupportModels.SupportConversationDetail;
import com.hotelapp.support.SupportModels.SupportConversationListResponse;
import com.hotelapp.support.SupportModels.SupportMessageRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the staff half of {@code modules/support/routes.rs}:
 * {@code /api/support/conversations*}, {@code /api/support/agents}.
 * The guest-portal support surface lives in {@link GuestSupportController}.
 */
@RestController
public class StaffSupportController {

    private final StaffSupport support;
    private final ClientIp clientIp;

    public StaffSupportController(StaffSupport support, ClientIp clientIp) {
        this.support = support;
        this.clientIp = clientIp;
    }

    private static long actorId() {
        return CurrentUser.require().userId();
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null
                : value.length() > 512 ? value.substring(0, 512) : value;
    }

    @GetMapping("/api/support/conversations")
    public SupportConversationListResponse listStaffConversations(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(name = "assigned_to_user_id", required = false) Long assignedToUserId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        long actorId = actorId();
        PermissionGateHelper.check(actorId, "support:read");
        return support.listStaffConversations(actorId, queue, status, priority,
                assignedToUserId, search, page, pageSize);
    }

    @GetMapping("/api/support/agents")
    public List<SupportAgent> listSupportAgents() {
        long actorId = actorId();
        PermissionGateHelper.checkAny(actorId, List.of("support:assign", "support:manage"));
        return support.listSupportAgents();
    }

    @GetMapping("/api/support/conversations/{conversationId}")
    public SupportConversationDetail getStaffConversation(@PathVariable long conversationId) {
        PermissionGateHelper.check(actorId(), "support:read");
        return support.staffDetail(conversationId);
    }

    @PostMapping("/api/support/conversations/{conversationId}/messages")
    public SupportConversationDetail sendStaffMessage(HttpServletRequest http,
            @PathVariable long conversationId, @RequestBody SupportMessageRequest request) {
        long actorId = actorId();
        return support.sendStaffMessage(actorId, conversationId, request,
                clientIp.extract(http), userAgent(http));
    }

    @PostMapping("/api/support/conversations/{conversationId}/actions")
    public SupportConversationDetail applyStaffAction(HttpServletRequest http,
            @PathVariable long conversationId, @RequestBody SupportActionRequest request) {
        long actorId = actorId();
        return support.applyStaffAction(actorId, conversationId, request,
                clientIp.extract(http), userAgent(http));
    }
}
