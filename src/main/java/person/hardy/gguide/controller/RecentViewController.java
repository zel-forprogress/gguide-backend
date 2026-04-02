package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import person.hardy.gguide.common.util.LocaleUtil;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.RecentViewService;

import java.util.List;

@RestController
@RequestMapping("/api/recently-viewed")
@CrossOrigin(origins = "*")
public class RecentViewController {

    @Autowired
    private RecentViewService recentViewService;

    @GetMapping
    public ResultVO<List<GameDTO>> getRecentlyViewed(
            Authentication authentication,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        String locale = LocaleUtil.resolveRequestedLocale(lang, acceptLanguage);
        return ResultVO.success(recentViewService.getRecentlyViewedGames(getCurrentUsername(authentication), locale));
    }

    @PostMapping("/{gameId}")
    public ResultVO<Boolean> recordRecentView(@PathVariable String gameId, Authentication authentication) {
        return ResultVO.success(
                "Recorded recent view",
                recentViewService.recordRecentView(getCurrentUsername(authentication), gameId)
        );
    }

    private String getCurrentUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized");
        }
        return authentication.getName();
    }
}
