package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.FavoriteService;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "*")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping
    public ResultVO<List<GameDTO>> getFavorites(Authentication authentication) {
        return ResultVO.success(favoriteService.getFavoriteGames(getCurrentUsername(authentication)));
    }

    @GetMapping("/{gameId}/status")
    public ResultVO<Boolean> getFavoriteStatus(@PathVariable String gameId, Authentication authentication) {
        return ResultVO.success(favoriteService.isFavorite(getCurrentUsername(authentication), gameId));
    }

    @PostMapping("/{gameId}")
    public ResultVO<Boolean> addFavorite(@PathVariable String gameId, Authentication authentication) {
        return ResultVO.success("Added to favorites", favoriteService.addFavorite(getCurrentUsername(authentication), gameId));
    }

    @DeleteMapping("/{gameId}")
    public ResultVO<Boolean> removeFavorite(@PathVariable String gameId, Authentication authentication) {
        return ResultVO.success("Removed from favorites", favoriteService.removeFavorite(getCurrentUsername(authentication), gameId));
    }

    private String getCurrentUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized");
        }
        return authentication.getName();
    }
}
