package com.lifeforge.tui4j;

import com.lifeforge.model.Goal;
import com.lifeforge.model.User;
import com.lifeforge.service.AiChatResponse;
import com.lifeforge.service.AiExplanationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KhmerUnicodeAndBorderTest {

    @Test
    public void testKhmerCharacterDisplayWidths() {
        // Base consonants: 1 column
        assertEquals(1, Theme.displayWidth(0x1780)); // KA ក
        assertEquals(1, Theme.displayWidth(0x1785)); // CA ច
        assertEquals(1, Theme.displayWidth(0x178F)); // TA ត

        // Spacing combining vowels: 1 column (occupy horizontal cursor space)
        assertEquals(1, Theme.displayWidth(0x17B6)); // Vowel AA ា
        assertEquals(1, Theme.displayWidth(0x17C1)); // Vowel E េ
        assertEquals(1, Theme.displayWidth(0x17C4)); // Vowel OO ោ
        assertEquals(1, Theme.displayWidth(0x17C2)); // Vowel AE ែ

        // Non-spacing combining vowels: 0 columns (attach above/below consonant)
        assertEquals(0, Theme.displayWidth(0x17B7)); // Vowel I ិ
        assertEquals(0, Theme.displayWidth(0x17B8)); // Vowel II ី
        assertEquals(0, Theme.displayWidth(0x17B9)); // Vowel Y ឹ
        assertEquals(0, Theme.displayWidth(0x17BB)); // Vowel U ុ

        // Non-spacing combining signs, diacritics, and coeng: 0 columns
        assertEquals(0, Theme.displayWidth(0x17D2)); // Coeng ្
        assertEquals(0, Theme.displayWidth(0x17C6)); // Nikahit ំ
        assertEquals(0, Theme.displayWidth(0x17CB)); // Bantoc ់
        assertEquals(0, Theme.displayWidth(0x17CD)); // Toandakhiat ៍

        // Zero-width space and formatting
        assertEquals(0, Theme.displayWidth(0x200B)); // Zero Width Space
        assertEquals(0, Theme.displayWidth(0xFEFF)); // BOM / Zero Width No-Break Space
    }

    @Test
    public void testKhmerStringWidthCalculation() {
        // Single consonant
        assertEquals(1, Theme.width("ក"));

        // Consonant + spacing vowel = 2 columns
        assertEquals(2, Theme.width("កា"));

        // Consonant + non-spacing vowel = 1 column
        assertEquals(1, Theme.width("កិ"));

        // Consonant + non-spacing below + non-spacing above = 1 column
        assertEquals(1, Theme.width("កុំ"));

        // Full word: ចិត្ត (CA(1) + I(0) + COENG(0) + TA(1) + TA(1)) = 3 columns
        assertEquals(3, Theme.width("ចិត្ត"));
    }

    @Test
    public void testKhmerTextWrappingDoesNotBreakCombiningMarks() {
        String khmerParagraph = "មានការប្ដូរ ចិត្ត ស្រស់ ស្អាត ឬ ពណ៌ខ្មៅ : ម្យ៉ាងដែលជាមានប្រការ "
                + "នៅតែមានការ គ្នា យ៉ាងល្អ ចិត្ត នៃការ ចាប់ផ្ដើម ស្រស់ស្អាត នៃការ ដំណើរការ របស់អ្នក";

        int wrapWidth = 40;
        List<String> wrapped = Theme.wrap(khmerParagraph, wrapWidth);

        assertFalse(wrapped.isEmpty());

        for (String line : wrapped) {
            assertTrue(Theme.width(line) <= wrapWidth,
                    "Line exceeds max wrap width: [" + line + "] width=" + Theme.width(line));

            // A line should NEVER start with an orphaned combining vowel or coeng
            assertFalse(line.startsWith("្"), "Line starts with orphaned coeng: " + line);
            assertFalse(line.startsWith("ើ"), "Line starts with orphaned vowel OE: " + line);
            assertFalse(line.startsWith("ា"), "Line starts with orphaned vowel AA: " + line);
            assertFalse(line.startsWith("ិ"), "Line starts with orphaned vowel I: " + line);
            assertFalse(line.startsWith("់"), "Line starts with orphaned bantoc: " + line);
        }
    }

    @Test
    public void testKhmerChatBubbleBorderAlignment() {
        int bubbleW = 66;
        int maxContentW = bubbleW - 4;

        String khmerMessage = "មានការប្ដូរ ចិត្ត ស្រស់ ស្អាត ឬ ពណ៌ខ្មៅ : ម្យ៉ាងដែលជាមានប្រការ "
                + "នៅតែមានការ គ្នា យ៉ាងល្អ ចិត្ត នៃការ ចាប់ផ្ដើម ស្រស់ស្អាត នៃការ ដំណើរការ របស់អ្នក";

        List<String> wrapped = Theme.wrap(khmerMessage, maxContentW);

        List<String> bubbleRows = new ArrayList<>();
        bubbleRows.add("┌" + "─".repeat(bubbleW - 2) + "┐");
        bubbleRows.add("│ " + Theme.padRight("LIFEForge AI", bubbleW - 4) + " │");
        bubbleRows.add("├" + "─".repeat(bubbleW - 2) + "┤");
        for (String line : wrapped) {
            bubbleRows.add("│ " + Theme.padRight(line, bubbleW - 4) + " │");
        }
        bubbleRows.add("└" + "─".repeat(bubbleW - 2) + "┘");

        // Every row in the bubble must have the EXACT same visible display width (bubbleW)
        for (int i = 0; i < bubbleRows.size(); i++) {
            String row = bubbleRows.get(i);
            int rowWidth = Theme.width(row);
            assertEquals(bubbleW, rowWidth,
                    String.format("Row %d has width %d instead of %d: [%s]", i, rowWidth, bubbleW, row));
            assertTrue(row.startsWith("┌") || row.startsWith("├") || row.startsWith("└") || row.startsWith("│"));
            assertTrue(row.endsWith("┐") || row.endsWith("┤") || row.endsWith("┘") || row.endsWith("│"));
        }
    }

    @Test
    public void testKhmerSanitizeText() {
        String hallucinatedKhmer = "ប្ រើ ចិ ត្ត  ដូ ចជា";
        String cleaned = AiExplanationService.sanitizeKhmerText(hallucinatedKhmer);
        assertEquals("ប្រើ ចិត្ត  ដូចជា", cleaned);
    }

    @Test
    public void testLanguageLeakageDetection() {
        // English questions must NOT be identified as Khmer requests
        assertFalse(AiExplanationService.isKhmerRequest("so how many time that good for health?"));
        assertFalse(AiExplanationService.isKhmerRequest("Yesterday I slep 10h so today I plan to sleep 8h what about the effective ?"));
        assertFalse(AiExplanationService.isKhmerRequest("How much water should I drink?"));

        // Explicit Khmer requests must be identified
        assertTrue(AiExplanationService.isKhmerRequest("can you translete it in khmer ?"));
        assertTrue(AiExplanationService.isKhmerRequest("តើខ្ញុំគួរគេងប៉ុន្មានម៉ោង?"));
    }

    @Test
    public void testSleepQueryInSleepCategoryFallback() {
        AiExplanationService svc = new AiExplanationService();
        Goal goal = new Goal(1L, "IMPROVE_SKIN_HEALTH", "Improve Skin Health", "Skin wellness", true);
        User user = new User(1L, "Test", "test", "test@example.com", "hash", 22,
                null, 165.0, 52.0, null, null, false, null, null);
        com.lifeforge.model.RecommendationCategory sleepCat = new com.lifeforge.model.RecommendationCategory(3L, "Sleep & Recovery", "Sleep guidance", null, 3);

        // User asks in English in the Sleep context
        AiChatResponse resp = svc.chatFallback(user, goal, sleepCat, null, null, "so how many time that good for health?");
        assertNotNull(resp);
        assertNotNull(resp.text);
        // Must be in English, strictly under 100 words, no asterisks
        assertFalse(AiExplanationService.containsKhmer(resp.text), "Response leaked into Khmer!");
        assertFalse(resp.text.contains("**"), "Response contains Markdown bold asterisks!");
        assertTrue(resp.text.contains("7–9 hours") || resp.text.contains("7-9 hours"));
        assertTrue(resp.text.contains("- Target Duration:") || resp.text.contains("- Consistency:"));
    }

    @Test
    public void testAiExplanationKhmerFallback() {
        AiExplanationService svc = new AiExplanationService();
        Goal goal = new Goal(1L, "IMPROVE_SKIN_HEALTH", "Improve Skin Health", "Skin wellness", true);
        User user = new User(1L, "Test", "test", "test@example.com", "hash", 22,
                null, 165.0, 52.0, null, null, false, null, null);

        AiChatResponse resp = svc.chatFallback(user, goal, null, null, null, "can you translete it in khmer ?");
        assertNotNull(resp);
        assertNotNull(resp.text);
        assertTrue(resp.text.contains("ការណែនាំជាភាសាខ្មែរ"));
        assertTrue(resp.text.contains("ជាតិទឹក"));
    }
}
