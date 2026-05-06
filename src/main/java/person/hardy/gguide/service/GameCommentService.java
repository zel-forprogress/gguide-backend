package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import person.hardy.gguide.model.dto.CreateGameCommentDTO;
import person.hardy.gguide.model.dto.GameCommentDTO;
import person.hardy.gguide.model.dto.UserProfileDTO;
import person.hardy.gguide.model.entity.GameComment;
import person.hardy.gguide.repository.GameCommentRepository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GameCommentService {

    private static final int MAX_COMMENT_LENGTH = 500;

    @Autowired
    private GameCommentRepository gameCommentRepository;

    @Autowired
    private GameService gameService;

    @Autowired
    private AuthService authService;

    public List<GameCommentDTO> getComments(String gameId) {
        gameService.ensureGameExists(gameId);
        return gameCommentRepository.findByGameIdOrderByCreatedAtAsc(gameId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public GameCommentDTO addComment(String gameId, String username, CreateGameCommentDTO request) {
        gameService.ensureGameExists(gameId);
        String content = normalizeContent(request);

        UserProfileDTO currentUser = authService.getCurrentUser(username);
        GameComment comment = new GameComment();
        comment.setGameId(gameId);
        comment.setUsername(currentUser.getUsername());
        comment.setAvatarUrl(currentUser.getAvatarUrl());
        comment.setContent(content);
        comment.setCreatedAt(Instant.now());

        return toDto(gameCommentRepository.save(comment));
    }

    public GameCommentDTO updateComment(String gameId, String commentId, String username, CreateGameCommentDTO request) {
        gameService.ensureGameExists(gameId);
        String content = normalizeContent(request);
        GameComment comment = getCommentForGame(gameId, commentId);
        ensureCanManage(comment, username);

        comment.setContent(content);
        comment.setUpdatedAt(Instant.now());
        return toDto(gameCommentRepository.save(comment));
    }

    public void deleteComment(String gameId, String commentId, String username) {
        gameService.ensureGameExists(gameId);
        GameComment comment = getCommentForGame(gameId, commentId);
        ensureCanManage(comment, username);
        gameCommentRepository.delete(comment);
    }

    private String normalizeContent(CreateGameCommentDTO request) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new RuntimeException("Comment content is required");
        }

        String content = request.getContent().trim();
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new RuntimeException("Comment content is too long");
        }

        return content;
    }

    private GameComment getCommentForGame(String gameId, String commentId) {
        GameComment comment = gameCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        if (!gameId.equals(comment.getGameId())) {
            throw new RuntimeException("Comment not found");
        }
        return comment;
    }

    private void ensureCanManage(GameComment comment, String username) {
        boolean isOwner = comment.getUsername() != null && comment.getUsername().equals(username);
        if (!isOwner && !authService.isAdmin(username)) {
            throw new AccessDeniedException("No permission to manage this comment");
        }
    }

    private GameCommentDTO toDto(GameComment comment) {
        return new GameCommentDTO(
                comment.getId(),
                comment.getGameId(),
                comment.getUsername(),
                comment.getAvatarUrl(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
