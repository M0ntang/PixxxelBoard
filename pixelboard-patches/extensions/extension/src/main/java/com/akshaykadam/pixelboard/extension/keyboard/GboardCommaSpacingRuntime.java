/*
 * PixelBoard - Gboard Enhancement Mod
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.keyboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.akshaykadam.pixelboard.extension.settings.GboardCommaSpacingSettings;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Runtime hook for smart comma spacing around {@link InputConnection#commitText}.
 *
 * <p>When enabled, removes accidental spaces before commas and guarantees a single trailing space
 * after a comma (e.g. {@code "word ,"} -> {@code "word, "}, {@code "word , next"} ->
 * {@code "word, next"}) while avoiding duplicate spaces and preserving numeric decimals
 * ({@code "1,5"}).</p>
 */
public final class GboardCommaSpacingRuntime {
    private static final int BEFORE_CURSOR_LOOKBACK = 24;

    private static final ThreadLocal<Boolean> IN_HOOK = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private static volatile int currentInputType = InputType.TYPE_CLASS_TEXT;
    private static volatile boolean justAutoAddedSpaceAfterComma = false;

    private static volatile boolean textAttributeMethodResolved = false;
    private static volatile Method commitTextWithAttributeMethod = null;

    private GboardCommaSpacingRuntime() {
    }

    public static void onStartInput(Object service, EditorInfo editorInfo) {
        currentInputType = editorInfo != null ? editorInfo.inputType : InputType.TYPE_CLASS_TEXT;
        justAutoAddedSpaceAfterComma = false;
    }

    public static boolean commitText(InputConnection ic, CharSequence text, int newCursorPosition) {
        return handleCommitText(ic, text, newCursorPosition, null);
    }

    public static boolean commitTextWithAttribute(
            InputConnection ic,
            CharSequence text,
            int newCursorPosition,
            Object textAttribute) {
        return handleCommitText(ic, text, newCursorPosition, textAttribute);
    }

    /**
     * Commits text bypassing comma-spacing normalization (used by quick paste).
     */
    public static boolean commitRawText(
            InputConnection ic,
            CharSequence text,
            int newCursorPosition) {
        if (ic == null) {
            return false;
        }
        boolean wasInHook = Boolean.TRUE.equals(IN_HOOK.get());
        IN_HOOK.set(Boolean.TRUE);
        try {
            justAutoAddedSpaceAfterComma = false;
            return ic.commitText(text, newCursorPosition);
        } finally {
            IN_HOOK.set(wasInHook ? Boolean.TRUE : Boolean.FALSE);
        }
    }

    private static boolean handleCommitText(
            InputConnection ic,
            CharSequence text,
            int newCursorPosition,
            Object textAttribute) {
        if (ic == null) {
            return false;
        }
        if (Boolean.TRUE.equals(IN_HOOK.get())) {
            return invokeCommitText(ic, text, newCursorPosition, textAttribute);
        }

        IN_HOOK.set(Boolean.TRUE);
        try {
            if (text == null || newCursorPosition != 1
                    || !GboardCommaSpacingSettings.isEnabled()
                    || !isEligibleInputType(currentInputType)) {
                justAutoAddedSpaceAfterComma = false;
                return invokeCommitText(ic, text, newCursorPosition, textAttribute);
            }

            String s = text.toString();
            if (s.isEmpty()) {
                return invokeCommitText(ic, text, newCursorPosition, textAttribute);
            }

            if (isSingleHorizontalSpace(s)) {
                return handleSingleSpace(ic, text, newCursorPosition, textAttribute);
            }

            if (isPureCommaToken(s)) {
                return handlePureCommaToken(ic, s, newCursorPosition, textAttribute);
            }

            if (isEligibleMultiCharCommit(s)) {
                return handleMultiCharCommit(ic, s, newCursorPosition, textAttribute);
            }

            justAutoAddedSpaceAfterComma = false;
            return invokeCommitText(ic, text, newCursorPosition, textAttribute);
        } catch (Throwable ignored) {
            justAutoAddedSpaceAfterComma = false;
            return invokeCommitText(ic, text, newCursorPosition, textAttribute);
        } finally {
            IN_HOOK.set(Boolean.FALSE);
        }
    }

