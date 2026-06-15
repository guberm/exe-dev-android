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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalActivity extends Activity {
    private static final String KEY_PRIVATE = "mobile_ssh_private_key";
    private static final String KEY_PUBLIC = "mobile_ssh_public_key";
    private static final String KEY_USER = "ssh_user";
    private static final int SSH_TIMEOUT_MS = 120000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private EditText host;
    private EditText user;
    private EditText command;
    private TextView output;

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
        hero.addView(Ui.text(this, "SSH TERMINAL", 12, 0xFFEDE9FE, Typeface.BOLD));
        hero.addView(Ui.text(this, "exe.dev Terminal", 30, 0xFFFFFFFF, Typeface.BOLD));
        hero.addView(Ui.text(this, "Generate a mobile SSH key, register it with exe.dev, then run commands on a VM directly from Android.", 15, 0xFFEDE9FE, Typeface.NORMAL));
        root.addView(hero);

        LinearLayout setup = Ui.card(this);
        setup.addView(Ui.text(this, "Mobile SSH setup", 19, p.text, Typeface.BOLD));
        setup.addView(Ui.text(this, "This creates an SSH key inside the app and adds its public key to exe.dev using your saved HTTPS API token. Your token must allow the ssh-key add command.", 14, p.muted, Typeface.NORMAL));
        Button setupKey = Ui.button(this, "Generate/register mobile SSH key", true);
        Button copyPub = Ui.button(this, "Copy mobile public key", false);
        setup.addView(setupKey);
        setup.addView(copyPub);
        root.addView(setup);
        setupKey.setOnClickListener(v -> registerMobileKey());
        copyPub.setOnClickListener(v -> {
            String pub = publicKey();
            if (pub.isEmpty()) showPopup("No mobile key", "Generate the mobile SSH key first.");
            else copy("Mobile public key", pub);
        });

        LinearLayout terminal = Ui.card(this);
        terminal.addView(Ui.text(this, "Run SSH command", 19, p.text, Typeface.BOLD));
        terminal.addView(Ui.text(this, "Host should look like electron-futon.exe.xyz. Username defaults to michael.guber because that is the working Windows SSH username for this VM; change it if exe.dev expects a different username.", 14, p.muted, Typeface.NORMAL));
        String defaultHost = getIntent().getStringExtra("host");
        host = Ui.input(this, "SSH host, e.g. electron-futon.exe.xyz", defaultHost == null ? "" : defaultHost, false);
        user = Ui.input(this, "SSH username", AppSettings.prefs(this).getString(KEY_USER, "michael.guber"), false);
        command = Ui.input(this, "Command", "uname -a && whoami && pwd", true);
        command.setMinLines(4);
        command.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Button run = Ui.button(this, "Run command", true);
        Button preview = Ui.button(this, "Start port 8000 preview server", false);
        Button clear = Ui.button(this, "Clear output", false);
        Button copyOut = Ui.button(this, "Copy output", false);
        terminal.addView(host);
        terminal.addView(user);
        terminal.addView(command);
        terminal.addView(run);
        terminal.addView(preview);
        terminal.addView(clear);
        terminal.addView(copyOut);
        output = Ui.text(this, "Ready. First run mobile SSH setup if this app has not been added to exe.dev yet.", 13, p.text, Typeface.MONOSPACE.getStyle());
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setGravity(Gravity.START);
        output.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        output.setBackground(Ui.rounded(p.background, p.border, 12, this));
        terminal.addView(output);
        root.addView(terminal);

        run.setOnClickListener(v -> runTypedCommand());
        preview.setOnClickListener(v -> runPreviewCommand());
        clear.setOnClickListener(v -> output.setText(""));
        copyOut.setOnClickListener(v -> copy("Terminal output", output.getText().toString()));

        setContentView(scroll);
    }

    private void registerMobileKey() {
        append("\n== mobile SSH setup ==\n");
        executor.submit(() -> {
            try {
                ensureKeyPair();
                String pub = publicKey();
                String cmd = "ssh-key add " + ApiClient.shellQuote(pub);
                String response = new ApiClient(this).exec(cmd);
                main.post(() -> {
                    append("Registered mobile SSH public key with exe.dev.\n" + response + "\n");
                    showPopup("Mobile SSH ready", "The app generated an SSH key and added its public key to exe.dev. You can now run VM commands from this terminal.\n\nPublic key:\n" + pub);
                });
            } catch (Exception e) {
                main.post(() -> {
                    String msg = formatSetupError(e);
                    append("Mobile SSH setup failed: " + msg + "\n");
                    showPopup("Mobile SSH setup failed", msg);
                });
            }
        });
    }

    private void runTypedCommand() {
        String cmd = text(command);
        if (cmd.isEmpty()) {
            showPopup("Missing command", "Type a command first.");
            return;
        }
        runSshCommand(cmd);
    }

    private void runPreviewCommand() {
        String encoded = Base64.encodeToString(previewServerScript().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String cmd = "echo " + encoded + " | base64 -d | sudo sh";
        command.setText(cmd);
        runSshCommand(cmd);
    }

    private void runSshCommand(String cmd) {
        String h = text(host);
        String u = text(user);
        if (h.isEmpty()) {
            showPopup("Missing host", "Paste a VM SSH host first, for example electron-futon.exe.xyz.");
            return;
        }
        if (u.isEmpty()) {
            showPopup("Missing username", "Type the SSH username first.");
            return;
        }
        String priv = privateKey();
        if (priv.isEmpty()) {
            showPopup("Mobile SSH key required", "Tap Generate/register mobile SSH key first, or paste/import a key in a future version.");
            return;
        }
        AppSettings.prefs(this).edit().putString(KEY_USER, u).apply();
        append("\n$ " + cmd + "\n");
        executor.submit(() -> {
            try {
                String result = execSsh(h, u, priv, cmd);
                main.post(() -> append(result + "\n"));
            } catch (Exception e) {
                main.post(() -> {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    append("ERROR: " + msg + "\n");
                    showPopup("SSH command failed", msg);
                });
            }
        });
    }

    private String execSsh(String h, String u, String privateKey, String cmd) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity("exe-dev-android", privateKey.getBytes(StandardCharsets.UTF_8), null, null);
        Session session = jsch.getSession(u, h, 22);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(15000);
        ChannelExec channel = null;
        StringBuilder out = new StringBuilder();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setPty(true);
            channel.setCommand(cmd);
            channel.setErrStream(err);
            InputStream in = channel.getInputStream();
            channel.connect(10000);
            byte[] buf = new byte[4096];
            long deadline = System.currentTimeMillis() + SSH_TIMEOUT_MS;
            while (!channel.isClosed()) {
                while (in.available() > 0) {
                    int n = in.read(buf, 0, buf.length);
                    if (n < 0) break;
                    out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect();
                    throw new RuntimeException("SSH command timed out after " + (SSH_TIMEOUT_MS / 1000) + " seconds. Output so far:\n" + out);
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
            out.append("\n[exit ").append(channel.getExitStatus()).append("]");
            return out.toString();
        } finally {
            if (channel != null) channel.disconnect();
            session.disconnect();
        }
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

    private String formatSetupError(Exception e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        if (msg.contains("HTTP 403")) {
            return msg + "\n\nYour API token probably does not allow ssh-key add. Generate a token with:\n\nssh exe.dev ssh-key generate-api-key \"--cmds=whoami,ls,new,ssh-key add,ssh-key list\" --exp=30d\n\nThen paste it in Login / Settings and retry mobile SSH setup.";
        }
        return msg;
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

    private void append(String text) {
        output.append(text);
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

    private static String text(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
