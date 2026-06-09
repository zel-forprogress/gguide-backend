package person.hardy.gguide.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import person.hardy.gguide.model.dto.CreateGameCommentDTO;
import person.hardy.gguide.model.dto.GameCommentDTO;
import person.hardy.gguide.model.dto.UserProfileDTO;
import person.hardy.gguide.model.entity.GameComment;
import person.hardy.gguide.repository.GameCommentRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameCommentServiceTests {

    @Mock
    private GameCommentRepository gameCommentRepository;

    @Mock
    private GameService gameService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private GameCommentService gameCommentService;

    @Test
    void addCommentUsesCurrentUserProfileAndTrimsContent() {
        CreateGameCommentDTO request = request("  很好玩  ");
        when(authService.getCurrentUser("alice"))
                .thenReturn(new UserProfileDTO("alice", false, "avatar.png"));
        when(gameCommentRepository.save(any(GameComment.class)))
                .thenAnswer(invocation -> {
                    GameComment saved = invocation.getArgument(0);
                    saved.setId("comment-1");
                    return saved;
                });

        GameCommentDTO result = gameCommentService.addComment("game-1", "alice", request);

        assertEquals("comment-1", result.getId());
        assertEquals("game-1", result.getGameId());
        assertEquals("alice", result.getUsername());
        assertEquals("avatar.png", result.getAvatarUrl());
        assertEquals("很好玩", result.getContent());
    }

    @Test
    void updateCommentAllowsOwner() {
        CreateGameCommentDTO request = request("更新后的评论");
        GameComment comment = comment("comment-1", "game-1", "alice", "旧评论");
        when(gameCommentRepository.findById("comment-1")).thenReturn(Optional.of(comment));
        when(gameCommentRepository.save(any(GameComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GameCommentDTO result = gameCommentService.updateComment("game-1", "comment-1", "alice", request);

        assertEquals("更新后的评论", result.getContent());
        assertNotNull(result.getUpdatedAt());
        verify(authService, never()).isAdmin(anyString());
    }

    @Test
    void updateCommentRejectsOtherUserWhenNotAdmin() {
        CreateGameCommentDTO request = request("不该成功");
        when(gameCommentRepository.findById("comment-1"))
                .thenReturn(Optional.of(comment("comment-1", "game-1", "alice", "旧评论")));
        when(authService.isAdmin("bob")).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> gameCommentService.updateComment("game-1", "comment-1", "bob", request)
        );
        verify(gameCommentRepository, never()).save(any(GameComment.class));
    }

    @Test
    void deleteCommentAllowsAdminToManageAnyComment() {
        GameComment comment = comment("comment-1", "game-1", "alice", "旧评论");
        when(gameCommentRepository.findById("comment-1")).thenReturn(Optional.of(comment));
        when(authService.isAdmin("admin")).thenReturn(true);

        gameCommentService.deleteComment("game-1", "comment-1", "admin");

        verify(gameCommentRepository).delete(comment);
    }

    private CreateGameCommentDTO request(String content) {
        CreateGameCommentDTO request = new CreateGameCommentDTO();
        request.setContent(content);
        return request;
    }

    private GameComment comment(String id, String gameId, String username, String content) {
        GameComment comment = new GameComment();
        comment.setId(id);
        comment.setGameId(gameId);
        comment.setUsername(username);
        comment.setAvatarUrl(username + ".png");
        comment.setContent(content);
        comment.setCreatedAt(Instant.parse("2026-05-02T13:22:00Z"));
        return comment;
    }
}
