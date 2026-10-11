package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * CI-safe UI contract tests. They verify the actual Messaging XML layouts and Settings navigation
 * declarations without requiring a device, default-SMS role, or AOSP private UI libraries.
 */
public class UiLayoutContractTest {
    @Test
    public void otpSettingsExposesRequiredControls() throws Exception {
        final Document layout = layout("otp_settings_fragment.xml");
        assertElement(layout, "otp_auto_copy_switch", "MaterialSwitch", null);
        assertElement(layout, "otp_show_toast_switch", "MaterialSwitch", null);
        assertElement(layout, "otp_notification_copy_action_switch", "MaterialSwitch", null);
        assertElement(layout, "otp_rules_list", "RecyclerView", null);
        assertElement(layout, "otp_add_rule", "FloatingActionButton", null);
    }

    @Test
    public void categorySettingsExposesManagementControls() throws Exception {
        final Document layout = layout("category_settings_fragment.xml");
        assertElement(layout, "category_settings_list", "RecyclerView", null);
        assertElement(layout, "category_reset_button", "MaterialButton", null);
        assertElement(layout, "category_add_button", "ExtendedFloatingActionButton", null);
    }

    @Test
    public void cardSettingsExposesParserAndCacheControls() throws Exception {
        final Document layout = layout("sms_card_settings_fragment.xml");
        assertElement(layout, "sms_card_enabled", "MaterialSwitch", null);
        assertElement(layout, "sms_card_confidence", "Slider", null);
        assertElement(layout, "sms_card_type_container", "LinearLayout", null);
        assertElement(layout, "sms_card_history", "MaterialButton", null);
        assertElement(layout, "sms_card_clear", "MaterialButton", null);
        assertElement(layout, "sms_card_import", "MaterialButton", null);
        assertElement(layout, "sms_card_export", "MaterialButton", null);
    }

    @Test
    public void conversationListHasHiddenCategoryStripUntilMultipleCategoriesExist() throws Exception {
        final Document layout = layout("conversation_list_fragment.xml");
        assertElement(layout, "category_chip_strip", "HorizontalScrollView", "gone");
        assertElement(layout, "category_chip_container", "LinearLayout", null);
    }

    @Test
    public void categoryStripIsDeclaredBeforeConversationListContainer() throws Exception {
        final Document document = layout("conversation_list_fragment.xml");
        final Element root = document.getDocumentElement();
        int chipStripIndex = -1;
        int listContainerIndex = -1;
        int elementIndex = 0;
        final NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (!(child instanceof Element)) continue;
            final Element element = (Element) child;
            if ("@+id/category_chip_strip".equals(element.getAttribute("android:id"))) {
                chipStripIndex = elementIndex;
            }
            if ("FrameLayout".equals(simpleName(element.getTagName()))) {
                listContainerIndex = elementIndex;
            }
            elementIndex++;
        }
        assertEquals("Category strip must be the first content element below the ActionBar", 0,
                chipStripIndex);
        assertEquals("Conversation list container must follow the category strip", 1,
                listContainerIndex);
    }

    @Test
    public void conversationRowCategoryChipAndMessageCardStartHidden() throws Exception {
        assertElement(layout("conversation_list_item_view.xml"), "conversation_category_chip", "Chip",
                "gone");
        assertElement(layout("conversation_message_view.xml"), "sms_card_view", "SmsCardView",
                "gone");
    }

    @Test
    public void otpNotificationCopyReceiverIsPrivate() throws Exception {
        final Document manifest = xml("AndroidManifest.xml");
        final NodeList receivers = manifest.getElementsByTagName("receiver");
        for (int index = 0; index < receivers.getLength(); index++) {
            final Element receiver = (Element) receivers.item(index);
            if (".receiver.OtpCopyReceiver".equals(
                    receiver.getAttribute("android:name"))) {
                assertEquals("Notification clipboard receiver must stay private", "false",
                        receiver.getAttribute("android:exported"));
                return;
            }
        }
        throw new AssertionError("Missing private OTP copy receiver");
    }

    @Test
    public void applicationPreferencesNavigateToAllSmartSmsSettings() throws Exception {
        final Document preferences = xml("res/xml/preferences_application.xml");
        assertPreferenceTarget(preferences, "otp_settings_pref_key",
                "com.android.messaging.ui.otp.OtpSettingsActivity");
        assertPreferenceTarget(preferences, "category_settings_pref_key",
                "com.android.messaging.ui.category.CategorySettingsActivity");
        assertPreferenceTarget(preferences, "sms_card_settings_pref_key",
                "com.android.messaging.ui.cardsettings.SmsCardSettingsActivity");
    }

    private static Document layout(final String filename) throws Exception {
        return xml("res/layout/" + filename);
    }

    private static Document xml(final String relativePath) throws Exception {
        final String root = System.getProperty("gkuimessage.rootDir");
        assertNotNull("Gradle must provide the repository root", root);
        final File file = new File(root, relativePath);
        assertEquals("Missing UI contract file: " + file, true, file.isFile());
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    }

    private static void assertElement(final Document document, final String id, final String type,
            final String visibility) {
        final Element element = findById(document, id);
        assertNotNull("Missing view id: " + id, element);
        assertEquals("Wrong view type for " + id, type, simpleName(element.getTagName()));
        if (visibility != null) {
            assertEquals("Unexpected initial visibility for " + id, visibility,
                    element.getAttribute("android:visibility"));
        }
    }

    private static void assertPreferenceTarget(final Document document, final String key,
            final String targetClass) {
        final NodeList nodes = document.getElementsByTagName("Preference");
        for (int index = 0; index < nodes.getLength(); index++) {
            final Element preference = (Element) nodes.item(index);
            if (!key.equals(preference.getAttribute("android:key"))) continue;
            final NodeList children = preference.getElementsByTagName("intent");
            assertEquals("Preference must have exactly one intent", 1, children.getLength());
            assertEquals(targetClass, ((Element) children.item(0)).getAttribute(
                    "android:targetClass"));
            return;
        }
        throw new AssertionError("Missing preference key: " + key);
    }

    private static Element findById(final Document document, final String id) {
        final NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            final Element element = (Element) nodes.item(index);
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) return element;
        }
        return null;
    }

    private static String simpleName(final String name) {
        final int separator = name.lastIndexOf('.');
        return separator < 0 ? name : name.substring(separator + 1);
    }
}
