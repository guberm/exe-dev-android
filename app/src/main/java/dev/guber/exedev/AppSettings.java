package dev.guber.exedev;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

public final class AppSettings {
    private static final String PREFS = "exe_dev_settings";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_MOBILE_SSH_PRIVATE = "mobile_ssh_private_key";
    private static final String KEY_MOBILE_SSH_PUBLIC = "mobile_ssh_public_key";
    public static final String DEFAULT_ENDPOINT = "https://exe.dev/exec";

    private AppSettings() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String endpoint(Context context) {
        String value = prefs(context).getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
        if (value == null || value.trim().isEmpty()) return DEFAULT_ENDPOINT;
        return value.trim();
    }

    public static String token(Context context) {
        String value = prefs(context).getString(KEY_TOKEN, "");
        return value == null ? "" : value.trim();
    }

    public static void save(Context context, String endpoint, String token) {
        prefs(context).edit()
                .putString(KEY_ENDPOINT, endpoint == null || endpoint.trim().isEmpty() ? DEFAULT_ENDPOINT : endpoint.trim())
                .putString(KEY_TOKEN, token == null ? "" : token.trim())
                .apply();
    }

    public static boolean hasToken(Context context) {
        return !token(context).isEmpty();
    }

    public static void logout(Context context) {
        prefs(context).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_MOBILE_SSH_PRIVATE)
                .remove(KEY_MOBILE_SSH_PUBLIC)
                .apply();
    }

    public static Palette palette(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = mode == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            return new Palette(
                    Color.rgb(2, 6, 23), Color.rgb(15, 23, 42), Color.rgb(30, 41, 59),
                    Color.rgb(248, 250, 252), Color.rgb(203, 213, 225), Color.rgb(167, 139, 250),
                    Color.rgb(52, 211, 153), Color.rgb(248, 113, 113));
        }
        return new Palette(
                Color.rgb(248, 250, 252), Color.WHITE, Color.rgb(226, 232, 240),
                Color.rgb(15, 23, 42), Color.rgb(71, 85, 105), Color.rgb(124, 58, 237),
                Color.rgb(5, 150, 105), Color.rgb(220, 38, 38));
    }

    public static final class Palette {
        public final int background, card, border, text, muted, primary, success, danger;
        Palette(int background, int card, int border, int text, int muted, int primary, int success, int danger) {
            this.background = background;
            this.card = card;
            this.border = border;
            this.text = text;
            this.muted = muted;
            this.primary = primary;
            this.success = success;
            this.danger = danger;
        }
    }
}
