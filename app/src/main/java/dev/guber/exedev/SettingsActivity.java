package dev.guber.exedev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
    private static final String TOKEN_COMMAND = "ssh exe.dev ssh-key generate-api-key \"--cmds=whoami,ls,new,ssh-key add,ssh-key list\" --exp=30d";
    private static final String API_DOCS_URL = "https://exe.dev/docs/https-api";

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

        root.addView(Ui.text(this, "Login to exe.dev", 28, p.text, Typeface.BOLD));
        root.addView(Ui.text(this, "exe.dev does not provide a normal mobile username/password login. This app logs in with an official HTTPS API bearer token.", 14, p.muted, Typeface.NORMAL));
        Ui.addSpacer(root, 14);

        LinearLayout wizard = Ui.card(this);
        wizard.addView(Ui.text(this, "3-step login wizard", 19, p.text, Typeface.BOLD));
        wizard.addView(Ui.text(this, "Step 1 - Best option: tap Login via SSH on this phone and complete exe.dev signup here. Fallback if you already have a trusted computer:", 14, p.muted, Typeface.NORMAL));
        wizard.addView(codeBlock(TOKEN_COMMAND));
        wizard.addView(Ui.text(this, "Step 2 - Copy the token printed by exe.dev. It usually starts with exe1. or exe0. Do not send it in chat - it works like a password.", 14, p.muted, Typeface.NORMAL));
        wizard.addView(Ui.text(this, "Step 3 - Paste the token below, keep the default endpoint, then tap Save and test login.", 14, p.muted, Typeface.NORMAL));
        Button phoneLogin = Ui.button(this, "Login via SSH on this phone", true);
        Button copyCommand = Ui.button(this, "Copy terminal command", false);
        Button openDocs = Ui.button(this, "Open exe.dev API docs", false);
        wizard.addView(phoneLogin);
        wizard.addView(copyCommand);
        wizard.addView(openDocs);
        root.addView(wizard);

        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Token login", 18, p.text, Typeface.BOLD));
        endpoint = Ui.input(this, "API endpoint", AppSettings.endpoint(this), false);
        token = Ui.input(this, "Paste bearer token here", AppSettings.token(this), false);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        card.addView(endpoint);
        card.addView(token);
        Button paste = Ui.button(this, "Paste token from clipboard", false);
        Button save = Ui.button(this, "Save", false);
        Button test = Ui.button(this, "Save and test login", true);
        status = Ui.text(this, "Not tested yet", 14, p.muted, Typeface.NORMAL);
        card.addView(paste); card.addView(save); card.addView(test); card.addView(status);
        root.addView(card);

        LinearLayout troubleshooting = Ui.card(this);
        troubleshooting.addView(Ui.text(this, "If login fails", 18, p.text, Typeface.BOLD));
        troubleshooting.addView(Ui.text(this, "- Check that you pasted the entire token, with no spaces before or after it.\n- Generate a fresh token if the old one expired.\n- The default endpoint should stay https://exe.dev/exec.\n- If you restricted token permissions, allow at least whoami, ls, and new.", 14, p.muted, Typeface.NORMAL));
        root.addView(troubleshooting);

        phoneLogin.setOnClickListener(v -> startActivity(new Intent(this, PhoneLoginActivity.class)));
        copyCommand.setOnClickListener(v -> copy("exe.dev token command", TOKEN_COMMAND));
        openDocs.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(API_DOCS_URL))));
        paste.setOnClickListener(v -> pasteToken());
        save.setOnClickListener(v -> { persist(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); });
        test.setOnClickListener(v -> { persist(); testLogin(); });
        setContentView(scroll);
    }

    private TextView codeBlock(String value) {
        AppSettings.Palette p = AppSettings.palette(this);
        TextView code = Ui.text(this, value, 14, p.text, Typeface.BOLD);
        code.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        code.setBackground(Ui.rounded(p.background, p.border, 10, this));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeLp.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        code.setLayoutParams(codeLp);
        return code;
    }

    private void persist() {
        AppSettings.save(this, endpoint.getText().toString(), token.getText().toString());
    }

    private void pasteToken() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Clipboard has no token text", Toast.LENGTH_SHORT).show();
            return;
        }
        token.setText(text.toString().trim());
        Toast.makeText(this, "Token pasted", Toast.LENGTH_SHORT).show();
    }

    private void testLogin() {
        String tokenValue = token.getText() == null ? "" : token.getText().toString().trim();
        if (tokenValue.isEmpty()) {
            String message = "Paste your exe.dev bearer token first. Use the command in Step 1 to generate it.";
            status.setText(message);
            showPopup("Token required", message);
            return;
        }
        if (!tokenValue.startsWith("exe")) {
            String message = "This does not look like an exe.dev token. Tokens normally start with exe1. or exe0.";
            status.setText(message);
            showPopup("Invalid token", message);
            return;
        }
        status.setText("Testing login with whoami...");
        executor.submit(() -> {
            try {
                String response = new ApiClient(this).whoami();
                main.post(() -> status.setText("Login OK. You can go back and manage VMs. Response: " + abbreviate(response, 220)));
            } catch (Exception e) {
                main.post(() -> {
                    String message = formatError(e);
                    status.setText("Login failed: " + message);
                    showPopup("Login failed", message);
                });
            }
        });
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private String formatError(Exception e) {
        if (e instanceof ApiClient.ApiException) {
            ApiClient.ApiException api = (ApiClient.ApiException) e;
            if (api.statusCode == 403) {
                return "HTTP 403: this token is valid, but it is not allowed to run the requested exe.dev command. Generate a new token with the command shown in Step 1, then paste it here.";
            }
            if (api.statusCode == 422 && e.getMessage() != null && e.getMessage().toLowerCase().contains("ssh")) {
                return "HTTP 422: exe.dev's HTTPS API cannot run ssh commands inside a VM. VM shell commands require a real SSH session.";
            }
        }
        return (e.getMessage() == null ? e.toString() : e.getMessage()) + "\n\nCheck the token, endpoint, and token permissions.";
    }

    private void showPopup(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("COPY", (dialog, which) -> copy(title + " details", title + "\n\n" + message))
                .setPositiveButton("OK", null)
                .show();
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
