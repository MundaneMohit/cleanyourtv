package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * quiet_focus (config.xml or a theme): one focus state everywhere, a short grow. Strips a top-bar pill to its bare
 * icon and a menu row to its text, keeping each at its resting colour, since with no white pill behind them the
 * focused (dark) colours would vanish on black.
 */
final class Quiet {

    private Quiet() {
    }

    /** Grows the view to AppAdapter.QUIET_SCALE while it holds focus, and back when it loses it. */
    static StateListAnimator grow() {
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_focused}, scaleTo(AppAdapter.QUIET_SCALE));
        states.addState(new int[0], scaleTo(1f));
        return states;
    }

    private static ObjectAnimator scaleTo(float scale) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder((Object) null,
                PropertyValuesHolder.ofFloat(View.SCALE_X, scale), PropertyValuesHolder.ofFloat(View.SCALE_Y, scale));
        a.setDuration(AppAdapter.QUIET_MS);
        a.setInterpolator(new DecelerateInterpolator());
        return a;
    }

    /** A top-bar pill as its bare icon: no background, the icon in its resting colour, growing on focus. */
    static void button(TextView pill) {
        pill.setBackground(null);
        restingColours(pill);
        pill.setStateListAnimator(grow());
    }

    /**
     * A popup panel as a flat dark box with no glass, sheen or rim, roomy enough at the sides for a row to grow,
     * and every focusable row in it, now and added later, made quiet.
     */
    static void panel(ViewGroup panel, float density) {
        GradientDrawable flat = new GradientDrawable();
        flat.setColor(0xF20A0A0A);
        flat.setCornerRadius(20 * density);
        panel.setBackground(flat);
        panel.setElevation(0f);
        int side = Math.round(18 * density), ends = Math.round(10 * density);
        panel.setPaddingRelative(side, ends, side, ends);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        quietRows(panel);
    }

    private static void quietRows(ViewGroup group) {
        group.setClipChildren(false);
        for (int i = 0; i < group.getChildCount(); i++) quietChild(group.getChildAt(i));
        group.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                quietChild(child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
    }

    private static void quietChild(View child) {
        if (child.isFocusable()) {
            child.setBackground(null);
            restingColours(child);
            child.setStateListAnimator(grow());
        } else if (child instanceof ViewGroup) {
            quietRows((ViewGroup) child);
        }
    }

    /** Text and icons in a view keep their unfocused colour whatever state they are in. */
    private static void restingColours(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.setTextColor(t.getTextColors().getDefaultColor());
            ColorStateList tint = t.getCompoundDrawableTintList();
            if (tint != null) t.setCompoundDrawableTintList(ColorStateList.valueOf(tint.getDefaultColor()));
        } else if (v instanceof ImageView) {
            ColorStateList tint = ((ImageView) v).getImageTintList();
            if (tint != null) ((ImageView) v).setImageTintList(ColorStateList.valueOf(tint.getDefaultColor()));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) restingColours(g.getChildAt(i));
        }
    }
}
