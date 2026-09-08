package com.lifeforge.service;

import com.lifeforge.config.AppConfig;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.RecommendationCategory;
import com.lifeforge.model.User;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiExplanationService implements RecommendationExplanationService {

    private final RuleBasedExplanationService fallback =
            new RuleBasedExplanationService();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public boolean isAvailable() {
        return AppConfig.isAiEnabled();
    }

    @Override
    public ExplanationOutcome explain(
            User user,
            Goal goal,
            ActivityLevel activityLevel,
            Recommendation recommendation) {

        if (!isAvailable()) {
            System.out.println("[LifeForge AI] AI is disabled.");
            return fallback.explain(user, goal, activityLevel, recommendation);
        }

        try {
            String prompt = buildPrompt(
                    user,
                    goal,
                    activityLevel,
                    recommendation
            );

            String response = callOllama(prompt);

            if (response == null || response.isBlank()) {
                System.err.println(
                        "[LifeForge AI] Ollama returned an empty response."
                );

                return fallback.explain(
                        user,
                        goal,
                        activityLevel,
                        recommendation
                );
            }

            System.out.println(
                    "[LifeForge AI] AI explanation generated successfully."
            );

            return new ExplanationOutcome(
                    response.trim(),
                    true
            );

        } catch (Exception e) {

            System.err.println(
                    "[LifeForge AI] AI request failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            return fallback.explain(
                    user,
                    goal,
                    activityLevel,
                    recommendation
            );
        }
    }

    /**
     * Answers an optional user question using the current LifeForge context.
     * It cannot select, replace, edit, or persist a recommendation. Failures
     * return a clean, local fallback so the TUI remains usable offline.
     */
    public AiChatResponse chat(User user, Goal goal, RecommendationCategory category,
                               Recommendation recommendation, List<String> recentConversation,
                               String question) {
        if (question == null || question.isBlank()) {
            return new AiChatResponse("Please enter a question for the AI assistant.", false);
        }
        if (!isAvailable()) {
            return chatFallback(recommendation);
        }
        try {
            String response = callOllama(buildChatPrompt(
                    user, goal, category, recommendation, recentConversation, question));
            if (response == null || response.isBlank()) {
                return chatFallback(recommendation);
            }
            return new AiChatResponse(response.trim(), true);
        } catch (Exception e) {
            System.err.println("[LifeForge AI] Chat request failed: "
                    + e.getClass().getSimpleName());
            return chatFallback(recommendation);
        }
    }

    private AiChatResponse chatFallback(Recommendation recommendation) {
        String title = recommendation == null || recommendation.getTitle() == null
                ? "your current LifeForge recommendation" : "\"" + recommendation.getTitle() + "\"";
        return new AiChatResponse(
                "AI is currently unavailable. Your official LifeForge recommendation, "
                        + title + ", is still available. Please use its recommended actions "
                        + "and important notes as general lifestyle guidance.", false);
    }

    private String buildChatPrompt(User user, Goal goal, RecommendationCategory category,
                                   Recommendation recommendation, List<String> recentConversation,
                                   String question) {
        StringBuilder sb = new StringBuilder("""
                You are the LIFEForge AI assistant, part of LIFEForge, a personalized
                health and lifestyle RECOMMENDATION system.

                Answer the user's SPECIFIC question directly, using the context below.
                Be concise, practical, contextual, and directly responsive.

                HARD RULES — MUST FOLLOW:
                - The official LIFEForge recommendation is deterministic and authoritative.
                - Do NOT create, replace, alter, or rank recommendations.
                - Do NOT invent profile data, measurements, targets, diagnoses, medication,
                  or unsupported medical claims.
                - Do NOT invent precise nutrition quantities. Do NOT claim that a specific
                  food, meal, or combination will provide an exact amount of protein,
                  calories, or other nutrients. LIFEForge does not calculate per-food macros.
                  Use qualitative and practical guidance instead (e.g. "protein-rich",
                  "low-fat", "spread protein across meals") without exact gram or calorie
                  figures that LIFEForge has not computed.
                - LIFEForge is a recommendation system, NOT a tracking app. It does not
                  track calories eaten, protein eaten, meals, water consumed, or daily habits.
                  Do NOT instruct the user to "monitor", "log", or "track" their daily intake.
                - Understand conversational context. Pronouns like "it", "this", "that",
                  or "the target" may refer to protein, the recommendation, or a goal
                  mentioned earlier in the conversation.

                RESPONSE STYLE:
                - Answer the question FIRST, then add a short practical explanation.
                - Keep responses concise: roughly 3-8 short paragraphs or bullet points,
                  matching the complexity of the question. Simple questions get simple answers.
                - Use bullets when they make the answer easier to scan.
                - Do not restate the entire recommendation.
                - Do not add unnecessary greetings or filler.
                - The official LIFEForge target remains the source of truth; you may
                  reference it, but do not change it.

                USER PROFILE:
                """);
        sb.append("Age: ").append(user.getAge()).append('\n')
                .append("Gender: ").append(user.getGender()).append('\n')
                .append("Height: ").append(user.getHeightCm()).append(" cm\n")
                .append("Weight: ").append(user.getWeightKg()).append(" kg\n")
                .append("Activity Level: ").append(user.getActivityLevel()).append("\n\n")
                .append("CURRENT GOAL:\n").append(goal.getName()).append("\n\n")
                .append("CURRENT CATEGORY:\n")
                .append(category == null ? "Not specified" : category.getName()).append("\n\n")
                .append("CURRENT LIFEFORGE RECOMMENDATION:\n");
        appendIfPresent(sb, "Title: ", recommendation.getTitle());
        appendIfPresent(sb, "Description: ", recommendation.getDescription());
        appendIfPresent(sb, "Recommended Actions: ", recommendation.getRecommendedActions());
        appendIfPresent(sb, "Suggested Target: ", recommendation.getSuggestedTarget());
        appendIfPresent(sb, "Examples: ", recommendation.getExamples());
        appendIfPresent(sb, "Important Notes: ", recommendation.getImportantNotes());
        if (recentConversation != null && !recentConversation.isEmpty()) {
            sb.append("\nRECENT CONVERSATION:\n");
            int start = Math.max(0, recentConversation.size() - 6);
            for (int i = start; i < recentConversation.size(); i++) {
                sb.append(recentConversation.get(i)).append('\n');
            }
        }
        sb.append("\nUSER QUESTION:\n").append(question.trim())
                .append("\n\nTASK:\nAnswer the user's question directly and concisely, "
                        + "without changing the official recommendation.");
        return sb.toString();
    }

    private String buildPrompt(
            User user,
            Goal goal,
            ActivityLevel activityLevel,
            Recommendation recommendation) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are the AI explanation assistant for LifeForge,
                a personalized health and lifestyle recommendation system.

                IMPORTANT:
                - Do NOT create a new recommendation.
                - Do NOT change the existing recommendation.
                - Explain WHY the existing recommendation fits the user.
                - Use only the information provided below.
                - Do not invent medical diagnoses.
                - Do not provide dangerous or extreme dieting advice.
                - Keep the answer friendly and concise.
                - Write exactly 2-3 sentences.

                USER PROFILE:
                """);

        sb.append("- Age: ")
                .append(user.getAge())
                .append("\n");

        sb.append("- Gender: ")
                .append(user.getGender())
                .append("\n");

        sb.append("- Height: ")
                .append(user.getHeightCm())
                .append(" cm\n");

        sb.append("- Weight: ")
                .append(user.getWeightKg())
                .append(" kg\n");

        sb.append("- Activity Level: ")
                .append(activityLevel)
                .append("\n\n");

        sb.append("SELECTED GOAL:\n");
        sb.append(goal.getName())
                .append("\n\n");

        sb.append("EXISTING RECOMMENDATION:\n");

        appendIfPresent(
                sb,
                "- Title: ",
                recommendation.getTitle()
        );

        appendIfPresent(
                sb,
                "- Description: ",
                recommendation.getDescription()
        );

        appendIfPresent(
                sb,
                "- Recommended Actions: ",
                recommendation.getRecommendedActions()
        );

        appendIfPresent(
                sb,
                "- Suggested Target: ",
                recommendation.getSuggestedTarget()
        );

        appendIfPresent(
                sb,
                "- Examples: ",
                recommendation.getExamples()
        );

        appendIfPresent(
                sb,
                "- Important Notes: ",
                recommendation.getImportantNotes()
        );

        sb.append("""

                TASK:
                Explain why this existing recommendation is appropriate
                for this specific user.
                """);

        return sb.toString();
    }

    private void appendIfPresent(
            StringBuilder sb,
            String label,
            String value) {

        if (value != null && !value.isBlank()) {
            sb.append(label)
                    .append(value)
                    .append("\n");
        }
    }

    private String callOllama(String prompt) throws Exception {

        String baseUrl = AppConfig.getOllamaBaseUrl();
        String model = AppConfig.getOllamaModel();

        System.out.println(
                "[LifeForge AI] URL: " + baseUrl
        );

        System.out.println(
                "[LifeForge AI] Model: " + model
        );

        String requestBody =
                "{"
                        + "\"model\":" + jsonString(model) + ","
                        + "\"prompt\":" + jsonString(prompt) + ","
                        + "\"stream\":false"
                        + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .timeout(Duration.ofSeconds(120))
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                requestBody
                        )
                )
                .build();

        System.out.println(
                "[LifeForge AI] Calling Ollama..."
        );

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        System.out.println(
                "[LifeForge AI] HTTP Status: "
                        + response.statusCode()
        );

        if (response.statusCode() != 200) {

            System.err.println(
                    "[LifeForge AI] Ollama error body:"
            );

            System.err.println(
                    response.body()
            );

            return null;
        }

        String body = response.body();

        System.out.println(
                "[LifeForge AI] Ollama response received."
        );

        String extracted =
                extractJsonStringField(
                        body,
                        "response"
                );

        if (extracted == null) {

            System.err.println(
                    "[LifeForge AI] Could not extract "
                            + "\"response\" from Ollama JSON."
            );

            System.err.println(
                    "[LifeForge AI] Raw response:"
            );

            System.err.println(body);

            return null;
        }

        return extracted;
    }

    private String jsonString(String value) {

        if (value == null) {
            return "\"\"";
        }

        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + "\"";
    }

    /**
     * Extracts a JSON string field while allowing whitespace
     * around the colon.
     */
    private String extractJsonStringField(
            String json,
            String field) {

        if (json == null || json.isBlank()) {
            return null;
        }

        String regex =
                "\"" + Pattern.quote(field)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";

        Pattern pattern =
                Pattern.compile(regex);

        Matcher matcher =
                pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return unescapeJsonString(
                matcher.group(1)
        );
    }

    private String unescapeJsonString(String value) {

        StringBuilder result =
                new StringBuilder();

        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (!escaping) {

                if (c == '\\') {
                    escaping = true;
                } else {
                    result.append(c);
                }

                continue;
            }

            switch (c) {

                case 'n' ->
                        result.append('\n');

                case 'r' ->
                        result.append('\r');

                case 't' ->
                        result.append('\t');

                case 'b' ->
                        result.append('\b');

                case 'f' ->
                        result.append('\f');

                case '"' ->
                        result.append('"');

                case '\\' ->
                        result.append('\\');

                case '/' ->
                        result.append('/');

                default ->
                        result.append(c);
            }

            escaping = false;
        }

        if (escaping) {
            result.append('\\');
        }

        return result.toString();
    }
}
