package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class RegisterViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    @SuppressWarnings("unchecked")
    private void setScreen(LifeForge tui, String screenName) throws Exception {
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object target = Enum.valueOf((Class<Enum>) screenEnum, screenName);
        screenField.set(tui, target);
    }

    @Test
    public void testRegisterScreenHasUsernameFieldInsteadOfFullName() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        setScreen(tui, "REGISTER");

        Method refreshMethod = LifeForge.class.getDeclaredMethod("refresh");
        refreshMethod.setAccessible(true);
        refreshMethod.invoke(tui);

        Field labelsField = LifeForge.class.getDeclaredField("fLabels");
        labelsField.setAccessible(true);
        String[] labels = (String[]) labelsField.get(tui);

        assertNotNull(labels);
        assertTrue(labels.length > 0);
        assertEquals("Username", labels[0], "First field of register form must be 'Username'");

        for (String label : labels) {
            assertNotEquals("Full Name", label, "Register form should not contain 'Full Name'");
        }

        // View rendering check
        String rendered = tui.view();
        assertNotNull(rendered);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");

        assertTrue(clean.contains("Username"), "Rendered screen must contain 'Username'");
        assertTrue(clean.contains("Email"), "Rendered screen must contain 'Email'");
        assertTrue(clean.contains("Register Account"), "Rendered screen must contain 'Register Account'");
        assertFalse(clean.contains("Full Name"), "Rendered screen must NOT contain 'Full Name'");
    }
}
