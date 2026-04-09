package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import person.hardy.gguide.model.dto.ChatRequestDTO;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.AIChatService;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIChatController {

    @Autowired
    private AIChatService aiChatService;

    @PostMapping("/chat")
    public ResultVO<String> chat(@RequestBody ChatRequestDTO request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ResultVO.error("消息列表不能为空");
        }

        String response = aiChatService.chat(request.getMessages());
        return ResultVO.success(response);
    }
}
