package person.hardy.gguide.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.LocaleUtil;
import person.hardy.gguide.model.dto.ChatConversationDTO;
import person.hardy.gguide.model.dto.ChatConversationSummaryDTO;
import person.hardy.gguide.model.dto.ChatMessageDTO;
import person.hardy.gguide.model.dto.ChatRequestDTO;
import person.hardy.gguide.model.dto.ChatResponseDTO;
import person.hardy.gguide.model.entity.AIChatConversation;
import person.hardy.gguide.model.entity.Game;
import person.hardy.gguide.repository.AIChatConversationRepository;
import person.hardy.gguide.repository.GameRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);
    private static final int CONTEXT_LIMIT = 8;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private AIChatConversationRepository aiChatConversationRepository;

    public ChatResponseDTO chat(String username, ChatRequestDTO request) {
        List<ChatMessageDTO> messages = request.getMessages();
        String lastUserMessage = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessageDTO::getContent)
                .orElse("");

        RecommendationContext recommendationContext = buildRecommendationContext(lastUserMessage);
        String systemPrompt = buildSystemPrompt(lastUserMessage, recommendationContext);
        List<ChatMessageDTO> enhancedMessages = buildEnhancedMessages(systemPrompt, messages);

        String response = callAIModel(enhancedMessages);
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
                messageCount
        );
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private RecommendationContext buildRecommendationContext(String userMessage) {
        List<Game> allGames = gameRepository.findAll();

        if (allGames.isEmpty()) {
            return new RecommendationContext(
                    "empty",
                    "平台当前还没有可用的游戏数据，请明确告诉用户暂时无法基于游戏库做推荐。"
            );
        }

        List<Game> matchedGames = searchRelevantGames(userMessage, allGames);
        if (!matchedGames.isEmpty()) {
            return new RecommendationContext(
                    "matched",
                    formatGameList("以下是和用户问题最相关的候选游戏，请优先基于这些内容回答：", matchedGames)
            );
        }

        List<Game> featuredGames = pickFeaturedGames(allGames);
        return new RecommendationContext(
                "featured",
                formatGameList("用户暂时没有提供明确偏好，请基于下面这些平台精选游戏给出 3 到 5 个入门推荐：", featuredGames)
        );
    }

    private List<Game> searchRelevantGames(String userMessage, List<Game> allGames) {
        String normalizedMessage = normalize(userMessage);
        if (normalizedMessage.isBlank() || isGenericRecommendationRequest(normalizedMessage)) {
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

        for (String category : game.getCategories()) {
            String normalizedCategory = normalize(category);
            if (!normalizedCategory.isBlank() && normalizedMessage.contains(normalizedCategory)) {
                score += 3;
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
                "游戏吧",
                "game",
                "recommend",
                "something to play"
        );

        return genericKeywords.stream().anyMatch(normalizedMessage::contains);
    }

    private List<String> splitKeywords(String normalizedMessage) {
        return List.of(normalizedMessage.split("[\\s,，。.!！？/]+"));
    }

    private String formatGameList(String title, List<Game> games) {
        String gameDetails = games.stream()
                .map(this::formatGameInfo)
                .collect(Collectors.joining("\n\n"));

        return title + "\n\n" + gameDetails;
    }

    private String formatGameInfo(Game game) {
        StringBuilder builder = new StringBuilder();
        builder.append("游戏名: ").append(getTitleFromI18n(game.getTitleI18n()));

        if (game.getRating() != null) {
            builder.append("\n评分: ").append(game.getRating()).append("/10");
        }

        if (game.getCategories() != null && !game.getCategories().isEmpty()) {
            builder.append("\n分类: ").append(String.join(", ", game.getCategories()));
        }

        if (game.getRegionCode() != null && !game.getRegionCode().isBlank() && !"UNKNOWN".equals(game.getRegionCode())) {
            builder.append("\n地区: ").append(game.getRegionCode());
        }

        if (game.getReleaseDate() != null) {
            builder.append("\n发售时间: ").append(formatInstant(game.getReleaseDate()));
        }

        String description = getDescriptionFromI18n(game.getDescriptionI18n());
        if (!description.isBlank()) {
            builder.append("\n简介: ").append(description);
        }

        return builder.toString();
    }

    private String buildSystemPrompt(String lastUserMessage, RecommendationContext recommendationContext) {
        return """
                你是 G-Guide 平台的 AI 游戏助手。
                你的任务是只基于平台提供的真实游戏数据，为用户给出可靠、简洁、有帮助的推荐和说明。

                回答规则：
                1. 只能推荐下方数据里真实存在的游戏，不要编造平台里没有的作品。
                2. 优先直接回答用户问题；如果用户只是泛泛地求推荐，请主动给出 3 到 5 个推荐，并说明推荐理由。
                3. 当用户没有浏览历史、收藏记录或明确偏好时，不要说“无法推荐”；你应该从平台精选游戏中挑选合适的作品给出入门建议。
                4. 如果平台当前确实没有任何可用游戏数据，再明确告诉用户暂无数据。
                5. 使用中文回答，语气自然、友好，尽量突出“适合谁玩、亮点是什么、为什么推荐”。

                用户最后一句话：
                %s

                可用游戏平台数据：
                %s

                当前推荐模式：%s
                """.formatted(
                lastUserMessage == null || lastUserMessage.isBlank() ? "用户没有提供额外问题" : lastUserMessage,
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

    private String callAIModel(List<ChatMessageDTO> messages) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .build();

            String requestBody = buildRequestBody(messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseAIResponse(response.body());
            }

            log.error("AI API调用失败: {} - {}", response.statusCode(), response.body());
            return "抱歉，AI 服务暂时不可用，请稍后再试。";
        } catch (Exception e) {
            log.error("调用 AI 模型时发生异常", e);
            return "抱歉，调用 AI 服务时出现问题，请稍后再试。";
        }
    }

    private String buildRequestBody(List<ChatMessageDTO> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"model\":\"").append(model).append("\",\"messages\":[");

        for (int index = 0; index < messages.size(); index++) {
            ChatMessageDTO message = messages.get(index);
            builder.append("{\"role\":\"").append(message.getRole()).append("\",\"content\":\"")
                    .append(escapeJson(message.getContent())).append("\"}");
            if (index < messages.size() - 1) {
                builder.append(",");
            }
        }

        builder.append("]}");
        return builder.toString();
    }

    private String parseAIResponse(String responseBody) {
        try {
            int choicesIndex = responseBody.indexOf("\"choices\"");
            if (choicesIndex == -1) {
                return "无法解析 AI 响应";
            }

            int messageIndex = responseBody.indexOf("\"message\"", choicesIndex);
            int contentIndex = responseBody.indexOf("\"content\"", messageIndex);
            int colonIndex = responseBody.indexOf(":", contentIndex);
            int quoteStart = responseBody.indexOf("\"", colonIndex) + 1;
            int quoteEnd = findClosingQuote(responseBody, quoteStart);

            return responseBody.substring(quoteStart, quoteEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
        } catch (Exception e) {
            log.error("解析 AI 响应失败", e);
            return "解析 AI 响应时发生错误";
        }
    }

    private int findClosingQuote(String text, int start) {
        int index = start;
        while (index < text.length()) {
            if (text.charAt(index) == '"' && (index == 0 || text.charAt(index - 1) != '\\')) {
                return index;
            }
            index++;
        }
        return text.length();
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
}
