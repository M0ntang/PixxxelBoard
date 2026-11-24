/*
 * PixelBoard - Gboard Enhancement Mod
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.akshaykadam.pixelboard.extension.R;
import com.akshaykadam.pixelboard.extension.keyboard.GboardCommaSpacingRuntime;
import com.akshaykadam.pixelboard.extension.settings.GboardQuickPasteSettings;
import com.akshaykadam.pixelboard.extension.settings.GboardSettingsText;

import java.lang.ref.WeakReference;

/**
 * Runtime manager for the one-tap Quick Paste button on the keyboard header bar.
 *
 * <p>Attaches a non-focusable Material You button to Gboard's header / toolbar host without
 * overlapping existing keys, updates its availability when clipboard contents change, and
 * inserts the latest clipboard text verbatim when tapped.</p>
 */
public final class GboardQuickPasteRuntime {
    static final String PASTE_BUTTON_TAG = "pixelboard_quick_paste_button";

    private static final float ALPHA_ACTIVE = 1.0f;
    private static final float ALPHA_EMPTY = 0.45f;

    private static WeakReference<InputMethodService> lastServiceRef = new WeakReference<>(null);
    private static WeakReference<View> lastInputViewRef = new WeakReference<>(null);
    private static WeakReference<ImageButton> activeButtonRef = new WeakReference<>(null);
    private static ClipboardManager.OnPrimaryClipChangedListener clipListener;

    private GboardQuickPasteRuntime() {
    }

    public static void onInputViewCreated(Object serviceObj, View inputView) {
        if (!(serviceObj instanceof InputMethodService) || inputView == null) {
            return;
        }
        InputMethodService service = (InputMethodService) serviceObj;
        lastServiceRef = new WeakReference<>(service);
        lastInputViewRef = new WeakReference<>(inputView);
        inputView.post(() -> syncPasteButton(service, inputView));
    }

    public static void onStartInputView(Object serviceObj, EditorInfo editorInfo) {
        if (!(serviceObj instanceof InputMethodService)) {
            return;
        }
        InputMethodService service = (InputMethodService) serviceObj;
        lastServiceRef = new WeakReference<>(service);
        View rootView = resolveRootView(service);
        if (rootView != null) {
            rootView.post(() -> syncPasteButton(service, rootView));
        }
    }

