package com.ashutosh.dotnetinterviewhub;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int NAVY = Color.rgb(11, 37, 69);
    public static final int BLUE = Color.rgb(46, 116, 181);
    public static final int DARK_BLUE = Color.rgb(31, 77, 120);
    public static final int INK = Color.rgb(28, 37, 48);
    public static final int MUTED = Color.rgb(90, 98, 108);
    public static final int SURFACE = Color.rgb(245, 247, 250);
    public static final int PALE_BLUE = Color.rgb(232, 238, 245);

    private Ui() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView header(Context context, String title, String subtitle) {
        TextView view = new TextView(context);
        view.setText(title + "\n" + subtitle);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        view.setBackgroundColor(NAVY);
        return view;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : DARK_BLUE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? BLUE : PALE_BLUE);
        background.setCornerRadius(dp(context, 10));
        if (!primary) background.setStroke(dp(context, 1), Color.rgb(199, 211, 224));
        button.setBackground(background);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        return button;
    }

    public static LinearLayout.LayoutParams weightedButtonParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 50), 1);
        params.setMargins(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        return params;
    }

    public static GradientDrawable cardBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(context, 12));
        background.setStroke(dp(context, 1), Color.rgb(222, 228, 235));
        return background;
    }

    public static void setMargins(View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.setMargins(dp(view.getContext(), left), dp(view.getContext(), top),
                    dp(view.getContext(), right), dp(view.getContext(), bottom));
            view.setLayoutParams(params);
        }
    }
}
