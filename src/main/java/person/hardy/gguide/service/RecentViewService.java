package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecentViewService {

    private static final int MAX_RECENTLY_VIEWED = 12;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameService gameService;

    public List<GameDTO> getRecentlyViewedGames(String username, String locale) {
        User user = getUserByUsername(username);
        return gameService.getGamesByIds(getRecentlyViewedGameIds(user), locale);
    }

    public boolean recordRecentView(String username, String gameId) {
        User user = getUserByUsername(username);
        gameService.ensureGameExists(gameId);

        List<String> recentlyViewedGameIds = getRecentlyViewedGameIds(user);
        recentlyViewedGameIds.removeIf(id -> id.equals(gameId));
        recentlyViewedGameIds.add(0, gameId);

        if (recentlyViewedGameIds.size() > MAX_RECENTLY_VIEWED) {
            recentlyViewedGameIds = new ArrayList<>(recentlyViewedGameIds.subList(0, MAX_RECENTLY_VIEWED));
        }

        user.setRecentlyViewedGameIds(recentlyViewedGameIds);
        userRepository.save(user);
        return true;
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private List<String> getRecentlyViewedGameIds(User user) {
        if (user.getRecentlyViewedGameIds() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getRecentlyViewedGameIds());
    }
}
