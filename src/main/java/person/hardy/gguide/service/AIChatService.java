package person.hardy.gguide.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.LocaleUtil;
import person.hardy.gguide.model.dto.ChatMessageDTO;
import person.hardy.gguide.model.entity.Game;
import person.hardy.gguide.repository.GameRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    @Value("${ai.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Autowired
    private GameRepository gameRepository;

    //
    public String chat(List<ChatMessageDTO> messages) {
        String lastUserMessage = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessageDTO::getContent)
                .orElse("");

        String context = buildContextFromDatabase(lastUserMessage);
        String systemPrompt = buildSystemPrompt(context);

        List<ChatMessageDTO> enhancedMessages = buildEnhancedMessages(systemPrompt, messages);

        return callAIModel(enhancedMessages);
    }

    private String buildContextFromDatabase(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "";
        }

        List<Game> relevantGames = searchRelevantGames(userMessage);

        if (relevantGames.isEmpty()) {
            return "当前数据库中没有找到相关游戏。";
        }

        return relevantGames.stream()
                .map(this::formatGameInfo)
                .collect(Collectors.joining("\n\n"));
    }

    private List<Game> searchRelevantGames(String userMessage) {
        List<Game> allGames = gameRepository.findAll();

        String lowerMessage = userMessage.toLowerCase();

        return allGames.stream()
                .filter(game -> isGameRelevant(game, lowerMessage))
                .limit(10)
                .collect(Collectors.toList());
    }

    private boolean isGameRelevant(Game game, String lowerMessage) {
        String title = getTitleFromI18n(game.getTitleI18n()).toLowerCase();
        String description = getDescriptionFromI18n(game.getDescriptionI18n()).toLowerCase();
        List<String> categories = game.getCategories();

        return title.contains(lowerMessage)
                || description.contains(lowerMessage)
                || categories.stream().anyMatch(lowerMessage::contains)
                || lowerMessage.contains(title);
    }

    private String formatGameInfo(Game game) {
        String title = getTitleFromI18n(game.getTitleI18n());
        String description = getDescriptionFromI18n(game.getDescriptionI18n());
        Double rating = game.getRating();
        List<String> categories = game.getCategories();
        String region = game.getRegionCode();

        StringBuilder sb = new StringBuilder();
        sb.append("游戏名称: ").append(title);
        if (rating != null) {
            sb.append("\n评分: ").append(rating).append("/10");
        }
        if (!categories.isEmpty()) {
            sb.append("\n分类: ").append(String.join(", ", categories));
        }
        if (region != null && !region.equals("UNKNOWN")) {
            sb.append("\n地区: ").append(region);
        }
        if (!description.isBlank()) {
            sb.append("\n简介: ").append(description);
        }

        return sb.toString();
    }

    private String getTitleFromI18n(java.util.Map<String, String> titleI18n) {
        if (titleI18n == null || titleI18n.isEmpty()) {
            return "未知游戏";
        }
        String zhTitle = titleI18n.get(LocaleUtil.LOCALE_ZH_CN);
        if (zhTitle != null && !zhTitle.isBlank()) {
            return zhTitle;
        }
        return titleI18n.values().stream().findFirst().orElse("未知游戏");
    }

    private String getDescriptionFromI18n(java.util.Map<String, String> descriptionI18n) {
        if (descriptionI18n == null || descriptionI18n.isEmpty()) {
            return "";
        }
        String zhDesc = descriptionI18n.get(LocaleUtil.LOCALE_ZH_CN);
        if (zhDesc != null && !zhDesc.isBlank()) {
            return zhDesc;
        }
        return descriptionI18n.values().stream().findFirst().orElse("");
    }

    private String buildSystemPrompt(String context) {
        return """
                你是一个专业游戏导航平台 G-Guide 的AI助手。你的任务是基于平台中真实的游戏数据为用户提供准确的推荐和建议。
                
                【重要规则】
                1. 你只能推荐以下提供的游戏列表中的游戏，不要编造不存在的内容
                2. 回答要简洁专业，突出重点信息（名称、评分、特色）
                3. 如果用户询问的游戏不在列表中，请如实告知并推荐相似类型的游戏
                4. 使用中文回答（除非用户明确要求其他语言）
                
                【可用游戏数据】
                %s
                
                请基于以上数据回答用户问题。
                """.formatted(context);
    }

    private List<ChatMessageDTO> buildEnhancedMessages(String systemPrompt, List<ChatMessageDTO> messages) {
        List<ChatMessageDTO> enhanced = new java.util.ArrayList<>();
        enhanced.add(new ChatMessageDTO("system", systemPrompt));
        enhanced.addAll(messages);
        return enhanced;
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
            } else {
                log.error("AI API调用失败: {} - {}", response.statusCode(), response.body());
                return "抱歉，AI服务暂时不可用，请稍后再试。";
            }
        } catch (Exception e) {
            log.error("调用AI模型时发生异常", e);
            return "抱歉，发生了错误，请稍后再试。";
        }
    }

    private String buildRequestBody(List<ChatMessageDTO> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(model).append("\",\"messages\":[");

        for (int i = 0; i < messages.size(); i++) {
            ChatMessageDTO msg = messages.get(i);
            sb.append("{\"role\":\"").append(msg.getRole()).append("\",\"content\":\"")
                    .append(escapeJson(msg.getContent())).append("\"}");
            if (i < messages.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]}");
        return sb.toString();
    }

    private String parseAIResponse(String responseBody) {
        try {
            int choicesIndex = responseBody.indexOf("\"choices\"");
            if (choicesIndex == -1) {
                return "无法解析AI响应";
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
            log.error("解析AI响应失败", e);
            return "解析AI响应时发生错误";
        }
    }

    private int findClosingQuote(String str, int start) {
        int i = start;
        while (i < str.length()) {
            if (str.charAt(i) == '"' && (i == 0 || str.charAt(i - 1) != '\\')) {
                return i;
            }
            i++;
        }
        return str.length();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
