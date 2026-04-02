package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.entity.Game;
import person.hardy.gguide.repository.GameRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private GameRepository gameRepository;

    public List<GameDTO> getAllGames() {
        return gameRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<GameDTO> getByCategory(String category) {
        return gameRepository.findByCategoriesContaining(category).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public GameDTO getGameById(String id) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        return convertToDTO(game);
    }

    public GameDTO createGame(GameDTO gameDTO) {
        if (gameRepository.existsByTitle(gameDTO.getTitle())) {
            throw new RuntimeException("Game title already exists");
        }

        Game game = new Game();
        game.setTitle(gameDTO.getTitle());
        game.setDescription(gameDTO.getDescription());
        game.setCoverImage(gameDTO.getCoverImage());
        game.setCinematicTrailer(gameDTO.getCinematicTrailer());
        game.setDownloadLink(gameDTO.getDownloadLink());
        game.setRating(gameDTO.getRating());
        game.setCategories(normalizeCategories(gameDTO.getCategories()));
        game.setReleaseDate(gameDTO.getReleaseDate());

        Game saved = gameRepository.save(game);
        return convertToDTO(saved);
    }

    public List<GameDTO> getGamesByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Game> gameMap = gameRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Game::getId, game -> game));

        return ids.stream()
                .map(gameMap::get)
                .filter(game -> game != null)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void ensureGameExists(String gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new RuntimeException("Game not found");
        }
    }

    private GameDTO convertToDTO(Game game) {
        GameDTO dto = new GameDTO();
        dto.setId(game.getId());
        dto.setTitle(game.getTitle());
        dto.setDescription(game.getDescription());
        dto.setCoverImage(game.getCoverImage());
        dto.setCinematicTrailer(game.getCinematicTrailer());
        dto.setDownloadLink(game.getDownloadLink());
        dto.setRating(game.getRating());
        dto.setCategories(normalizeCategories(game.getCategories()));
        dto.setReleaseDate(game.getReleaseDate());
        return dto;
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }

        return categories.stream()
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
