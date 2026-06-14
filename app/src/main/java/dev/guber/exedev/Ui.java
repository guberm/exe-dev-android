package dev.guber.exedev;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, int sp, int color, int style) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setLineSpacing(dp(context, 2), 1.0f);
        return tv;
    }

    public static EditText input(Context context, String hint, String value, boolean multiLine) {
        AppSettings.Palette p = AppSettings.palette(context);
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setText(value == null ? "" : value);
        et.setTextColor(p.text);
        et.setHintTextColor(p.muted);
        et.setTextSize(15);
        et.setSingleLine(!multiLine);
        if (multiLine) {
            et.setMinLines(3);
            et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        }
        et.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        et.setBackground(rounded(p.card, p.border, 12, context));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, 7), 0, dp(context, 7));
        et.setLayoutParams(lp);
        return et;
    }

    public static Button button(Context context, String label, boolean primary) {
        AppSettings.Palette p = AppSettings.palette(context);
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(primary ? 0xFFFFFFFF : p.primary);
        b.setBackground(rounded(primary ? p.primary : p.card, primary ? p.primary : p.border, 12, context));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, 6), 0, dp(context, 6));
        b.setLayoutParams(lp);
        return b;
    }

    public static LinearLayout card(Context context) {
        AppSettings.Palette p = AppSettings.palette(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(p.card, p.border, 18, context));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(context, 14));
        card.setLayoutParams(lp);
        return card;
    }

    public static GradientDrawable rounded(int color, int stroke, int radiusDp, Context context) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(context, radiusDp));
        gd.setStroke(dp(context, 1), stroke);
        return gd;
    }

    public static void addSpacer(LinearLayout layout, int dp) {
        View v = new View(layout.getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(layout.getContext(), dp)));
        layout.addView(v);
    }
}