    static void syncPasteButton(InputMethodService service, View rootView) {
        if (service == null || rootView == null) {
            return;
        }
        try {
            boolean enabled = GboardQuickPasteSettings.isEnabled(service);
            View existing = rootView.findViewWithTag(PASTE_BUTTON_TAG);
            if (!enabled) {
                if (existing != null) {
                    ViewParent parent = existing.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(existing);
                    } else {
                        existing.setVisibility(View.GONE);
                    }
                }
                activeButtonRef = new WeakReference<>(null);
                return;
            }

            ImageButton button;
            if (existing instanceof ImageButton) {
                button = (ImageButton) existing;
            } else {
                ViewGroup host = findHeaderHost(rootView);
                if (host == null) {
                    return;
                }
                button = createPasteButton(host.getContext(), service);
                attachButtonToHost(host, button);
            }

            button.setVisibility(View.VISIBLE);
            activeButtonRef = new WeakReference<>(button);
            updateButtonAvailability(button, service);
            ensureClipListenerRegistered(service);
        } catch (Throwable ignored) {
        }
    }

    static ViewGroup findHeaderHost(View rootView) {
        if (rootView == null) {
            return null;
        }
        Context context = rootView.getContext();
        Resources resources = context != null ? context.getResources() : null;
        String pkg = context != null ? context.getPackageName() : null;

        if (resources != null && pkg != null) {
            String[] directHostNames = {
                    "keyboard_header",
                    "header_view",
                    "access_points_bar",
            };
            for (String name : directHostNames) {
                int id = resources.getIdentifier(name, "id", pkg);
                if (id != 0) {
                    View candidate = rootView.findViewById(id);
                    if (candidate instanceof ViewGroup) {
                        return (ViewGroup) candidate;
                    }
                }
            }

            String[] childKeyNames = {
                    "key_pos_header_end_extra_key",
                    "key_pos_header_start_extra_key",
                    "key_pos_header_access_points_menu",
            };
            for (String name : childKeyNames) {
                int id = resources.getIdentifier(name, "id", pkg);
                if (id != 0) {
                    View child = rootView.findViewById(id);
                    if (child != null && child.getParent() instanceof ViewGroup) {
                        return (ViewGroup) child.getParent();
                    }
                }
            }
        }

        ViewGroup dfsMatch = findHeaderHostByDfs(rootView);
        if (dfsMatch != null) {
            return dfsMatch;
        }

        return rootView instanceof ViewGroup ? (ViewGroup) rootView : null;
    }

    private static ViewGroup findHeaderHostByDfs(View view) {
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        String simpleName = group.getClass().getSimpleName();
        if (simpleName.contains("KeyboardHeader")
                || simpleName.contains("Header")
                || simpleName.contains("AccessPointsBar")) {
            return group;
        }

        int childCount = group.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = group.getChildAt(i);
            if (child.getClass().getSimpleName().contains("LatinFixedCountCandidatesHolderView")) {
                return group;
            }
            ViewGroup nested = findHeaderHostByDfs(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    static ImageButton createPasteButton(Context context, InputMethodService service) {
        ImageButton button = new ImageButton(context);
        button.setTag(PASTE_BUTTON_TAG);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int paddingPx = dp(context, 8);
        button.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        boolean darkMode = isNightMode(context);
        int iconColor = darkMode ? Color.parseColor("#E3E3E3") : Color.parseColor("#1F1F1F");
        int pillColor = darkMode ? Color.parseColor("#2B2F36") : Color.parseColor("#ECEFF4");
        int rippleColor = darkMode ? Color.parseColor("#44FFFFFF") : Color.parseColor("#22000000");

        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.OVAL);
        pill.setColor(pillColor);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), pill, null));
        button.setImageDrawable(new PasteIconDrawable(iconColor));
        button.setContentDescription(
                GboardSettingsText.get(context, R.string.gboard_patches_quick_paste_button_desc));

        button.setOnClickListener(v -> performQuickPaste(service, v));
        return button;
    }

    static void attachButtonToHost(ViewGroup host, ImageButton button) {
        Context context = host.getContext();
        int sizePx = dp(context, 36);
        int marginPx = dp(context, 4);

        if (host instanceof LinearLayout
                && ((LinearLayout) host).getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMarginStart(marginPx);
            lp.setMarginEnd(marginPx);
            int insertIndex = Math.max(0, host.getChildCount() - 1);
            host.addView(button, insertIndex, lp);
        } else if (host instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    sizePx,
                    sizePx,
                    Gravity.END | Gravity.CENTER_VERTICAL);
            lp.setMarginEnd(dp(context, 48));
            button.setElevation(dp(context, 4));
            host.addView(button, lp);
        } else if (host instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(sizePx, sizePx);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
            lp.setMarginEnd(dp(context, 48));
            host.addView(button, lp);
        } else {
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(sizePx, sizePx);
            lp.setMarginStart(marginPx);
            lp.setMarginEnd(marginPx);
            host.addView(button, lp);
        }
    }

    public static boolean performQuickPaste(InputMethodService service, View anchor) {
        if (anchor != null) {
            try {
                anchor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Throwable ignored) {
            }
        }
        if (service == null) {
            return false;
        }
        InputConnection ic = service.getCurrentInputConnection();
        return performQuickPaste(ic, service);
    }

    static boolean performQuickPaste(InputConnection ic, Context context) {
        if (ic == null || context == null) {
            return false;
        }
        try {
            CharSequence latestText = readLatestClipboardText(context);
            if (latestText != null && latestText.length() > 0) {
                return GboardCommaSpacingRuntime.commitRawText(ic, latestText, 1);
            }
            if (hasClipboardContent(context)) {
                return ic.performContextMenuAction(android.R.id.paste);
            }
        } catch (Throwable ignored) {
            try {
                return ic.performContextMenuAction(android.R.id.paste);
            } catch (Throwable ignoredFallback) {
            }
        }
        return false;
    }

    static CharSequence readLatestClipboardText(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return null;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return null;
            }
            ClipData.Item item = clip.getItemAt(0);
            if (item == null) {
                return null;
            }
            CharSequence text = item.coerceToText(context);
            return text != null && text.length() > 0 ? text : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean hasClipboardContent(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return false;
            }
            if (cm.hasPrimaryClip()) {
                return true;
            }
            ClipData clip = cm.getPrimaryClip();
            return clip != null && clip.getItemCount() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void updateButtonAvailability(ImageButton button, Context context) {
        if (button == null || context == null) {
            return;
        }
        button.setAlpha(hasClipboardContent(context) ? ALPHA_ACTIVE : ALPHA_EMPTY);
    }

    private static void ensureClipListenerRegistered(Context context) {
        if (clipListener != null || context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext();
            Context targetContext = appContext != null ? appContext : context;
            ClipboardManager cm =
                    (ClipboardManager) targetContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return;
            }
            ClipboardManager.OnPrimaryClipChangedListener listener = () -> {
                ImageButton button = activeButtonRef.get();
                if (button != null) {
                    button.post(() -> updateButtonAvailability(button, targetContext));
                }
            };
            cm.addPrimaryClipChangedListener(listener);
            clipListener = listener;
        } catch (Throwable ignored) {
        }
    }

    private static View resolveRootView(InputMethodService service) {
        View cached = lastInputViewRef.get();
        if (cached != null && cached.isAttachedToWindow()) {
            return cached;
        }
        try {
            if (service.getWindow() != null && service.getWindow().getWindow() != null) {
                return service.getWindow().getWindow().getDecorView();
            }
        } catch (Throwable ignored) {
        }
        return cached;
    }

    private static boolean isNightMode(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return config != null
                && (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()));
    }

    static void resetForTests() {
        lastServiceRef = new WeakReference<>(null);
        lastInputViewRef = new WeakReference<>(null);
        activeButtonRef = new WeakReference<>(null);
        clipListener = null;
    }

    /**
     * Programmatic Material clipboard-paste vector icon so no external drawable resource ID is needed.
     */
    private static final class PasteIconDrawable extends Drawable {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bodyRect = new RectF();
        private final RectF clipRect = new RectF();
        private final Path linesPath = new Path();

        PasteIconDrawable(int color) {
            strokePaint.setColor(color);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint.setColor(color);
            fillPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float w = bounds.width();
            float h = bounds.height();
            if (w <= 0f || h <= 0f) {
                return;
            }
            float size = Math.min(w, h);
            float left = bounds.left + (w - size) / 2f;
            float top = bounds.top + (h - size) / 2f;

            float stroke = Math.max(1.5f, size * 0.09f);
            strokePaint.setStrokeWidth(stroke);

            bodyRect.set(
                    left + size * 0.20f,
                    top + size * 0.20f,
                    left + size * 0.80f,
                    top + size * 0.90f);
            canvas.drawRoundRect(bodyRect, size * 0.10f, size * 0.10f, strokePaint);

            clipRect.set(
                    left + size * 0.36f,
                    top + size * 0.10f,
                    left + size * 0.64f,
                    top + size * 0.25f);
            canvas.drawRoundRect(clipRect, size * 0.06f, size * 0.06f, fillPaint);

            linesPath.reset();
            linesPath.moveTo(left + size * 0.34f, top + size * 0.46f);
            linesPath.lineTo(left + size * 0.66f, top + size * 0.46f);
            linesPath.moveTo(left + size * 0.34f, top + size * 0.62f);
            linesPath.lineTo(left + size * 0.66f, top + size * 0.62f);
            linesPath.moveTo(left + size * 0.34f, top + size * 0.76f);
            linesPath.lineTo(left + size * 0.54f, top + size * 0.76f);
            canvas.drawPath(linesPath, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            strokePaint.setAlpha(alpha);
            fillPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            strokePaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
