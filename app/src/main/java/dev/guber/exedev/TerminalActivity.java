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
import android.util.Base64;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalActivity extends Activity {
    private static final String KEY_PRIVATE = "mobile_ssh_private_key";
    private static final String KEY_PUBLIC = "mobile_ssh_public_key";
    private static final String KEY_USER = "ssh_user";
    private static final int MAX_TRANSCRIPT_CHARS = 28000;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder transcript = new StringBuilder();

    private TextView title;
    private TextView status;
    private TextView output;
    private EditText input;
    private Button connectButton;
    private Button sendButton;
    private String host;
    private String user;
    private volatile Session session;
    private volatile ChannelShell channel;
    private volatile OutputStream shellInput;
    private volatile boolean connecting;
    private volatile boolean previewStarted;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        host = valueOrEmpty(getIntent().getStringExtra("host"));
        user = AppSettings.prefs(this).getString(KEY_USER, "michael.guber");
        if (user == null || user.trim().isEmpty()) user = "michael.guber";
        buildUi();
        main.postDelayed(this::connectTerminal, 250);
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
        hero.addView(Ui.text(this, "VM TERMINAL", 12, 0xFFEDE9FE, Typeface.BOLD));
        title = Ui.text(this, host.isEmpty() ? "exe.dev VM" : host, 28, 0xFFFFFFFF, Typeface.BOLD);
        hero.addView(title);
        hero.addView(Ui.text(this, "Type commands below and press Send. The app opens SSH directly from the phone and starts the port 8000 preview service automatically.", 15, 0xFFEDE9FE, Typeface.NORMAL));
        root.addView(hero);

        LinearLayout terminal = Ui.card(this);
        status = Ui.text(this, "Opening SSH terminal...", 14, p.muted, Typeface.NORMAL);
        output = Ui.text(this, "", 13, p.text, Typeface.NORMAL);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setGravity(Gravity.START);
        output.setMinLines(18);
        output.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        output.setBackground(Ui.rounded(p.background, p.border, 12, this));

        input = Ui.input(this, "Type command, e.g. ls -la", "", false);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTypedCommand();
                return true;
            }
            return false;
        });

        sendButton = Ui.button(this, "Send", true);
        Button ctrlC = Ui.button(this, "Ctrl-C", false);
        connectButton = Ui.button(this, "Reconnect", false);
        Button clear = Ui.button(this, "Clear", false);
        Button copyOut = Ui.button(this, "Copy output", false);

        terminal.addView(status);
        terminal.addView(output);
        terminal.addView(input);
        terminal.addView(sendButton);
        terminal.addView(ctrlC);
        terminal.addView(connectButton);
        terminal.addView(clear);
        terminal.addView(copyOut);
        root.addView(terminal);

        sendButton.setOnClickListener(v -> sendTypedCommand());
        ctrlC.setOnClickListener(v -> sendControlC());
        connectButton.setOnClickListener(v -> connectTerminal());
        clear.setOnClickListener(v -> { transcript.setLength(0); renderTranscript(); });
        copyOut.setOnClickListener(v -> copy("Terminal output", transcript.toString()));

        setContentView(scroll);
        setConnectedUi(false);
        append("Opening " + (host.isEmpty() ? "VM" : host) + "...\n");
    }

    private void connectTerminal() {
        if (connecting || isConnected()) return;
        if (host.isEmpty()) {
            showPopup("Missing SSH host", "This VM has no SSH destination in the API response.");
            return;
        }
        connecting = true;
        setStatus("Connecting to " + user + "@" + host + "...");
        setConnectedUi(false);
        append("\n== connecting to " + user + "@" + host + " ==\n");
        executor.submit(() -> {
            try {
                ensureKeyPair();
                try {
                    openShell();
                } catch (Exception first) {
                    if (AppSettings.hasValidToken(this)) {
                        main.post(() -> append("SSH was not ready; registering the phone key with exe.dev and retrying...\n"));
                        registerMobileKeyViaApi();
                        openShell();
                    } else {
                        throw first;
                    }
                }
            } catch (Exception e) {
                main.post(() -> {
                    String msg = message(e);
                    append("Connection failed: " + msg + "\n");
                    setStatus("Not connected. Open Login / Settings if this phone key is not trusted yet.");
                    setConnectedUi(false);
                    if (needsLogin(msg)) {
                        showPopup("Login needed", "This phone key is not trusted by exe.dev yet. Go back to Login / Settings, tap Login via SSH on this phone, and the app will save the token/key automatically.\n\nDetails:\n" + msg);
                    } else {
                        showPopup("SSH connect failed", msg);
                    }
                });
                disconnect(false);
            } finally {
                connecting = false;
                main.post(() -> setConnectedUi(isConnected()));
            }
        });
    }

    private void openShell() throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity("exe-dev-android", privateKey().getBytes(StandardCharsets.UTF_8), null, null);
        Session s = jsch.getSession(user, host, 22);
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
        main.post(() -> {
            setStatus("Connected. Type a command and press Send.");
            setConnectedUi(true);
            append("Connected.\n");
        });
        readLoop(shellOutput);
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
            main.post(() -> append("\nDisconnected.\n"));
            disconnect(false);
        }
    }

    private void sendTypedCommand() {
        String value = input.getText() == null ? "" : input.getText().toString();
        if (value.trim().isEmpty()) return;
        input.setText("");
        sendLine(value);
    }

    private void sendLine(String value) {
        OutputStream out = shellInput;
        if (out == null || !isConnected()) {
            append("Not connected yet. Reconnecting...\n");
            connectTerminal();
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

    private void sendControlC() {
        OutputStream out = shellInput;
        if (out == null || !isConnected()) return;
        executor.submit(() -> {
            try {
                out.write(3);
                out.flush();
            } catch (Exception e) {
                main.post(() -> showPopup("Ctrl-C failed", message(e)));
            }
        });
    }

    private void registerMobileKeyViaApi() throws Exception {
        ensureKeyPair();
        String response = new ApiClient(this).exec("ssh-key add " + ApiClient.shellQuote(publicKey()));
        main.post(() -> append("Mobile SSH key registered.\n" + abbreviate(response, 500) + "\n"));
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

    private boolean isConnected() {
        return channel != null && channel.isConnected() && !channel.isClosed() && shellInput != null;
    }

    private void setConnectedUi(boolean connected) {
        if (sendButton != null) sendButton.setEnabled(connected);
        if (connectButton != null) connectButton.setEnabled(!connected && !connecting);
    }

    private void setStatus(String value) {
        if (status != null) status.setText(value);
    }

    private void append(String text) {
        if (text == null || text.isEmpty()) return;
        transcript.append(text);
        if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
            transcript.delete(0, transcript.length() - MAX_TRANSCRIPT_CHARS);
            if (transcript.length() > 0) transcript.insert(0, "... output truncated ...\n");
        }
        renderTranscript();
        maybeStartPreviewService();
    }

    private void maybeStartPreviewService() {
        if (previewStarted || !isConnected()) return;
        String text = transcript.toString();
        boolean hasPrompt = text.matches("(?s).*\\n[^\\n]*@[^\\n:]+:[^\\n]*\\$\\s*$.*")
                || text.contains("exedev@")
                || text.contains("$ ");
        if (!hasPrompt) return;
        previewStarted = true;
        setStatus("Connected. Starting port 8000 preview service automatically...");
        append("\n== auto-starting port 8000 preview service ==\n");
        executor.submit(() -> {
            try {
                String result = execSshCommand(previewCommand());
                main.post(() -> {
                    append("Preview service setup finished.\n" + abbreviate(result, 700) + "\n");
                    setStatus("Connected. Preview service is ready on port 8000.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    append("Preview service auto-setup failed: " + message(e) + "\n");
                    setStatus("Connected. Preview auto-setup failed; terminal still works.");
                });
            }
        });
    }

    private void renderTranscript() {
        if (output != null) output.setText(trimConsecutiveBlankLines(transcript.toString()));
    }

    private String execSshCommand(String cmd) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity("exe-dev-android-preview", privateKey().getBytes(StandardCharsets.UTF_8), null, null);
        Session s = jsch.getSession(user, host, 22);
        s.setConfig("StrictHostKeyChecking", "no");
        s.connect(20000);
        ChannelExec exec = null;
        StringBuilder out = new StringBuilder();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            exec = (ChannelExec) s.openChannel("exec");
            exec.setPty(true);
            exec.setCommand(cmd);
            exec.setErrStream(err);
            InputStream in = exec.getInputStream();
            exec.connect(10000);
            byte[] buf = new byte[4096];
            long deadline = System.currentTimeMillis() + 120000;
            while (!exec.isClosed()) {
                while (in.available() > 0) {
                    int n = in.read(buf, 0, buf.length);
                    if (n < 0) break;
                    out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                if (System.currentTimeMillis() > deadline) {
                    exec.disconnect();
                    throw new RuntimeException("SSH setup timed out after 120 seconds. Output so far:\n" + out);
                }
                Thread.sleep(100);
            }
            while (in.available() > 0) {
                int n = in.read(buf, 0, buf.length);
                if (n < 0) break;
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            String stderr = err.toString("UTF-8");
            if (!stderr.trim().isEmpty()) out.append(stderr);
            out.append("\n[exit ").append(exec.getExitStatus()).append("]");
            return sanitizeTerminalOutput(out.toString());
        } finally {
            if (exec != null) exec.disconnect();
            s.disconnect();
        }
    }

    private String previewCommand() {
        String encoded = Base64.encodeToString(previewServerScript().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "echo " + encoded + " | base64 -d | sudo sh";
    }

    private String previewServerScript() {
        return "set -e\n"
                + "mkdir -p /opt/exe-preview\n"
                + "cat > /opt/exe-preview/index.html <<'HTML'\n"
                + "<!doctype html><html><head><title>exe.dev preview</title></head><body><h1>exe.dev preview is running</h1><p>Started from the Android terminal on port 8000.</p></body></html>\n"
                + "HTML\n"
                + "cat > /etc/systemd/system/exe-preview.service <<'UNIT'\n"
                + "[Unit]\n"
                + "Description=exe.dev Android preview server\n"
                + "After=network.target\n\n"
                + "[Service]\n"
                + "WorkingDirectory=/opt/exe-preview\n"
                + "ExecStart=/usr/bin/python3 -m http.server 8000 --bind 0.0.0.0\n"
                + "Restart=always\n\n"
                + "[Install]\n"
                + "WantedBy=multi-user.target\n"
                + "UNIT\n"
                + "systemctl daemon-reload\n"
                + "systemctl enable --now exe-preview.service\n"
                + "systemctl is-active exe-preview.service\n";
    }

    private void disconnect(boolean userRequested) {
        try { if (channel != null) channel.disconnect(); } catch (Exception ignored) {}
        try { if (session != null) session.disconnect(); } catch (Exception ignored) {}
        channel = null;
        session = null;
        shellInput = null;
        main.post(() -> {
            setConnectedUi(false);
            if (userRequested) setStatus("Disconnected.");
        });
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
        return value.replaceAll("\n{4,}", "\n\n\n");
    }

    private static String sanitizeTerminalOutput(String value) {
        if (value == null) return "";
        return value
                .replaceAll("\\u001B\\[[0-9;?]*[ -/]*[@-~]", "")
                .replaceAll("\\u001B\\][^\\u0007]*(\\u0007|\\u001B\\\\)", "")
                .replace('\r', '\n');
    }

    private static boolean needsLogin(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("auth fail") || lower.contains("publickey") || lower.contains("permission denied") || lower.contains("invalid token");
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        text = text.trim();
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    @Override protected void onDestroy() {
        disconnect(false);
        executor.shutdownNow();
        super.onDestroy();
    }
}
