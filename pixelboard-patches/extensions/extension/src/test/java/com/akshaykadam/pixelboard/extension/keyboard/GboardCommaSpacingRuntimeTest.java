package com.akshaykadam.pixelboard.extension.keyboard;

import android.text.InputType;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import com.akshaykadam.pixelboard.extension.settings.GboardCommaSpacingSettings;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardCommaSpacingRuntimeTest {

    private FakeInputConnection ic;

    @Before
    public void setUp() {
        GboardCommaSpacingRuntime.resetForTests();
        GboardCommaSpacingSettings.setEnabledOverrideForTesting(Boolean.TRUE);
        EditorInfo info = new EditorInfo();
        info.inputType = InputType.TYPE_CLASS_TEXT;
        GboardCommaSpacingRuntime.onStartInput(new Object(), info);
        ic = new FakeInputConnection();
    }

    @After
    public void tearDown() {
        GboardCommaSpacingSettings.setEnabledOverrideForTesting(null);
        GboardCommaSpacingRuntime.resetForTests();
    }

    @Test
    public void spaceBeforeCommaIsRemovedAndSpaceAfterIsAdded() {
        ic.setTextAndCursor("слово ", 6);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        Assert.assertEquals("слово, ", ic.getText());
    }

    @Test
    public void spaceCommaSpaceDoesNotDoubleSpace() {
        ic.setTextAndCursor("слово ", 6);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        GboardCommaSpacingRuntime.commitText(ic, " ", 1);
        GboardCommaSpacingRuntime.commitText(ic, "мир", 1);
        Assert.assertEquals("слово, мир", ic.getText());
    }

    @Test
    public void spaceCommaImmediatelyFollowedByWordInsertsSpace() {
        ic.setTextAndCursor("слово ", 6);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        GboardCommaSpacingRuntime.commitText(ic, "мир", 1);
        Assert.assertEquals("слово, мир", ic.getText());
    }

    @Test
    public void commaWithoutSpaceBeforeFollowedByWordInsertsSpace() {
        ic.setTextAndCursor("слово", 5);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        GboardCommaSpacingRuntime.commitText(ic, "мир", 1);
        Assert.assertEquals("слово, мир", ic.getText());
    }

    @Test
    public void commaFollowedByManualSpaceDoesNotDoubleSpace() {
        ic.setTextAndCursor("слово", 5);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        GboardCommaSpacingRuntime.commitText(ic, " ", 1);
        GboardCommaSpacingRuntime.commitText(ic, "мир", 1);
        Assert.assertEquals("слово, мир", ic.getText());
    }

    @Test
    public void commaBeforeExistingLeadingSpaceDoesNotDuplicateSpace() {
        ic.setTextAndCursor("слово  мир", 6);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        Assert.assertEquals("слово, мир", ic.getText());
    }

    @Test
    public void multipleSpacesBeforeCommaAreCollapsed() {
        ic.setTextAndCursor("слово   ", 8);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        Assert.assertEquals("слово, ", ic.getText());
    }

    @Test
    public void preExistingSpaceCommaFollowedBySpaceFixesSpacing() {
        ic.setTextAndCursor("слово ,", 7);
        GboardCommaSpacingRuntime.commitText(ic, " ", 1);
        Assert.assertEquals("слово, ", ic.getText());
    }

    @Test
    public void decimalCommaAfterDigitIsPreservedWithoutSpace() {
        ic.setTextAndCursor("1", 1);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        GboardCommaSpacingRuntime.commitText(ic, "5", 1);
        Assert.assertEquals("1,5", ic.getText());
    }

    @Test
    public void commaAfterNumberAndSpaceAddsSpaceAfterComma() {
        ic.setTextAndCursor("5 ", 2);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        Assert.assertEquals("5, ", ic.getText());
    }

    @Test
    public void multiCharCommitsNormalizeCommaSpacing() {
        GboardCommaSpacingRuntime.commitText(ic, "слово ,", 1);
        Assert.assertEquals("слово, ", ic.getText());

        ic.setTextAndCursor("", 0);
        GboardCommaSpacingRuntime.commitText(ic, "слово , мир", 1);
        Assert.assertEquals("слово, мир", ic.getText());
    }

    @Test
    public void disabledSettingLeavesInputUntouched() {
        GboardCommaSpacingSettings.setEnabledOverrideForTesting(Boolean.FALSE);
        ic.setTextAndCursor("слово ", 6);
        GboardCommaSpacingRuntime.commitText(ic, ",", 1);
        Assert.assertEquals("слово ,", ic.getText());
    }

    @Test
    public void passwordUriEmailAndNumberInputTypesAreBypassed() {
        int[] skippedTypes = {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        };
        for (int inputType : skippedTypes) {
            EditorInfo info = new EditorInfo();
            info.inputType = inputType;
            GboardCommaSpacingRuntime.onStartInput(new Object(), info);
            ic.setTextAndCursor("abc ", 4);
            GboardCommaSpacingRuntime.commitText(ic, ",", 1);
            Assert.assertEquals("InputType " + inputType + " should be bypassed", "abc ,", ic.getText());
        }
    }

    @Test
    public void commitRawTextBypassesCommaNormalization() {
        GboardCommaSpacingRuntime.commitRawText(ic, "a , b", 1);
        Assert.assertEquals("a , b", ic.getText());
    }

    @Test
    public void reentrantInputConnectionWrapperDoesNotDoubleProcess() {
        InputConnection wrapper = new InputConnectionWrapper(ic, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                return GboardCommaSpacingRuntime.commitText(ic, text, newCursorPosition);
            }
        };
        ic.setTextAndCursor("слово ", 6);
        GboardCommaSpacingRuntime.commitText(wrapper, ",", 1);
        Assert.assertEquals("слово, ", ic.getText());
    }

    private static final class FakeInputConnection extends BaseInputConnection {
        private final StringBuilder buffer = new StringBuilder();
        private int cursor = 0;

        FakeInputConnection() {
            super(new EditText(RuntimeEnvironment.getApplication()), false);
        }

        void setTextAndCursor(String text, int cursorPos) {
            buffer.setLength(0);
            buffer.append(text);
            cursor = cursorPos;
        }

        String getText() {
            return buffer.toString();
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            int start = Math.max(0, cursor - n);
            return buffer.substring(start, cursor);
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            int end = Math.min(buffer.length(), cursor + n);
            return buffer.substring(cursor, end);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            int delBefore = Math.min(beforeLength, cursor);
            int delAfter = Math.min(afterLength, buffer.length() - cursor);
            buffer.delete(cursor - delBefore, cursor + delAfter);
            cursor -= delBefore;
            return true;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            String s = text != null ? text.toString() : "";
            buffer.insert(cursor, s);
            cursor += s.length();
            return true;
        }
    }
}
