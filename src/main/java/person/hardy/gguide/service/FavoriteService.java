package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class FavoriteService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameService gameService;

    public List<GameDTO> getFavoriteGames(String username, String locale) {
        User user = getUserByUsername(username);
        return gameService.getGamesByIds(getFavoriteGameIds(user), locale);
    }

    public boolean addFavorite(String username, String gameId) {
        User user = getUserByUsername(username);
        gameService.ensureGameExists(gameId);

        List<String> favoriteGameIds = getFavoriteGameIds(user);
        if (!favoriteGameIds.contains(gameId)) {
            favoriteGameIds.add(gameId);
            user.setFavoriteGameIds(favoriteGameIds);
            userRepository.save(user);
        }

        return true;
    }

    public boolean removeFavorite(String username, String gameId) {
        User user = getUserByUsername(username);
        List<String> favoriteGameIds = getFavoriteGameIds(user);

        boolean removed = favoriteGameIds.removeIf(id -> id.equals(gameId));
        if (removed) {
            user.setFavoriteGameIds(favoriteGameIds);
            userRepository.save(user);
        }

        return removed;
    }

    public boolean isFavorite(String username, String gameId) {
        User user = getUserByUsername(username);
        return getFavoriteGameIds(user).contains(gameId);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private List<String> getFavoriteGameIds(User user) {
        if (user.getFavoriteGameIds() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getFavoriteGameIds());
    }
}
