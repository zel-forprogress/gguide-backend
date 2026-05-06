package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import person.hardy.gguide.model.dto.CreateGameCommentDTO;
import person.hardy.gguide.model.dto.GameCommentDTO;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.GameCommentService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GameCommentController {

    @Autowired
    private GameCommentService gameCommentService;

    @GetMapping("/{id}/comments")
    public ResultVO<List<GameCommentDTO>> getComments(@PathVariable String id) {
        return ResultVO.success(gameCommentService.getComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResultVO<GameCommentDTO> addComment(
            @PathVariable String id,
            @RequestBody CreateGameCommentDTO request,
            Principal principal
    ) {
        return ResultVO.success(gameCommentService.addComment(id, principal.getName(), request));
    }

    @PutMapping("/{id}/comments/{commentId}")
    public ResultVO<GameCommentDTO> updateComment(
            @PathVariable String id,
            @PathVariable String commentId,
            @RequestBody CreateGameCommentDTO request,
            Principal principal
    ) {
        return ResultVO.success(gameCommentService.updateComment(id, commentId, principal.getName(), request));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResultVO<String> deleteComment(
            @PathVariable String id,
            @PathVariable String commentId,
            Principal principal
    ) {
        gameCommentService.deleteComment(id, commentId, principal.getName());
        return ResultVO.success("Comment deleted");
    }
}
