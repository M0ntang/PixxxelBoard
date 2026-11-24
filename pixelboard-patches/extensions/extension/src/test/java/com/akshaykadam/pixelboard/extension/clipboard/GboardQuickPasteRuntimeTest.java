package com.akshaykadam.pixelboard.extension.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.akshaykadam.pixelboard.extension.settings.GboardQuickPasteSettings;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardQuickPasteRuntimeTest {

    private Context context;

    @Before
    public void setUp() {
        GboardQuickPasteRuntime.resetForTests();
        GboardQuickPasteSettings.setEnabledOverrideForTesting(Boolean.TRUE);
        context = RuntimeEnvironment.getApplication();
    }

    @After
    public void tearDown() {
        GboardQuickPasteSettings.setEnabledOverrideForTesting(null);
        GboardQuickPasteRuntime.resetForTests();
    }

    @Test
    public void performQuickPasteInsertsLatestClipboardTextVerbatim() {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("label", "hello , world"));

        RecordingInputConnection ic = new RecordingInputConnection(context);
        boolean pasted = GboardQuickPasteRuntime.performQuickPaste(ic, context);

        Assert.assertTrue(pasted);
        Assert.assertEquals("hello , world", ic.committed.toString());
        Assert.assertFalse(ic.contextMenuPasteCalled);
    }

    @Test
    public void performQuickPasteFallsBackToContextMenuPasteWhenClipTextIsEmpty() {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("empty", ""));

        RecordingInputConnection ic = new RecordingInputConnection(context);
        boolean pasted = GboardQuickPasteRuntime.performQuickPaste(ic, context);

        Assert.assertTrue(pasted);
        Assert.assertTrue(ic.contextMenuPasteCalled);
    }

    @Test
    public void attachButtonPlacesInHorizontalLinearLayoutBeforeTrailingChild() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        View leftKey = new View(context);
        View micKey = new View(context);
        header.addView(leftKey);
        header.addView(micKey);

        ImageButton pasteButton = GboardQuickPasteRuntime.createPasteButton(context, null);
        GboardQuickPasteRuntime.attachButtonToHost(header, pasteButton);

        Assert.assertEquals(3, header.getChildCount());
        Assert.assertSame(pasteButton, header.getChildAt(1));
        Assert.assertSame(micKey, header.getChildAt(2));
        Assert.assertFalse(pasteButton.isFocusable());
    }

    @Test
    public void attachButtonToFrameLayoutUsesEndGravityWithOffsetMargin() {
        FrameLayout header = new FrameLayout(context);
        ImageButton pasteButton = GboardQuickPasteRuntime.createPasteButton(context, null);
        GboardQuickPasteRuntime.attachButtonToHost(header, pasteButton);

        Assert.assertEquals(1, header.getChildCount());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pasteButton.getLayoutParams();
        Assert.assertTrue(lp.getMarginEnd() > 0);
    }

    @Test
    public void hasClipboardContentReflectsClipboardState() {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        Assert.assertFalse(GboardQuickPasteRuntime.hasClipboardContent(context));

        cm.setPrimaryClip(ClipData.newPlainText("item", "copied text"));
        Assert.assertTrue(GboardQuickPasteRuntime.hasClipboardContent(context));
    }

    private static final class RecordingInputConnection extends BaseInputConnection {
        final StringBuilder committed = new StringBuilder();
        boolean contextMenuPasteCalled = false;

        RecordingInputConnection(Context context) {
            super(new EditText(context), false);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            committed.append(text);
            return true;
        }

        @Override
        public boolean performContextMenuAction(int id) {
            if (id == android.R.id.paste) {
                contextMenuPasteCalled = true;
                return true;
            }
            return false;
        }
    }
}
