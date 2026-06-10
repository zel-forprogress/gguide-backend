package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import person.hardy.gguide.model.dto.AISettingsDTO;
import person.hardy.gguide.model.dto.AISettingsRequestDTO;
import person.hardy.gguide.model.dto.ChatConversationDTO;
import person.hardy.gguide.model.dto.ChatConversationSummaryDTO;
import person.hardy.gguide.model.dto.ChatRequestDTO;
import person.hardy.gguide.model.dto.ChatResponseDTO;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.AIChatService;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    @Autowired
    private AIChatService aiChatService;

    @GetMapping("/settings")
    public ResultVO<AISettingsDTO> getSettings(Authentication authentication) {
        return ResultVO.success(aiChatService.getSettings(authentication.getName()));
    }

    @PutMapping("/settings")
    public ResultVO<AISettingsDTO> updateSettings(
            @RequestBody AISettingsRequestDTO request,
            Authentication authentication
    ) {
        return ResultVO.success(aiChatService.updateSettings(authentication.getName(), request));
    }

    @PostMapping("/chat")
    public ResultVO<ChatResponseDTO> chat(
            @RequestBody ChatRequestDTO request,
            Authentication authentication
    ) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ResultVO.error("消息列表不能为空");
        }

        ChatResponseDTO response = aiChatService.chat(authentication.getName(), request);
        return ResultVO.success(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamChat(
            @RequestBody ChatRequestDTO request,
            Authentication authentication
    ) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String username = authentication.getName();
        StreamingResponseBody body = outputStream -> {
            try {
                aiChatService.streamChat(username, request, outputStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @GetMapping("/conversations")
    public ResultVO<List<ChatConversationSummaryDTO>> listConversations(Authentication authentication) {
        return ResultVO.success(aiChatService.listConversations(authentication.getName()));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResultVO<ChatConversationDTO> getConversation(
            @PathVariable String conversationId,
            Authentication authentication
    ) {
        return ResultVO.success(aiChatService.getConversation(authentication.getName(), conversationId));
    }
}
