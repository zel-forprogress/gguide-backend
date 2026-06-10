package person.hardy.gguide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.GameCategoryCatalog;
import person.hardy.gguide.common.util.GameRegionCatalog;
import person.hardy.gguide.common.util.LocaleUtil;
import person.hardy.gguide.model.dto.AISettingsDTO;
import person.hardy.gguide.model.dto.AISettingsRequestDTO;
import person.hardy.gguide.model.dto.ChatConversationDTO;
import person.hardy.gguide.model.dto.ChatConversationSummaryDTO;
import person.hardy.gguide.model.dto.ChatMessageDTO;
import person.hardy.gguide.model.dto.ChatRequestDTO;
import person.hardy.gguide.model.dto.ChatResponseDTO;
import person.hardy.gguide.model.entity.AIChatConversation;
import person.hardy.gguide.model.entity.Game;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.repository.AIChatConversationRepository;
import person.hardy.gguide.repository.GameRepository;
import person.hardy.gguide.repository.UserRepository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);
    private static final int CONTEXT_LIMIT = 8;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    @Value("${ai.api.url:}")
    private String apiUrl;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.model:}")
    private String model;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private AIChatConversationRepository aiChatConversationRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatResponseDTO chat(String username, ChatRequestDTO request) {
        List<ChatMessageDTO> messages = request.getMessages() == null ? List.of() : request.getMessages();
        String lastUserMessage = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessageDTO::getContent)
                .orElse("");

        User user = findUser(username);
        RecommendationContext recommendationContext = buildRecommendationContext(
                lastUserMessage,
                user,
                request.getContextGameId()
        );
        String systemPrompt = buildSystemPrompt(lastUserMessage, recommendationContext);
        List<ChatMessageDTO> enhancedMessages = buildEnhancedMessages(systemPrompt, messages);

        String response = callAIModel(enhancedMessages, resolveRuntimeSettings(user));
        AIChatConversation conversation = saveConversation(
                username,
                request.getConversationId(),
                messages,
                response
        );

        return new ChatResponseDTO(
                conversation.getId(),
                conversation.getTitle(),
                response,
                conversation.getMessages(),
                conversation.getUpdatedAt()
        );
    }

    public List<ChatConversationSummaryDTO> listConversations(String username) {
        return aiChatConversationRepository.findTop30ByUsernameOrderByUpdatedAtDesc(username).stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());
    }

    public ChatConversationDTO getConversation(String username, String conversationId) {
        AIChatConversation conversation = getOwnedConversation(username, conversationId);
        return new ChatConversationDTO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getMessages(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    public AISettingsDTO getSettings(String username) {
        User user = findUser(username);
        return toSettingsDTO(user);
    }

    public AISettingsDTO updateSettings(String username, AISettingsRequestDTO request) {
        User user = findUser(username);

        if (request == null) {
            return toSettingsDTO(user);
        }

        if (request.isClearApiKey()) {
            user.setAiApiKey(null);
        } else if (hasText(request.getApiKey())) {
            user.setAiApiKey(request.getApiKey().trim());
        }

        user.setAiBaseUrl(normalizeNullableText(request.getBaseUrl()));
        user.setAiModel(normalizeNullableText(request.getModel()));
        return toSettingsDTO(userRepository.save(user));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AISettingsDTO toSettingsDTO(User user) {
        boolean hasUserBaseUrl = hasText(user.getAiBaseUrl());
        boolean hasUserModel = hasText(user.getAiModel());
        return new AISettingsDTO(
                hasText(user.getAiApiKey()),
                maskApiKey(user.getAiApiKey()),
                hasUserBaseUrl ? user.getAiBaseUrl().trim() : normalizeNullableText(apiUrl),
                hasUserModel ? user.getAiModel().trim() : normalizeNullableText(model),
                !hasUserBaseUrl,
                !hasUserModel
        );
    }

    private AIRuntimeSettings resolveRuntimeSettings(User user) {
        return new AIRuntimeSettings(
                firstText(user.getAiApiKey(), apiKey),
                normalizeAIEndpoint(firstText(user.getAiBaseUrl(), apiUrl)),
                firstText(user.getAiModel(), model)
        );
    }

    private String firstText(String primary, String fallback) {
        return hasText(primary) ? primary.trim() : normalizeNullableText(fallback);
    }

    private String normalizeNullableText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String maskApiKey(String value) {
        if (!hasText(value)) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****" + trimmed.substring(Math.max(0, trimmed.length() - 2));
        }

        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private AIChatConversation saveConversation(
            String username,
            String conversationId,
            List<ChatMessageDTO> userMessages,
            String assistantResponse
    ) {
        AIChatConversation conversation = hasText(conversationId)
                ? getOwnedConversation(username, conversationId)
                : createConversation(username, userMessages);

        List<ChatMessageDTO> persistedMessages = new ArrayList<>(userMessages);
        persistedMessages.add(new ChatMessageDTO("assistant", assistantResponse));

        conversation.setMessages(persistedMessages);
        conversation.setUpdatedAt(Instant.now());
        return aiChatConversationRepository.save(conversation);
    }

    private AIChatConversation createConversation(String username, List<ChatMessageDTO> messages) {
        AIChatConversation conversation = new AIChatConversation();
        conversation.setUsername(username);
        conversation.setTitle(buildConversationTitle(messages));
        Instant now = Instant.now();
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return conversation;
    }

    private AIChatConversation getOwnedConversation(String username, String conversationId) {
        AIChatConversation conversation = aiChatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("AI chat conversation not found"));

        if (!username.equals(conversation.getUsername())) {
            throw new RuntimeException("AI chat conversation not found");
        }

        return conversation;
    }

    private ChatConversationSummaryDTO toSummaryDTO(AIChatConversation conversation) {
        int messageCount = conversation.getMessages() == null ? 0 : conversation.getMessages().size();
        return new ChatConversationSummaryDTO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getUpdatedAt(),
                messageCount,
                buildConversationPreview(conversation.getMessages())
        );
    }

    private String buildConversationPreview(List<ChatMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        String preview = messages.stream()
                .map(ChatMessageDTO::getContent)
                .filter(this::hasText)
                .map(content -> content.replaceAll("\\s+", " ").trim())
                .collect(Collectors.joining(" "));

        if (preview.length() <= 160) {
            return preview;
        }

        return preview.substring(0, 160) + "...";
    }

    private String buildConversationTitle(List<ChatMessageDTO> messages) {
        String title = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(ChatMessageDTO::getContent)
                .filter(this::hasText)
                .findFirst()
                .orElse("New chat")
                .replaceAll("\\s+", " ")
                .trim();

        if (title.length() <= 28) {
            return title;
        }

        return title.substring(0, 28) + "...";
    }

    private RecommendationContext buildRecommendationContext(String userMessage, User user, String contextGameId) {
        List<Game> allGames = gameRepository.findAll();
        String normalizedMessage = normalize(userMessage);

        if (!isGameRelatedRequest(normalizedMessage) && !isCurrentGameQuestion(normalizedMessage)) {
            return new RecommendationContext(
                    "general",
                    "用户当前问题不是明确的游戏推荐、游戏资料查询或当前游戏追问。"
            );
        }

        if (allGames.isEmpty()) {
            return new RecommendationContext(
                    "empty",
                    "平台当前还没有可用的游戏数据。如果用户需要游戏推荐，请明确说明暂时无法基于游戏库推荐。"
            );
        }

        Game contextGame = findGameById(contextGameId, allGames);
        List<Game> favoriteGames = resolveGamesByIds(safeGameIds(user.getFavoriteGameIds()), allGames, 5);
        List<Game> recentGames = resolveGamesByIds(safeGameIds(user.getRecentlyViewedGameIds()), allGames, 5);

        if (contextGame != null && isCurrentGameQuestion(normalizedMessage)) {
            List<Game> similarGames = findSimilarGames(contextGame, allGames);
            return new RecommendationContext(
                    "current_game",
                    buildGameContext("用户正在查看的游戏", List.of(contextGame))
                            + buildGameContext("和当前游戏相近的候选游戏", similarGames)
                            + buildUserPreferenceContext(favoriteGames, recentGames)
            );
        }

        List<Game> matchedGames = searchRelevantGames(userMessage, allGames);
        if (!matchedGames.isEmpty()) {
            return new RecommendationContext(
                    "matched",
                    buildGameContext("和用户问题最相关的候选游戏", matchedGames)
                            + buildUserPreferenceContext(favoriteGames, recentGames)
            );
        }

        if (isGenericRecommendationRequest(normalizedMessage)) {
            List<Game> featuredGames = mergeGames(favoriteGames, recentGames, pickFeaturedGames(allGames)).stream()
                    .limit(CONTEXT_LIMIT)
                    .collect(Collectors.toList());
            return new RecommendationContext(
                    "featured",
                    buildGameContext("可用于推荐的候选游戏", featuredGames)
                            + buildUserPreferenceContext(favoriteGames, recentGames)
            );
        }

        return new RecommendationContext(
                "general",
                "用户当前问题不是明确的游戏推荐、游戏资料查询或当前游戏追问。"
        );
    }

    public void streamChat(String username, ChatRequestDTO request, OutputStream outputStream) throws Exception {
        List<ChatMessageDTO> messages = request.getMessages() == null ? List.of() : request.getMessages();
        String lastUserMessage = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessageDTO::getContent)
                .orElse("");

        User user = findUser(username);
        RecommendationContext recommendationContext = buildRecommendationContext(
                lastUserMessage,
                user,
                request.getContextGameId()
        );
        String systemPrompt = buildSystemPrompt(lastUserMessage, recommendationContext);
        List<ChatMessageDTO> enhancedMessages = buildEnhancedMessages(systemPrompt, messages);
        AIRuntimeSettings settings = resolveRuntimeSettings(user);

        if (!hasText(settings.apiKey()) || !hasText(settings.apiUrl()) || !hasText(settings.model())) {
            writeSseEvent(outputStream, "error", Map.of(
                    "message", "AI 助手还没有可用配置。请先在个人设置的 AI 助手管理里填写 API Key、Base URL 和模型名称。"
            ));
            return;
        }

        StringBuilder assistantResponse = new StringBuilder();

        try {
            streamAIModel(enhancedMessages, settings, delta -> {
                assistantResponse.append(delta);
                writeSseEvent(outputStream, "delta", Map.of("content", delta));
            });

            if (assistantResponse.isEmpty()) {
                throw new RuntimeException("AI streaming response was empty");
            }

            AIChatConversation conversation = saveConversation(
                    username,
                    request.getConversationId(),
                    messages,
                    assistantResponse.toString()
            );

            writeSseEvent(outputStream, "done", Map.of(
                    "conversationId", conversation.getId(),
                    "title", conversation.getTitle(),
                    "updatedAt", conversation.getUpdatedAt().toString(),
                    "messages", conversation.getMessages(),
                    "messageCount", conversation.getMessages() == null ? 0 : conversation.getMessages().size()
            ));
        } catch (Exception e) {
            log.error("Failed to stream AI response", e);
            writeSseEvent(outputStream, "error", Map.of(
                    "message", "抱歉，调用 AI 服务时出现问题，请稍后再试。"
            ));
        }
    }

    private List<Game> searchRelevantGames(String userMessage, List<Game> allGames) {
        String normalizedMessage = normalize(userMessage);
        if (normalizedMessage.isBlank()) {
            return List.of();
        }

        return allGames.stream()
                .filter(game -> isGameRelevant(game, normalizedMessage))
                .sorted(Comparator
                        .comparing((Game game) -> computeRelevanceScore(game, normalizedMessage)).reversed()
                        .thenComparing(this::safeRating, Comparator.reverseOrder())
                        .thenComparing(this::safeReleaseDate, Comparator.reverseOrder()))
                .limit(CONTEXT_LIMIT)
                .collect(Collectors.toList());
    }

    private List<Game> pickFeaturedGames(List<Game> allGames) {
        return allGames.stream()
                .sorted(Comparator
                        .comparing(this::safeRating, Comparator.reverseOrder())
                        .thenComparing(this::safeReleaseDate, Comparator.reverseOrder()))
                .limit(CONTEXT_LIMIT)
                .collect(Collectors.toList());
    }

    private boolean isGameRelevant(Game game, String normalizedMessage) {
        return computeRelevanceScore(game, normalizedMessage) > 0;
    }

    private int computeRelevanceScore(Game game, String normalizedMessage) {
        int score = 0;
        String title = normalize(getTitleFromI18n(game.getTitleI18n()));
        String description = normalize(getDescriptionFromI18n(game.getDescriptionI18n()));
        String region = normalize(game.getRegionCode());

        if (!title.isBlank()) {
            if (title.contains(normalizedMessage) || normalizedMessage.contains(title)) {
                score += 6;
            }

            for (String token : splitKeywords(normalizedMessage)) {
                if (token.length() >= 2 && title.contains(token)) {
                    score += 3;
                }
            }
        }

        if (!description.isBlank()) {
            for (String token : splitKeywords(normalizedMessage)) {
                if (token.length() >= 2 && description.contains(token)) {
                    score += 2;
                }
            }
        }

        if (!region.isBlank() && normalizedMessage.contains(region)) {
            score += 2;
        }

        if (game.getCategories() != null) {
            for (String category : game.getCategories()) {
                String normalizedCategory = normalize(category);
                String zhLabel = normalize(GameCategoryCatalog.resolveLabel(category, LocaleUtil.LOCALE_ZH_CN));
                String enLabel = normalize(GameCategoryCatalog.resolveLabel(category, LocaleUtil.LOCALE_EN_US));
                if ((!normalizedCategory.isBlank() && normalizedMessage.contains(normalizedCategory))
                        || (!zhLabel.isBlank() && normalizedMessage.contains(zhLabel))
                        || (!enLabel.isBlank() && normalizedMessage.contains(enLabel))) {
                    score += 3;
                }
            }
        }

        return score;
    }

    private boolean isGenericRecommendationRequest(String normalizedMessage) {
        List<String> genericKeywords = List.of(
                "推荐",
                "有啥",
                "玩什么",
                "有什么",
                "几个游戏",
                "游戏吗",
                "game",
                "recommend",
                "something to play"
        );

        return genericKeywords.stream().anyMatch(normalizedMessage::contains);
    }

    private boolean isGameRelatedRequest(String normalizedMessage) {
        if (normalizedMessage.isBlank()) {
            return false;
        }

        List<String> keywords = List.of(
                "游戏",
                "玩",
                "推荐",
                "适合",
                "好玩",
                "开放世界",
                "动作",
                "冒险",
                "角色扮演",
                "剧情",
                "单人",
                "多人",
                "合作",
                "策略",
                "射击",
                "解谜",
                "休闲",
                "恐怖",
                "生存",
                "模拟",
                "评分",
                "game",
                "play",
                "recommend",
                "rpg",
                "action",
                "adventure",
                "story",
                "open world"
        );

        return keywords.stream().anyMatch(normalizedMessage::contains);
    }

    private boolean isCurrentGameQuestion(String normalizedMessage) {
        if (normalizedMessage.isBlank()) {
            return false;
        }

        List<String> keywords = List.of(
                "这个游戏",
                "这款",
                "它",
                "当前游戏",
                "类似",
                "适合",
                "值得",
                "怎么样",
                "好玩吗",
                "玩法",
                "剧情",
                "评分",
                "下载",
                "预告",
                "介绍",
                "similar",
                "this game",
                "worth",
                "like it"
        );

        return keywords.stream().anyMatch(normalizedMessage::contains);
    }

    private List<String> splitKeywords(String normalizedMessage) {
        return List.of(normalizedMessage.split("[\\s,，。?!！？/]+"));
    }

    private String formatGameList(String title, List<Game> games) {
        String gameDetails = games.stream()
                .map(this::formatGameInfo)
                .collect(Collectors.joining("\n\n"));

        return title + "\n\n" + gameDetails;
    }

    private String buildGameContext(String title, List<Game> games) {
        if (games == null || games.isEmpty()) {
            return "";
        }

        return formatGameList(title + "：", games) + "\n\n";
    }

    private String buildUserPreferenceContext(List<Game> favoriteGames, List<Game> recentGames) {
        StringBuilder builder = new StringBuilder();

        if (favoriteGames != null && !favoriteGames.isEmpty()) {
            builder.append(buildGameContext("用户收藏过的游戏，可用于理解偏好", favoriteGames));
        }

        if (recentGames != null && !recentGames.isEmpty()) {
            builder.append(buildGameContext("用户最近浏览过的游戏，可用于理解当前兴趣", recentGames));
        }

        if (builder.isEmpty()) {
            builder.append("用户暂时没有收藏或最近浏览记录。\n\n");
        }

        return builder.toString();
    }

    private List<Game> resolveGamesByIds(List<String> ids, List<Game> allGames, int limit) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<String, Game> gameMap = allGames.stream()
                .collect(Collectors.toMap(Game::getId, game -> game, (left, right) -> left));

        return ids.stream()
                .map(gameMap::get)
                .filter(game -> game != null)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<String> safeGameIds(List<String> gameIds) {
        return gameIds == null ? List.of() : gameIds;
    }

    private Game findGameById(String gameId, List<Game> allGames) {
        if (!hasText(gameId)) {
            return null;
        }

        return allGames.stream()
                .filter(game -> gameId.equals(game.getId()))
                .findFirst()
                .orElse(null);
    }

    @SafeVarargs
    private final List<Game> mergeGames(List<Game>... groups) {
        Set<String> seenIds = new LinkedHashSet<>();
        List<Game> merged = new ArrayList<>();

        for (List<Game> group : groups) {
            if (group == null) {
                continue;
            }

            for (Game game : group) {
                if (game == null || !hasText(game.getId()) || !seenIds.add(game.getId())) {
                    continue;
                }
                merged.add(game);
            }
        }

        return merged;
    }

    private List<Game> findSimilarGames(Game sourceGame, List<Game> allGames) {
        if (sourceGame == null || sourceGame.getCategories() == null || sourceGame.getCategories().isEmpty()) {
            return List.of();
        }

        Set<String> sourceCategories = new LinkedHashSet<>(sourceGame.getCategories());
        return allGames.stream()
                .filter(game -> !sourceGame.getId().equals(game.getId()))
                .filter(game -> game.getCategories() != null && game.getCategories().stream().anyMatch(sourceCategories::contains))
                .sorted(Comparator
                        .comparing((Game game) -> countCategoryOverlap(sourceCategories, game.getCategories())).reversed()
                        .thenComparing(this::safeRating, Comparator.reverseOrder())
                        .thenComparing(this::safeReleaseDate, Comparator.reverseOrder()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private int countCategoryOverlap(Set<String> sourceCategories, List<String> categories) {
        if (categories == null) {
            return 0;
        }

        int count = 0;
        for (String category : categories) {
            if (sourceCategories.contains(category)) {
                count++;
            }
        }
        return count;
    }

    private String formatGameInfo(Game game) {
        StringBuilder builder = new StringBuilder();
        builder.append("游戏名：").append(getTitleFromI18n(game.getTitleI18n()));

        if (game.getRating() != null) {
            builder.append("\n评分：").append(game.getRating()).append("/10");
        }

        if (game.getCategories() != null && !game.getCategories().isEmpty()) {
            builder.append("\n分类：").append(game.getCategories().stream()
                    .map(category -> GameCategoryCatalog.resolveLabel(category, LocaleUtil.LOCALE_ZH_CN))
                    .collect(Collectors.joining(", ")));
        }

        if (game.getRegionCode() != null && !game.getRegionCode().isBlank() && !"UNKNOWN".equals(game.getRegionCode())) {
            builder.append("\n地区：").append(GameRegionCatalog.resolveLabel(game.getRegionCode(), LocaleUtil.LOCALE_ZH_CN));
        }

        if (game.getReleaseDate() != null) {
            builder.append("\n发售时间：").append(formatInstant(game.getReleaseDate()));
        }

        String description = getDescriptionFromI18n(game.getDescriptionI18n());
        if (!description.isBlank()) {
            builder.append("\n简介：").append(description);
        }

        return builder.toString();
    }

    private String buildSystemPrompt(String lastUserMessage, RecommendationContext recommendationContext) {
        String basePrompt = """
                你是 G-Guide 平台的 AI 游戏助手。

                回答规则：
                1. 只基于下方“平台数据和用户上下文”回答游戏推荐或游戏资料问题，不要编造平台里没有的游戏。
                2. 如果用户问具体游戏，就围绕对应游戏直接回答；如果用户要推荐，请给出 3 到 5 个游戏，并说明推荐理由。
                3. 如果有用户收藏或最近浏览记录，可以用它们推断偏好，但不要把这些记录当成用户明确说过的话。
                4. 如果用户的问题其实不是游戏相关问题，请直接自然回答，不要强行推荐游戏。
                5. 使用中文回答，语气自然、友好，重点说明适合谁玩、亮点是什么、为什么推荐。
                6. 排版保持简洁：优先使用短段落；如需列表，只使用 Markdown 的 "- "，每个要点单独一行。
                7. 不要使用 ◆、◇、•、· 这类装饰符号，不要把多个列表项写在同一行。

                用户最后一句话：
                %s

                平台数据和用户上下文：
                %s

                当前模式：
                %s
                """;

        String generalPrompt = """
                你是 G-Guide 平台的 AI 助手。

                当前问题没有被识别为游戏推荐、游戏资料查询或当前游戏追问。

                回答规则：
                1. 直接回答用户问题，不要强行转成游戏推荐。
                2. 用户明确问到游戏、推荐、收藏、最近浏览或当前游戏时，才围绕 G-Guide 的游戏内容回答。
                3. 使用中文回答，语气自然、友好。
                4. 排版保持简洁：优先使用短段落；如需列表，只使用 Markdown 的 "- "，每个要点单独一行。
                5. 不要使用 ◆、◇、•、· 这类装饰符号，不要把多个列表项写在同一行。

                用户最后一句话：
                %s

                当前模式：
                %s
                """;

        if ("general".equals(recommendationContext.mode())) {
            return generalPrompt.formatted(
                    hasText(lastUserMessage) ? lastUserMessage : "用户没有提供额外问题",
                    recommendationContext.mode()
            );
        }

        return basePrompt.formatted(
                hasText(lastUserMessage) ? lastUserMessage : "用户没有提供额外问题",
                recommendationContext.context(),
                recommendationContext.mode()
        );
    }

    private List<ChatMessageDTO> buildEnhancedMessages(String systemPrompt, List<ChatMessageDTO> messages) {
        List<ChatMessageDTO> enhancedMessages = new ArrayList<>();
        enhancedMessages.add(new ChatMessageDTO("system", systemPrompt));
        enhancedMessages.addAll(messages);
        return enhancedMessages;
    }

    private String callAIModel(List<ChatMessageDTO> messages, AIRuntimeSettings settings) {
        if (!hasText(settings.apiKey()) || !hasText(settings.apiUrl()) || !hasText(settings.model())) {
            return "AI 助手还没有可用配置。请先在个人设置的 AI 助手管理里填写 API Key、Base URL 和模型名称。";
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            String requestBody = buildRequestBody(messages, settings.model(), false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.apiUrl()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseAIResponse(response.body());
            }

            log.error("AI API call failed: {} - {}", response.statusCode(), response.body());
            return "抱歉，AI 服务暂时不可用，请稍后再试。";
        } catch (Exception e) {
            log.error("Failed to call AI model", e);
            return "抱歉，调用 AI 服务时出现问题，请稍后再试。";
        }
    }

    private void streamAIModel(
            List<ChatMessageDTO> messages,
            AIRuntimeSettings settings,
            StreamDeltaHandler deltaHandler
    ) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        String requestBody = buildRequestBody(messages, settings.model(), true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.apiUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("X-DashScope-SSE", "enable")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            log.error("AI streaming API call failed: {} - {}", response.statusCode(), body);
            throw new RuntimeException("AI streaming API call failed");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring("data:".length()).trim();
                if (data.isBlank()) {
                    continue;
                }

                if ("[DONE]".equals(data)) {
                    break;
                }

                String delta = parseAIStreamDelta(data);
                if (hasText(delta)) {
                    deltaHandler.accept(delta);
                }
            }
        }
    }

    private String normalizeAIEndpoint(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = value.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }

        return normalized + "/chat/completions";
    }

    private String buildRequestBody(List<ChatMessageDTO> messages, String activeModel, boolean stream) throws Exception {
        List<Map<String, String>> payloadMessages = messages.stream()
                .map(message -> {
                    Map<String, String> payloadMessage = new LinkedHashMap<>();
                    payloadMessage.put("role", firstText(message.getRole(), "user"));
                    payloadMessage.put("content", firstText(message.getContent(), ""));
                    return payloadMessage;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", activeModel);
        payload.put("messages", payloadMessages);
        if (stream) {
            payload.put("stream", true);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String parseAIResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                log.error("AI response did not include choices[0].message.content: {}", responseBody);
                return "无法解析 AI 响应。";
            }

            return content.asText();
        } catch (Exception e) {
            log.error("Failed to parse AI response", e);
            return "解析 AI 响应时发生错误。";
        }
    }

    private String parseAIStreamDelta(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            JsonNode content = choice.path("delta").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText();
            }

            JsonNode messageContent = choice.path("message").path("content");
            if (!messageContent.isMissingNode() && !messageContent.isNull()) {
                return messageContent.asText();
            }

            return "";
        } catch (Exception e) {
            log.warn("Failed to parse AI stream chunk: {}", responseBody, e);
            return "";
        }
    }

    private void writeSseEvent(OutputStream outputStream, String event, Object payload) throws Exception {
        String data = objectMapper.writeValueAsString(payload);
        String eventText = "event: " + event + "\n" + "data: " + data + "\n\n";
        outputStream.write(eventText.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private String getTitleFromI18n(Map<String, String> titleI18n) {
        if (titleI18n == null || titleI18n.isEmpty()) {
            return "未知游戏";
        }

        String zhTitle = titleI18n.get(LocaleUtil.LOCALE_ZH_CN);
        if (zhTitle != null && !zhTitle.isBlank()) {
            return zhTitle;
        }

        return titleI18n.values().stream().findFirst().orElse("未知游戏");
    }

    private String getDescriptionFromI18n(Map<String, String> descriptionI18n) {
        if (descriptionI18n == null || descriptionI18n.isEmpty()) {
            return "";
        }

        String zhDescription = descriptionI18n.get(LocaleUtil.LOCALE_ZH_CN);
        if (zhDescription != null && !zhDescription.isBlank()) {
            return zhDescription;
        }

        return descriptionI18n.values().stream().findFirst().orElse("");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private Double safeRating(Game game) {
        return game.getRating() == null ? 0D : game.getRating();
    }

    private Instant safeReleaseDate(Game game) {
        return game.getReleaseDate() == null ? Instant.EPOCH : game.getReleaseDate();
    }

    private String formatInstant(Instant instant) {
        return DATE_FORMATTER.format(instant);
    }

    private record RecommendationContext(String mode, String context) {
    }

    private record AIRuntimeSettings(String apiKey, String apiUrl, String model) {
    }

    @FunctionalInterface
    private interface StreamDeltaHandler {
        void accept(String delta) throws Exception;
    }
}
