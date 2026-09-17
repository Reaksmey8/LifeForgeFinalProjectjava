package com.lifeforge.tui4j;

import com.lifeforge.AppContext;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminUsersViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private List<User> buildSampleUsers() {
        User u1 = new User();
        u1.setId(1617L);
        u1.setFullName("Chhom ChanReaksmey");
        u1.setEmail("smeyzz@gmail.com");
        u1.setRole(Role.USER);

        User u2 = new User();
        u2.setId(1210L);
        u2.setFullName("Bee Mey");
        u2.setEmail("mey@gmail.com");
        u2.setRole(Role.USER);

        User u3 = new User();
        u3.setId(6L);
        u3.setFullName("chan Thu");
        u3.setEmail("chantu@gmail.com");
        u3.setRole(Role.USER);

        // ADMIN user — must NOT be displayed in Manage Users screen
        User u4 = new User();
        u4.setId(5L);
        u4.setFullName("Bee Meyzz");
        u4.setEmail("beemey@gmail.com");
        u4.setRole(Role.ADMIN);

        User u5 = new User();
        u5.setId(2L);
        u5.setFullName("San sengthanu");
        u5.setEmail("thanu@gmail.com");
        u5.setRole(Role.USER);

        return List.of(u1, u2, u3, u4, u5);
    }

    @Test
    public void testAdminUsersViewAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Object adminUsersScreen = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_USERS");
        screenField.set(tui, adminUsersScreen);

        Field usersField = LifeForge.class.getDeclaredField("users");
        usersField.setAccessible(true);
        usersField.set(tui, buildSampleUsers());

        Method renderMethod = LifeForge.class.getDeclaredMethod("renderAdminUsersPage", int.class);
        renderMethod.setAccessible(true);
        String rendered = (String) renderMethod.invoke(tui, 80);

        assertNotNull(rendered);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // 1. Sharp square corners
        assertTrue(lines[0].startsWith("┌"), "Top line must start with ┌: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top line must end with ┐: " + lines[0]);
        assertTrue(lines[lines.length - 1].startsWith("└"), "Bottom line must start with └: " + lines[lines.length - 1]);
        assertTrue(lines[lines.length - 1].endsWith("┘"), "Bottom line must end with ┘: " + lines[lines.length - 1]);

        // 2. Perfect border alignment across all rows at exactly 80 columns
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            assertEquals(80, Theme.width(l), "Line " + i + " width mismatch at 80 cols: " + l);
            assertTrue(l.endsWith("│") || l.endsWith("┐") || l.endsWith("┘") || l.endsWith("┤"),
                    "Line " + i + " must end with border: " + l);
        }

        // 3. Header structure
        assertTrue(clean.contains("LIFEForge"), "Should contain brand LIFEForge");
        assertTrue(clean.contains("ADMIN / USER MANAGEMENT"), "Should contain title ADMIN / USER MANAGEMENT");
        assertTrue(clean.contains("👤 Admin"), "Should contain admin badge 👤 Admin");
        assertTrue(clean.contains("Manage and maintain registered users"), "Should contain subtitle");

        // 4. Clean Toolbar
        assertTrue(clean.contains("Total Users: 4"), "Should contain Total Users: 4 (ADMIN account excluded)");
        assertTrue(clean.contains("[S] Search"), "Should contain [S] Search");
        assertTrue(clean.contains("[↑↓] Select"), "Should contain [↑↓] Select");
        assertTrue(clean.contains("[←→] Page"), "Should contain [←→] Page");

        // 5. User Table headers (no ROLE, no ACTIONS column)
        assertTrue(clean.contains("ID"), "Should contain ID");
        assertTrue(clean.contains("NAME"), "Should contain NAME");
        assertTrue(clean.contains("EMAIL"), "Should contain EMAIL");
        assertTrue(clean.contains("STATUS"), "Should contain STATUS");
        assertFalse(clean.contains("ROLE"), "Should NOT contain ROLE column");
        assertFalse(clean.contains("ACTIONS"), "Should NOT contain ACTIONS column in table");

        // 6. Selected row with > indicator and aligned data
        assertTrue(clean.contains("> #1617"), "Selected row must start with > #1617");
        assertTrue(clean.contains("Chhom ChanReaksmey"), "Should contain user name");
        assertTrue(clean.contains("smeyzz@gmail.com"), "Should contain user email");
        assertTrue(clean.contains("● Active"), "Should contain status ● Active");

        // Admin account must NOT be displayed
        assertFalse(clean.contains("beemey@gmail.com"), "ADMIN account email must not be displayed");

        // Removed unnecessary icons
        assertFalse(clean.contains("[👁]"), "Should NOT contain [👁] icon");
        assertFalse(clean.contains("[✏]"), "Should NOT contain [✏] icon");
        assertFalse(clean.contains("[🗑]"), "Should NOT contain [🗑] icon");
        assertFalse(clean.contains("☑"), "Should NOT contain checkbox ☑");
        assertFalse(clean.contains("☐"), "Should NOT contain checkbox ☐");

        // 7. Selected User Action Area
        assertTrue(clean.contains("SELECTED USER"), "Should contain SELECTED USER section");
        assertTrue(clean.contains("[V] View Details"), "Should contain [V] View Details action");
        assertTrue(clean.contains("[B] Block User"), "Should contain [B] Block User action");
        assertTrue(clean.contains("[D] Delete User"), "Should contain [D] Delete User action");

        // 8. Clean Pagination line
        assertTrue(clean.contains("Page 1 / 1"), "Should contain Page 1 / 1");
        assertTrue(clean.contains("Showing 1–4 of 4 users"), "Should contain Showing 1–4 of 4 users");

        // 9. Clean Footer
        assertTrue(clean.contains("↑↓ Select"), "Should contain ↑↓ Select in footer");
        assertTrue(clean.contains("←→ Page"), "Should contain ←→ Page in footer");
        assertTrue(clean.contains("S Search"), "Should contain S Search in footer");
        assertTrue(clean.contains("X Clear"), "Should contain X Clear in footer");
        assertTrue(clean.contains("B Back"), "Should contain B Back in footer");
        assertTrue(clean.contains("H Home"), "Should contain H Home in footer");
        assertTrue(clean.contains("Q Quit"), "Should contain Q Quit in footer");
        assertFalse(clean.contains("Enter Details"), "Should NOT contain Enter Details in footer");

        System.out.println("=== VISUAL RENDER (80 COLUMNS) ===");
        System.out.println(clean);
    }

    @Test
    public void testAdminUsersViewAt100Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Object adminUsersScreen = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_USERS");
        screenField.set(tui, adminUsersScreen);

        Field usersField = LifeForge.class.getDeclaredField("users");
        usersField.setAccessible(true);
        usersField.set(tui, buildSampleUsers());

        Method renderMethod = LifeForge.class.getDeclaredMethod("renderAdminUsersPage", int.class);
        renderMethod.setAccessible(true);
        String rendered = (String) renderMethod.invoke(tui, 100);

        assertNotNull(rendered);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            assertEquals(100, Theme.width(l), "Line " + i + " width mismatch at 100 cols: " + l);
            assertTrue(l.endsWith("│") || l.endsWith("┐") || l.endsWith("┘") || l.endsWith("┤"),
                    "Line " + i + " must end with border: " + l);
        }

        assertTrue(clean.contains("Chhom ChanReaksmey"), "Should contain full name at 100 columns");
        System.out.println("=== VISUAL RENDER (100 COLUMNS) ===");
        System.out.println(clean);
    }

    @Test
    public void testAdminUsersViewAt120Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Object adminUsersScreen = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_USERS");
        screenField.set(tui, adminUsersScreen);

        Field usersField = LifeForge.class.getDeclaredField("users");
        usersField.setAccessible(true);
        usersField.set(tui, buildSampleUsers());

        Method renderMethod = LifeForge.class.getDeclaredMethod("renderAdminUsersPage", int.class);
        renderMethod.setAccessible(true);
        String rendered = (String) renderMethod.invoke(tui, 120);

        assertNotNull(rendered);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            assertEquals(120, Theme.width(l), "Line " + i + " width mismatch at 120 cols: " + l);
            assertTrue(l.endsWith("│") || l.endsWith("┐") || l.endsWith("┘") || l.endsWith("┤"),
                    "Line " + i + " must end with border: " + l);
        }

        assertTrue(clean.contains("Chhom ChanReaksmey"), "Should contain full name at 120 columns");
    }

    @Test
    public void testAdminUsersViewBorderAndColors() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        Method renderMethod = LifeForge.class.getDeclaredMethod("renderAdminUsersPage", int.class);
        renderMethod.setAccessible(true);
        Field usersField = LifeForge.class.getDeclaredField("users");
        usersField.setAccessible(true);
        usersField.set(tui, buildSampleUsers());

        String rendered = (String) renderMethod.invoke(tui, 80);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            assertEquals(80, l.length(), "Line " + i + " character length mismatch: '" + l + "'");
            assertEquals(80, Theme.width(l), "Line " + i + " width mismatch: '" + l + "'");
            assertTrue(l.endsWith("│") || l.endsWith("┐") || l.endsWith("┘") || l.endsWith("┤"),
                    "Line " + i + " must end with border: " + l);
        }

        System.out.println("=== VISUAL RENDER (VERIFIED 80 COLUMNS ALL LINES) ===");
        System.out.println(clean);
    }
}
