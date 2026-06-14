package dev.guber.exedev;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private EditText endpoint;
    private EditText token;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.Palette p = AppSettings.palette(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(p.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(root);

        root.addView(Ui.text(this, "exe.dev Settings", 28, p.text, Typeface.BOLD));
        root.addView(Ui.text(this, "Paste an exe.dev HTTPS API bearer token. The app stores it locally on this device and never commits or uploads it anywhere else.", 14, p.muted, Typeface.NORMAL));
        Ui.addSpacer(root, 14);

        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Connection", 18, p.text, Typeface.BOLD));
        endpoint = Ui.input(this, "API endpoint", AppSettings.endpoint(this), false);
        token = Ui.input(this, "Bearer token", AppSettings.token(this), false);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        card.addView(endpoint);
        card.addView(token);
        Button save = Ui.button(this, "Save", true);
        Button test = Ui.button(this, "Save and test login", false);
        Button docs = Ui.button(this, "Open token instructions", false);
        status = Ui.text(this, "Not tested yet", 14, p.muted, Typeface.NORMAL);
        card.addView(save); card.addView(test); card.addView(docs); card.addView(status);
        root.addView(card);

        LinearLayout help = Ui.card(this);
        help.addView(Ui.text(this, "How to create a token", 18, p.text, Typeface.BOLD));
        help.addView(Ui.text(this, "On a trusted computer where you are logged into exe.dev, run:", 14, p.muted, Typeface.NORMAL));
        TextView code = Ui.text(this, "ssh exe.dev ssh-key generate-api-key --exp=30d", 14, p.text, Typeface.BOLD);
        code.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        code.setBackground(Ui.rounded(p.background, p.border, 10, this));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeLp.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        code.setLayoutParams(codeLp);
        help.addView(code);
        help.addView(Ui.text(this, "For least privilege, create a short-lived token and restrict commands to whoami, ls, and new if you do not need more.", 14, p.muted, Typeface.NORMAL));
        root.addView(help);

        save.setOnClickListener(v -> { persist(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); });
        test.setOnClickListener(v -> { persist(); testLogin(); });
        docs.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://exe.dev/docs/https-api"))));
        setContentView(scroll);
    }

    private void persist() { AppSettings.save(this, endpoint.getText().toString(), token.getText().toString()); }

    private void testLogin() {
        status.setText("Testing...");
        executor.submit(() -> {
            try {
                String response = new ApiClient(this).whoami();
                main.post(() -> status.setText("Login OK: " + abbreviate(response, 240)));
            } catch (Exception e) {
                main.post(() -> status.setText("Login failed: " + e.getMessage()));
            }
        });
    }

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        text = text.trim();
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
