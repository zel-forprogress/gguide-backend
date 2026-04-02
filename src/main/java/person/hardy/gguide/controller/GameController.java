package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import person.hardy.gguide.common.util.LocaleUtil;
import person.hardy.gguide.model.dto.GameDTO;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.GameService;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GameController {

    @Autowired
    private GameService gameService;

    @GetMapping
    public ResultVO<List<GameDTO>> getAllGames(
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        String locale = LocaleUtil.resolveRequestedLocale(lang, acceptLanguage);
        return ResultVO.success(gameService.getAllGames(locale));
    }

    @GetMapping("/category/{category}")
    public ResultVO<List<GameDTO>> getByCategory(
            @PathVariable String category,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        String locale = LocaleUtil.resolveRequestedLocale(lang, acceptLanguage);
        return ResultVO.success(gameService.getByCategory(category, locale));
    }

    @GetMapping("/{id}")
    public ResultVO<GameDTO> getGameById(
            @PathVariable String id,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        String locale = LocaleUtil.resolveRequestedLocale(lang, acceptLanguage);
        return ResultVO.success(gameService.getGameById(id, locale));
    }

    @PostMapping
    public ResultVO<GameDTO> createGame(@RequestBody GameDTO gameDTO) {
        return ResultVO.success("创建成功", gameService.createGame(gameDTO));
    }
}
