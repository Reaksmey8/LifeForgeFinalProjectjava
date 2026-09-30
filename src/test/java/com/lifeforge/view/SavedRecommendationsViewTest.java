package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.dao.SavedRecommendationDao;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.SavedRecommendation;
import com.lifeforge.service.BookmarkService;
import com.lifeforge.service.SavedRecommendationService;
import com.lifeforge.view.ScreenKit.Line;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SavedRecommendationsViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    /**
     * In-memory mock DAO to verify BookmarkService duplicate prevention logic
     * without needing an active database connection.
     */
    static class MockSavedRecommendationDao extends SavedRecommendationDao {
        final Map<String, LocalDateTime> storage = new HashMap<>();
        int saveCount = 0;
        int updateCount = 0;

        private String key(Long userId, Long recId) {
            return userId + ":" + recId;
        }

        @Override
        public boolean alreadySaved(Long userId, Long recommendationId) {
            return storage.containsKey(key(userId, recommendationId));
        }

        @Override
        public void updateSavedAt(Long userId, Long recommendationId) {
            updateCount++;
            storage.put(key(userId, recommendationId), LocalDateTime.now());
        }

        @Override
        public SavedRecommendation save(Long userId, Long recommendationId) {
            saveCount++;
            LocalDateTime now = LocalDateTime.now();
            storage.put(key(userId, recommendationId), now);
            return new SavedRecommendation(1L, userId, recommendationId, now);
        }
    }

    @Test
    public void testBookmarkDuplicatePreventionUpdatesTimestamp() {
        MockSavedRecommendationDao mockDao = new MockSavedRecommendationDao();
        BookmarkService bookmarkService = new BookmarkService(mockDao);

        Recommendation rec = new Recommendation();
        rec.setId(42L);
        rec.setTitle("Eat More Protein");

        // First save -> should invoke save()
        SavedRecommendationService.SaveResult res1 = bookmarkService.bookmark(1L, rec);
        assertTrue(res1.success);
        assertEquals(1, mockDao.saveCount);
        assertEquals(0, mockDao.updateCount);

        // Second save of same item -> must NOT append duplicate, must update timestamp!
        SavedRecommendationService.SaveResult res2 = bookmarkService.bookmark(1L, rec);
        assertTrue(res2.success, "Duplicate save should succeed cleanly");
        assertEquals(1, mockDao.saveCount, "Save count should remain 1");
        assertEquals(1, mockDao.updateCount, "Update count should increment to 1");

        // Bookmark with null/invalid recommendation
        SavedRecommendationService.SaveResult res3 = bookmarkService.bookmark(1L, (Recommendation) null);
        assertFalse(res3.success);
    }

    @Test
    public void testSavedRecommendationsTableLayoutAndStraightBorders() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        // Set screen to SAVED via reflection
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Object savedScreen = Enum.valueOf((Class<Enum>) screenEnum, "SAVED");
        screenField.set(tui, savedScreen);

        // Set terminal width to 80
        Field widthField = LifeForge.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(tui, 80);

        // Prepare sample saved recommendations
        LocalDateTime time1 = LocalDateTime.of(2026, 9, 14, 1, 20, 0, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 9, 14, 1, 21, 0, 0);
        SavedRecommendation item1 = new SavedRecommendation(1L, 100L, 10L, time1);
        SavedRecommendation item2 = new SavedRecommendation(2L, 100L, 20L, time2);

        Field savedListField = LifeForge.class.getDeclaredField("savedList");
        savedListField.setAccessible(true);
        savedListField.set(tui, List.of(item1, item2));

        // Call viewSaved(body)
        List<Line> body = new ArrayList<>();
        Method viewSavedMethod = LifeForge.class.getDeclaredMethod("viewSaved", List.class);
        viewSavedMethod.setAccessible(true);
        String subtitle = (String) viewSavedMethod.invoke(tui, body);

        assertEquals("Everything you bookmarked", subtitle);
        assertFalse(body.isEmpty());

        // Verify borders and column widths
        int cardWidth = 72;
        int expectedLineWidth = cardWidth + 2; // 2 leading spaces + 72 card cols = 74

        for (int i = 0; i < body.size(); i++) {
            Line line = body.get(i);
            int visibleWidth = Theme.width(line.text());
            assertEquals(expectedLineWidth, visibleWidth,
                    "Line " + i + " width mismatch (" + line.text() + ")");
            assertTrue(line.text().startsWith("  ┌") || line.text().startsWith("  │") || line.text().startsWith("  └"),
                    "Line " + i + " must start with border: " + line.text());
            assertTrue(line.text().endsWith("┐") || line.text().endsWith("│") || line.text().endsWith("┘"),
                    "Line " + i + " must end with border: " + line.text());
        }

        // Verify exact content formatting
        String fullText = body.stream().map(Line::text).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(fullText.contains("┌─ 📌 MY SAVED RECOMMENDATIONS"), "Must contain card title");
        assertTrue(fullText.contains("2 saved item(s)."), "Must contain item count");
        assertTrue(fullText.contains("> "), "First focused item must have selection pointer '> '");
        assertTrue(fullText.contains("│ Saved: 2026-09-14 01:20"), "Must contain formatted date 1 without nanoseconds");
        assertTrue(fullText.contains("│ Saved: 2026-09-14 01:21"), "Must contain formatted date 2 without nanoseconds");

        // Strictly verify no raw nanoseconds or variable dash spacers exist
        assertFalse(fullText.contains(".000000"), "Should not contain nanoseconds");
        assertFalse(fullText.contains("  -  saved"), "Should not contain variable dash spacers");
    }

    @Test
    public void testAccurateFooterForSavedScreen() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        // Set screen to SAVED via reflection
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Object savedScreen = Enum.valueOf((Class<Enum>) screenEnum, "SAVED");
        screenField.set(tui, savedScreen);

        Method footerMethod = LifeForge.class.getDeclaredMethod("footer");
        footerMethod.setAccessible(true);
        List<String[]> footerRows = (List<String[]>) footerMethod.invoke(tui);

        String[] keys = ScreenKit.keys(footerRows.toArray(new String[0][]));
        assertEquals(1, keys.length);

        String expectedFooter = "[↑/↓] Navigate   [Enter] Open & Manage   [B] Back   [H] Home   [Q] Quit";
        assertEquals(expectedFooter, keys[0]);
    }

    @Test
    public void testSaveRecommendationReplacesDuplicateInCategory() {
        MockSavedRecommendationDao mockDao = new MockSavedRecommendationDao() {
            Long existingSavedId = 1L;
            Long currentRecId = 10L;

            @Override
            public Optional<SavedRecommendation> findSavedBySameCategory(Long userId, Long recommendationId) {
                // Return existing item in the same category
                return Optional.of(new SavedRecommendation(existingSavedId, userId, currentRecId, LocalDateTime.now()));
            }

            @Override
            public void updateSavedRecommendation(Long savedRecId, Long newRecId) {
                assertEquals(1L, savedRecId);
                currentRecId = newRecId;
                updateCount++;
            }
        };

        SavedRecommendationService service = new SavedRecommendationService(mockDao);

        // Save a different recommendation ID belonging to the same category
        SavedRecommendationService.SaveResult res = service.saveRecommendation(100L, 20L);
        assertTrue(res.success);
        assertEquals(1, mockDao.updateCount, "Should update existing category bookmark instead of inserting duplicate");
        assertEquals(0, mockDao.saveCount, "Save count should be 0 because category already had an entry");
    }

    @Test
    public void testCompleteMasterRoutineWhyRecommendationStraightBorders() {
        Recommendation masterRec = new Recommendation(
                100L,
                1L,
                5L,
                com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                "Complete Master Routine (Build Muscle)",
                "A unified daily routine combining nutrition, exercise, hydration, sleep, and micro-habits.",
                "🍎 Nutrition:\n   • Protein-rich breakfast.\n\n🏋 Exercise:\n   • 4x weekly resistance training.\n\n💧 Hydration:\n   • 2.5 L/day.\n\n😴 Sleep & Recovery:\n   • 8 hours.\n\n🌱 Daily Micro-Habits:\n    • Drink morning water.",
                "Nutrition: ~2400 kcal/day | Hydration: 2.8 L/day | Exercise: 4 sessions/wk | Sleep: 7–9 hrs/night",
                "Morning hydration and balanced meals",
                "Execute consistently."
        );

        com.lifeforge.model.User user = new com.lifeforge.model.User(
                1L, "Alex Doe", "alex", "alex@example.com", "hash", 28,
                com.lifeforge.model.Gender.MALE, 178.0, 75.0,
                com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                com.lifeforge.model.Role.USER, false, null, null);

        com.lifeforge.model.Goal goal = new com.lifeforge.model.Goal(
                1L, "BUILD_MUSCLE", "Build Muscle", "Strength focused", true);

        List<Line> body = new ArrayList<>();
        LifeForge.renderWhyRecommendation(body, masterRec, goal, user, null, false, 80);

        assertFalse(body.isEmpty());
        String fullOutput = body.stream().map(Line::text).reduce("", (a, b) -> a + "\n" + b);

        // Check Card 1 content
        assertTrue(fullOutput.contains("Unified 5-Pillar Lifestyle Blueprint"), "Card 1 must show clean blueprint target");
        assertTrue(fullOutput.contains("Execute balanced daily actions"),
                "Card 1 must show concise guidance rather than dumping unformatted 15-line multi-bullet string");
        assertFalse(fullOutput.contains("🍎 Nutrition:"),
                "Card 1 must not dump raw unformatted multi-pillar text with newlines");

        // Check Card 2 rationale bullets
        assertTrue(fullOutput.contains("Behavioral Anchoring"), "Card 2 must contain Behavioral Anchoring keyword");
        assertTrue(fullOutput.contains("Systemic Compounding"), "Card 2 must contain Systemic Compounding keyword");
        assertTrue(fullOutput.contains("Lifestyle Synergy"), "Card 2 must contain Lifestyle Synergy keyword");

        // Verify border straightness: each bordered line inside cards must start with "  │" and end with "│"
        int cardWidth = 72; // termWidth 80 - 8 = 72
        int expectedLineWidth = cardWidth + 2; // 2 leading spaces + 72 = 74

        for (int i = 0; i < body.size(); i++) {
            String text = body.get(i).text();
            if (text.startsWith("  │")) {
                int width = Theme.width(text);
                assertEquals(expectedLineWidth, width,
                        "Border row " + i + " must have exact width " + expectedLineWidth + ": [" + text + "]");
                assertTrue(text.endsWith("│"), "Border row " + i + " must end with │");
                assertFalse(text.contains("\n"), "Border row " + i + " must not contain internal newlines");
                assertFalse(text.contains("\r"), "Border row " + i + " must not contain internal carriage returns");
            }
        }
    }
}