    private static boolean handleSingleSpace(
            InputConnection ic,
            CharSequence originalText,
            int newCursorPosition,
            Object textAttribute) {
        CharSequence before = safeGetTextBeforeCursor(ic, BEFORE_CURSOR_LOOKBACK);
        if (justAutoAddedSpaceAfterComma && endsWithCommaAndHorizontalSpace(before)) {
            justAutoAddedSpaceAfterComma = false;
            return true;
        }

        justAutoAddedSpaceAfterComma = false;
        if (before != null && before.length() >= 3 && before.charAt(before.length() - 1) == ',') {
            int commaIdx = before.length() - 1;
            int spacesBeforeComma = countTrailingHorizontalSpaces(before, commaIdx);
            if (spacesBeforeComma > 0) {
                char charBeforeSpaces = commaIdx - spacesBeforeComma - 1 >= 0
                        ? before.charAt(commaIdx - spacesBeforeComma - 1)
                        : 0;
                if (isWordCharBeforeCommaSpaces(charBeforeSpaces)) {
                    ic.deleteSurroundingText(spacesBeforeComma + 1, 0);
                    CharSequence after = safeGetTextAfterCursor(ic, 1);
                    String replacement = startsWithSpaceOrNewline(after) ? "," : ", ";
                    return invokeCommitText(ic, replacement, newCursorPosition, textAttribute);
                }
            }
        }

        return invokeCommitText(ic, originalText, newCursorPosition, textAttribute);
    }

    private static boolean handlePureCommaToken(
            InputConnection ic,
            String s,
            int newCursorPosition,
            Object textAttribute) {
        CharSequence before = safeGetTextBeforeCursor(ic, BEFORE_CURSOR_LOOKBACK);
        CharSequence after = safeGetTextAfterCursor(ic, 1);

        int beforeLen = before != null ? before.length() : 0;
        int spacesBefore = countTrailingHorizontalSpaces(before, beforeLen);
        char charBeforeSpaces = beforeLen - spacesBefore - 1 >= 0
                ? before.charAt(beforeLen - spacesBefore - 1)
                : 0;

        if (spacesBefore > 0 && isWordCharBeforeCommaSpaces(charBeforeSpaces)) {
            ic.deleteSurroundingText(spacesBefore, 0);
        }

        // Preserve decimal/thousand separators like "1,5" when typing "," directly after a digit.
        if (spacesBefore == 0 && ",".equals(s) && Character.isDigit(charBeforeSpaces)) {
            justAutoAddedSpaceAfterComma = false;
            return invokeCommitText(ic, ",", newCursorPosition, textAttribute);
        }

        if (startsWithSpaceOrNewline(after)) {
            justAutoAddedSpaceAfterComma = false;
            return invokeCommitText(ic, ",", newCursorPosition, textAttribute);
        }

        justAutoAddedSpaceAfterComma = true;
        return invokeCommitText(ic, ", ", newCursorPosition, textAttribute);
    }

    private static boolean handleMultiCharCommit(
            InputConnection ic,
            String s,
            int newCursorPosition,
            Object textAttribute) {
        CharSequence before = safeGetTextBeforeCursor(ic, BEFORE_CURSOR_LOOKBACK);
        CharSequence after = safeGetTextAfterCursor(ic, 1);

        int firstNonSpace = 0;
        while (firstNonSpace < s.length() && isHorizontalSpace(s.charAt(firstNonSpace))) {
            firstNonSpace++;
        }

        String working = s;
        if (firstNonSpace < working.length() && working.charAt(firstNonSpace) == ',') {
            int beforeLen = before != null ? before.length() : 0;
            int spacesBefore = countTrailingHorizontalSpaces(before, beforeLen);
            char charBeforeSpaces = beforeLen - spacesBefore - 1 >= 0
                    ? before.charAt(beforeLen - spacesBefore - 1)
                    : 0;
            if (spacesBefore > 0 && isWordCharBeforeCommaSpaces(charBeforeSpaces)) {
                ic.deleteSurroundingText(spacesBefore, 0);
                working = working.substring(firstNonSpace);
            } else if (firstNonSpace > 0 && isWordCharBeforeCommaSpaces(charBeforeSpaces)) {
                working = working.substring(firstNonSpace);
            }
        }

        StringBuilder out = new StringBuilder(working.length() + 4);
        boolean addedTrailingSpace = false;

        for (int i = 0; i < working.length(); i++) {
            char c = working.charAt(i);
            if (c == ',') {
                int outSpaces = countTrailingHorizontalSpaces(out, out.length());
                if (outSpaces > 0) {
                    char prev = out.length() - outSpaces - 1 >= 0
                            ? out.charAt(out.length() - outSpaces - 1)
                            : 0;
                    if (isWordCharBeforeCommaSpaces(prev)) {
                        out.setLength(out.length() - outSpaces);
                    }
                }
                out.append(',');

                char prevAfterTrim = out.length() >= 2 ? out.charAt(out.length() - 2) : 0;
                if (i + 1 < working.length()) {
                    char next = working.charAt(i + 1);
                    if (Character.isLetter(next)) {
                        out.append(' ');
                    }
                } else {
                    boolean decimalComma = outSpaces == 0
                            && (Character.isDigit(prevAfterTrim)
                            || (out.length() == 1 && before != null && before.length() > 0
                            && Character.isDigit(before.charAt(before.length() - 1))));
                    if (!decimalComma && !startsWithSpaceOrNewline(after)) {
                        out.append(' ');
                        addedTrailingSpace = true;
                    }
                }
            } else {
                out.append(c);
            }
        }

        justAutoAddedSpaceAfterComma = addedTrailingSpace;
        return invokeCommitText(ic, out.toString(), newCursorPosition, textAttribute);
    }

