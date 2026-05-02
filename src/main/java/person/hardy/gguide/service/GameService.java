package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.GameCategoryCatalog;
import person.hardy.gguide.common.util.GameRegionCatalog;
import person.hardy.gguide.common.util.LocaleUtil;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.entity.Game;
import person.hardy.gguide.repository.GameRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private AuthService authService;

    public List<GameDTO> getAllGames(String locale) {
        return gameRepository.findAll().stream()
                .map(game -> convertToDTO(game, locale))
                .collect(Collectors.toList());
    }

    public List<GameDTO> getByCategory(String category, String locale) {
        String normalizedCategory = GameCategoryCatalog.normalizeCode(category);
        if (normalizedCategory == null) {
            return Collections.emptyList();
        }

        return gameRepository.findByCategoriesContaining(normalizedCategory).stream()
                .map(game -> convertToDTO(game, locale))
                .collect(Collectors.toList());
    }

    public GameDTO getGameById(String id, String locale) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        return convertToDTO(game, locale);
    }

    public GameDTO createGame(GameDTO gameDTO, String username, String locale) {
        if (!authService.isAdmin(username)) {
            throw new AccessDeniedException("Only admin users can create games");
        }

        Map<String, String> titleI18n = normalizeTranslations(gameDTO.getTitleI18n(), gameDTO.getTitle());
        Map<String, String> descriptionI18n = normalizeTranslations(gameDTO.getDescriptionI18n(), gameDTO.getDescription());
        String defaultTitle = LocaleUtil.resolveText(titleI18n, LocaleUtil.LOCALE_ZH_CN);

        if (defaultTitle.isBlank()) {
            throw new RuntimeException("Game title is required");
        }

        boolean exists = gameRepository.findAll().stream()
                .map(Game::getTitleI18n)
                .filter(map -> map != null && !map.isEmpty())
                .map(map -> LocaleUtil.resolveText(map, LocaleUtil.LOCALE_ZH_CN))
                .anyMatch(title -> title.equalsIgnoreCase(defaultTitle));

        if (exists) {
            throw new RuntimeException("Game title already exists");
        }

        Game game = new Game();
        game.setTitleI18n(titleI18n);
        game.setDescriptionI18n(descriptionI18n);
        game.setCoverImage(gameDTO.getCoverImage());
        game.setCinematicTrailer(gameDTO.getCinematicTrailer());
        game.setDownloadLink(gameDTO.getDownloadLink());
        game.setRating(gameDTO.getRating());
        game.setCategories(GameCategoryCatalog.normalizeCodes(gameDTO.getCategories()));
        game.setRegionCode(GameRegionCatalog.normalizeCode(gameDTO.getRegionCode()));
        game.setReleaseDate(gameDTO.getReleaseDate());

        Game saved = gameRepository.save(game);
        return convertToDTO(saved, locale);
    }

    public GameDTO updateGame(String id, GameDTO gameDTO, String username, String locale) {
        if (!authService.isAdmin(username)) {
            throw new AccessDeniedException("Only admin users can update games");
        }

        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        Map<String, String> titleI18n = normalizeTranslations(gameDTO.getTitleI18n(), gameDTO.getTitle());
        Map<String, String> descriptionI18n = normalizeTranslations(gameDTO.getDescriptionI18n(), gameDTO.getDescription());
        String defaultTitle = LocaleUtil.resolveText(titleI18n, LocaleUtil.LOCALE_ZH_CN);

        if (defaultTitle.isBlank()) {
            throw new RuntimeException("Game title is required");
        }

        game.setTitleI18n(titleI18n);
        game.setDescriptionI18n(descriptionI18n);
        game.setCoverImage(gameDTO.getCoverImage());
        game.setCinematicTrailer(gameDTO.getCinematicTrailer());
        game.setDownloadLink(gameDTO.getDownloadLink());
        game.setRating(gameDTO.getRating());
        game.setCategories(GameCategoryCatalog.normalizeCodes(gameDTO.getCategories()));
        game.setRegionCode(GameRegionCatalog.normalizeCode(gameDTO.getRegionCode()));
        game.setReleaseDate(gameDTO.getReleaseDate());
        game.setUpdatedAt(Instant.now());

        Game saved = gameRepository.save(game);
        return convertToDTO(saved, locale);
    }

    public List<GameDTO> getGamesByIds(List<String> ids, String locale) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Game> gameMap = gameRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Game::getId, game -> game));

        return ids.stream()
                .map(gameMap::get)
                .filter(game -> game != null)
                .map(game -> convertToDTO(game, locale))
                .collect(Collectors.toList());
    }

    public void ensureGameExists(String gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new RuntimeException("Game not found");
        }
    }

    private GameDTO convertToDTO(Game game, String locale) {
        GameDTO dto = new GameDTO();
        dto.setId(game.getId());
        dto.setTitleI18n(normalizeTranslations(game.getTitleI18n(), null));
        dto.setDescriptionI18n(normalizeTranslations(game.getDescriptionI18n(), null));
        dto.setTitle(LocaleUtil.resolveText(dto.getTitleI18n(), locale));
        dto.setDescription(LocaleUtil.resolveText(dto.getDescriptionI18n(), locale));
        dto.setCoverImage(game.getCoverImage());
        dto.setCinematicTrailer(game.getCinematicTrailer());
        dto.setDownloadLink(game.getDownloadLink());
        dto.setRating(game.getRating());
        dto.setCategories(GameCategoryCatalog.normalizeCodes(game.getCategories()));
        dto.setCategoryLabels(GameCategoryCatalog.resolveLabels(dto.getCategories(), locale));
        dto.setRegionCode(GameRegionCatalog.normalizeCode(game.getRegionCode()));
        dto.setRegionLabel(GameRegionCatalog.resolveLabel(dto.getRegionCode(), locale));
        dto.setReleaseDate(game.getReleaseDate());
        return dto;
    }

    private Map<String, String> normalizeTranslations(Map<String, String> source, String fallback) {
        Map<String, String> translations = LocaleUtil.normalizeTranslations(source, fallback);
        if (translations.isEmpty()) {
            return Collections.emptyMap();
        }

        return translations.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }
}
