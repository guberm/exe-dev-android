package dev.guber.exedev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneLoginActivity extends Activity {
    private static final String KEY_PRIVATE = "mobile_ssh_private_key";
    private static final String KEY_PUBLIC = "mobile_ssh_public_key";
    private static final int MAX_TRANSCRIPT_CHARS = 24000;
    private static final String TOKEN_COMMAND = "ssh-key generate-api-key \"--cmds=whoami,ls,new,ssh-key add,ssh-key list\" --exp=30d";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\bexe[01]\\.[A-Za-z0-9._~+/=-]{20,}\\b");

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    private EditText sshUser;
    private EditText input;
    private TextView output;
    private Button connectButton;
    private Button sendButton;
    private Button tokenButton;

    private volatile Session session;
    private volatile ChannelShell channel;
    private volatile OutputStream shellInput;
    private final StringBuilder transcript = new StringBuilder();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        AppSettings.Palette p = AppSettings.palette(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(p.background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 32));
        scroll.addView(root);

        LinearLayout hero = Ui.card(this);
        hero.setBackground(Ui.rounded(p.primary, p.primary, 22, this));
        hero.addView(Ui.text(this, "PHONE-ONLY LOGIN", 12, 0xFFEDE9FE, Typeface.BOLD));
        hero.addView(Ui.text(this, "ssh exe.dev on Android", 29, 0xFFFFFFFF, Typeface.BOLD));
        hero.addView(Ui.text(this, "This opens the real exe.dev SSH signup/login flow from the phone. Enter your email and verification code here; no computer needed.", 15, 0xFFEDE9FE, Typeface.NORMAL));
        root.addView(hero);

        LinearLayout flow = Ui.card(this);
        flow.addView(Ui.text(this, "How it works", 18, p.text, Typeface.BOLD));
        flow.addView(Ui.text(this,
                "1. The app generates a mobile SSH key inside private app storage.\n"
                        + "2. It connects to exe.dev over SSH using that key.\n"
                        + "3. Follow exe.dev prompts in the terminal below.\n"
                        + "4. When registration/login finishes, tap Generate and save API token.",
                14, p.muted, Typeface.NORMAL));
        root.addView(flow);

        LinearLayout controls = Ui.card(this);
        controls.addView(Ui.text(this, "Connect", 18, p.text, Typeface.BOLD));
        sshUser = Ui.input(this, "SSH username for exe.dev", "mg", false);
        controls.addView(sshUser);
        connectButton = Ui.button(this, "Connect to ssh exe.dev", true);
        tokenButton = Ui.button(this, "Generate and save API token", false);
        Button copyPublic = Ui.button(this, "Copy mobile public key", false);
        Button disconnect = Ui.button(this, "Disconnect", false);
        controls.addView(connectButton);
        controls.addView(tokenButton);
        controls.addView(copyPublic);
        controls.addView(disconnect);
        root.addView(controls);

        LinearLayout terminal = Ui.card(this);
        terminal.addView(Ui.text(this, "Interactive SSH terminal", 18, p.text, Typeface.BOLD));
        output = Ui.text(this, "Ready. Tap Connect to ssh exe.dev.\n", 13, p.text, Typeface.NORMAL);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setGravity(Gravity.START);
        output.setMinLines(16);
        output.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        output.setBackground(Ui.rounded(p.background, p.border, 12, this));
        input = Ui.input(this, "Type response or command, then tap Send", "", false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        sendButton = Ui.button(this, "Send", true);
        Button copyOutput = Ui.button(this, "Copy terminal output", false);
        terminal.addView(output);
        terminal.addView(input);
        terminal.addView(sendButton);
        terminal.addView(copyOutput);
        root.addView(terminal);

        connectButton.setOnClickListener(v -> connect());
        sendButton.setOnClickListener(v -> sendTypedInput());
        tokenButton.setOnClickListener(v -> sendLine(TOKEN_COMMAND));
        copyPublic.setOnClickListener(v -> {
            try {
                ensureKeyPair();
                copy("Mobile public key", publicKey());
            } catch (Exception e) {
                showPopup("Key generation failed", message(e));
            }
        });
        disconnect.setOnClickListener(v -> disconnect());
        copyOutput.setOnClickListener(v -> copy("Phone login output", transcript.toString()));

        setContentView(scroll);
    }

    private void connect() {
        String user = text(sshUser);
        if (user.isEmpty()) {
            showPopup("Username required", "Type an SSH username first. For exe.dev signup the username is usually not important; mg is the default.");
            return;
        }
        append("\n== connecting to " + user + "@exe.dev ==\n");
        connectButton.setEnabled(false);
        executor.submit(() -> {
            try {
                ensureKeyPair();
                JSch jsch = new JSch();
                jsch.addIdentity("exe-dev-android", privateKey().getBytes(StandardCharsets.UTF_8), null, null);
                Session s = jsch.getSession(user, "exe.dev", 22);
                s.setConfig("StrictHostKeyChecking", "no");
                s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
                s.connect(20000);
                ChannelShell ch = (ChannelShell) s.openChannel("shell");
                ch.setPtyType("dumb");
                ch.setPtySize(120, 36, 960, 720);
                InputStream shellOutput = ch.getInputStream();
                OutputStream shellIn = ch.getOutputStream();
                ch.connect(10000);
                session = s;
                channel = ch;
                shellInput = shellIn;
                main.post(() -> append("Connected. Follow exe.dev prompts below.\n"));
                readLoop(shellOutput);
            } catch (Exception e) {
                main.post(() -> {
                    append("Connection failed: " + message(e) + "\n");
                    showPopup("Phone login failed", message(e));
                    connectButton.setEnabled(true);
                });
                disconnect();
            }
        });
    }

    private void readLoop(InputStream shellOutput) {
        byte[] buf = new byte[4096];
        try {
            while (channel != null && !channel.isClosed()) {
                int n = shellOutput.read(buf);
                if (n < 0) break;
                String chunk = sanitizeTerminalOutput(new String(buf, 0, n, StandardCharsets.UTF_8));
                main.post(() -> append(chunk));
            }
        } catch (Exception e) {
            main.post(() -> append("\nRead loop ended: " + message(e) + "\n"));
        } finally {
            main.post(() -> {
                append("\nDisconnected from exe.dev.\n");
                connectButton.setEnabled(true);
            });
            disconnect();
        }
    }

    private void sendTypedInput() {
        String value = input.getText() == null ? "" : input.getText().toString();
        input.setText("");
        sendLine(value);
    }

    private void sendLine(String value) {
        OutputStream out = shellInput;
        if (out == null) {
            showPopup("Not connected", "Tap Connect to ssh exe.dev first.");
            return;
        }
        executor.submit(() -> {
            try {
                out.write((value + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                main.post(() -> showPopup("Send failed", message(e)));
            }
        });
    }

    private void ensureKeyPair() throws Exception {
        if (!privateKey().isEmpty() && !publicKey().isEmpty()) return;
        JSch jsch = new JSch();
        com.jcraft.jsch.KeyPair kp = com.jcraft.jsch.KeyPair.genKeyPair(jsch, com.jcraft.jsch.KeyPair.RSA, 3072);
        ByteArrayOutputStream priv = new ByteArrayOutputStream();
        ByteArrayOutputStream pub = new ByteArrayOutputStream();
        kp.writePrivateKey(priv);
        kp.writePublicKey(pub, "exe-dev-android");
        kp.dispose();
        AppSettings.prefs(this).edit()
                .putString(KEY_PRIVATE, priv.toString("UTF-8"))
                .putString(KEY_PUBLIC, pub.toString("UTF-8").trim())
                .apply();
    }

    private String privateKey() {
        String value = AppSettings.prefs(this).getString(KEY_PRIVATE, "");
        return value == null ? "" : value;
    }

    private String publicKey() {
        String value = AppSettings.prefs(this).getString(KEY_PUBLIC, "");
        return value == null ? "" : value.trim();
    }

    private void append(String text) {
        if (text == null || text.isEmpty()) return;
        transcript.append(text);
        if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
            transcript.delete(0, transcript.length() - MAX_TRANSCRIPT_CHARS);
            if (transcript.length() > 0) transcript.insert(0, "... output truncated ...\n");
        }
        output.setText(trimConsecutiveBlankLines(transcript.toString()));
        maybeSaveToken();
    }

    private void maybeSaveToken() {
        Matcher m = TOKEN_PATTERN.matcher(transcript);
        String token = null;
        while (m.find()) token = m.group();
        if (token == null || token.equals(AppSettings.token(this))) return;
        AppSettings.save(this, AppSettings.DEFAULT_ENDPOINT, token);
        showPopup("API token saved", "The app found an exe.dev API token in the SSH output and saved it. You can go back and manage VMs from the phone.");
    }

    private void disconnect() {
        try { if (channel != null) channel.disconnect(); } catch (Exception ignored) {}
        try { if (session != null) session.disconnect(); } catch (Exception ignored) {}
        channel = null;
        session = null;
        shellInput = null;
        main.post(() -> connectButton.setEnabled(true));
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private void showPopup(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("COPY", (dialog, which) -> copy(title + " details", title + "\n\n" + message))
                .setPositiveButton("OK", null)
                .show();
    }

    private static String trimConsecutiveBlankLines(String value) {
        if (value == null) return "";
        return value.replaceAll("\\n{4,}", "\\n\\n\\n");
    }

    private static String sanitizeTerminalOutput(String value) {
        if (value == null) return "";
        String cleaned = value
                .replaceAll("\\u001B\\[[0-9;?]*[ -/]*[@-~]", "")
                .replaceAll("\\u001B\\][^\\u0007]*(\\u0007|\\u001B\\\\)", "")
                .replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        boolean lastBlank = false;
        for (String line : cleaned.split("\\n", -1)) {
            if (isDecorativeExeBannerLine(line)) continue;
            boolean blank = line.trim().isEmpty();
            if (blank && lastBlank) continue;
            out.append(line).append('\n');
            lastBlank = blank;
        }
        return out.toString();
    }

    private static boolean isDecorativeExeBannerLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return false;
        int decorative = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ("█╔╗╚╝═║╩╦╠╣╬▀▄▌▐".indexOf(c) >= 0) decorative++;
        }
        return decorative >= 4 || (decorative > 0 && decorative >= trimmed.length() / 3);
    }

    private static String text(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    @Override protected void onDestroy() {
        disconnect();
        executor.shutdownNow();
        super.onDestroy();
    }
}