    private static boolean invokeCommitText(
            InputConnection ic,
            CharSequence text,
            int newCursorPosition,
            Object textAttribute) {
        if (textAttribute != null) {
            Method method = resolveCommitTextWithAttributeMethod();
            if (method != null) {
                try {
                    Object result = method.invoke(ic, text, newCursorPosition, textAttribute);
                    return Boolean.TRUE.equals(result);
                } catch (Throwable ignored) {
                    // Fall back to standard 2-arg commitText below.
                }
            }
        }
        return ic.commitText(text, newCursorPosition);
    }

    private static Method resolveCommitTextWithAttributeMethod() {
        if (!textAttributeMethodResolved) {
            try {
                Class<?> textAttributeClass = Class.forName("android.view.inputmethod.TextAttribute");
                commitTextWithAttributeMethod = InputConnection.class.getMethod(
                        "commitText",
                        CharSequence.class,
                        int.class,
                        textAttributeClass);
            } catch (Throwable ignored) {
                commitTextWithAttributeMethod = null;
            }
            textAttributeMethodResolved = true;
        }
        return commitTextWithAttributeMethod;
    }

    static boolean isEligibleInputType(int inputType) {
        if (inputType == InputType.TYPE_NULL) {
            return false;
        }
        int clazz = inputType & InputType.TYPE_MASK_CLASS;
        if (clazz == InputType.TYPE_CLASS_NUMBER
                || clazz == InputType.TYPE_CLASS_PHONE
                || clazz == InputType.TYPE_CLASS_DATETIME) {
            return false;
        }
        if (clazz == InputType.TYPE_CLASS_TEXT) {
            int variation = inputType & InputType.TYPE_MASK_VARIATION;
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_URI
                    || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSingleHorizontalSpace(String s) {
        return s.length() == 1 && isHorizontalSpace(s.charAt(0));
    }

    private static boolean isPureCommaToken(String s) {
        int commaCount = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',') {
                commaCount++;
                if (commaCount > 1) {
                    return false;
                }
            } else if (!isHorizontalSpace(c)) {
                return false;
            }
        }
        return commaCount == 1;
    }

    private static boolean isEligibleMultiCharCommit(String s) {
        return s.length() > 1
                && s.length() <= 64
                && s.indexOf(',') >= 0
                && s.indexOf('\n') < 0
                && !s.toLowerCase(Locale.ROOT).contains("://")
                && s.indexOf('@') < 0;
    }

    private static boolean endsWithCommaAndHorizontalSpace(CharSequence before) {
        if (before == null || before.length() < 2) {
            return false;
        }
        int len = before.length();
        return before.charAt(len - 2) == ',' && isHorizontalSpace(before.charAt(len - 1));
    }

    private static int countTrailingHorizontalSpaces(CharSequence seq, int endExclusive) {
        if (seq == null || endExclusive <= 0) {
            return 0;
        }
        int count = 0;
        for (int i = endExclusive - 1; i >= 0; i--) {
            if (isHorizontalSpace(seq.charAt(i))) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static boolean isWordCharBeforeCommaSpaces(char c) {
        return c != 0
                && !Character.isWhitespace(c)
                && c != ','
                && c != '('
                && c != '['
                && c != '{';
    }

    private static boolean isHorizontalSpace(char c) {
        return c == ' ' || c == '\u00A0';
    }

    private static boolean startsWithSpaceOrNewline(CharSequence after) {
        if (after == null || after.length() == 0) {
            return false;
        }
        char first = after.charAt(0);
        return Character.isWhitespace(first);
    }

    private static CharSequence safeGetTextBeforeCursor(InputConnection ic, int n) {
        try {
            return ic.getTextBeforeCursor(n, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CharSequence safeGetTextAfterCursor(InputConnection ic, int n) {
        try {
            return ic.getTextAfterCursor(n, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void resetForTests() {
        currentInputType = InputType.TYPE_CLASS_TEXT;
        justAutoAddedSpaceAfterComma = false;
        IN_HOOK.set(Boolean.FALSE);
    }
}
