package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
@CrossOrigin(origins = "*")
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
