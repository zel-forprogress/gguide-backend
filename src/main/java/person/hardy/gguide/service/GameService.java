package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import person.hardy.gguide.model.entity.Game;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.repository.GameRepository;

import java.util.List;
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
        return gameRepository.findByCategory(category).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public GameDTO createGame(GameDTO gameDTO) {
        if (gameRepository.existsByTitle(gameDTO.getTitle())) {
            throw new RuntimeException("游戏名称已存在");
        }
        Game game = new Game();
        game.setTitle(gameDTO.getTitle());
        game.setDescription(gameDTO.getDescription());
        game.setCoverImage(gameDTO.getCoverImage());
        game.setCinematicTrailer(gameDTO.getCinematicTrailer());
        game.setDownloadLink(gameDTO.getDownloadLink());
        game.setRating(gameDTO.getRating());
        game.setCategory(gameDTO.getCategory());
        game.setReleaseDate(gameDTO.getReleaseDate());

        Game saved = gameRepository.save(game);
        return convertToDTO(saved);
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
        dto.setCategory(game.getCategory());
        dto.setReleaseDate(game.getReleaseDate());
        return dto;
    }
}
