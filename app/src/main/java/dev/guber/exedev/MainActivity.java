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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout vmList;
    private TextView status;
    private EditText name, image, cpu, memory, disk, tags, env, integrations, comment, setupScript, prompt;
    private CheckBox noEmail;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        updateLoginStatus();
        if (AppSettings.hasToken(this)) refreshVms();
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
        TextView badge = Ui.text(this, "SSH-FIRST VM CLOUD", 12, 0xFFEDE9FE, Typeface.BOLD);
        badge.setGravity(Gravity.START);
        hero.addView(badge);
        hero.addView(Ui.text(this, "exe.dev Mobile", 30, 0xFFFFFFFF, Typeface.BOLD));
        hero.addView(Ui.text(this, "Log in with an HTTPS API token, create persistent VMs, and connect to existing machines from Android.", 15, 0xFFEDE9FE, Typeface.NORMAL));
        root.addView(hero);

        LinearLayout account = Ui.card(this);
        account.addView(Ui.text(this, "Account", 19, p.text, Typeface.BOLD));
        status = Ui.text(this, "Not configured", 14, p.muted, Typeface.NORMAL);
        Button settings = Ui.button(this, "Login / Settings", true);
        Button test = Ui.button(this, "Test login", false);
        account.addView(status); account.addView(settings); account.addView(test);
        root.addView(account);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        test.setOnClickListener(v -> testLogin());

        LinearLayout existing = Ui.card(this);
        existing.addView(Ui.text(this, "Existing VMs", 19, p.text, Typeface.BOLD));
        existing.addView(Ui.text(this, "Refresh your VM list, then copy the SSH command or open the HTTPS URL for a selected machine.", 14, p.muted, Typeface.NORMAL));
        Button refresh = Ui.button(this, "Refresh VMs", true);
        existing.addView(refresh);
        vmList = new LinearLayout(this);
        vmList.setOrientation(LinearLayout.VERTICAL);
        existing.addView(vmList);
        root.addView(existing);
        refresh.setOnClickListener(v -> refreshVms());

        LinearLayout create = Ui.card(this);
        create.addView(Ui.text(this, "Create a new VM", 19, p.text, Typeface.BOLD));
        create.addView(Ui.text(this, "Only fill the fields you need. Empty fields are omitted from the exe.dev new command.", 14, p.muted, Typeface.NORMAL));
        name = Ui.input(this, "VM name, optional", "", false);
        image = Ui.input(this, "Container image, optional, e.g. ubuntu:22.04", "", false);
        cpu = Ui.input(this, "CPU, optional, e.g. 4", "", false);
        memory = Ui.input(this, "Memory, optional, e.g. 8GB", "", false);
        disk = Ui.input(this, "Disk, optional, e.g. 50GB", "", false);
        tags = Ui.input(this, "Tags, optional, comma-separated", "", false);
        env = Ui.input(this, "Environment variables, optional, one KEY=VALUE per line", "", true);
        integrations = Ui.input(this, "Integrations, optional, comma-separated", "", false);
        comment = Ui.input(this, "Comment, optional", "", false);
        setupScript = Ui.input(this, "Setup script, optional", "", true);
        prompt = Ui.input(this, "Shelley prompt, optional", "", true);
        noEmail = new CheckBox(this);
        noEmail.setText("Do not send email notification");
        noEmail.setTextColor(p.text);
        noEmail.setButtonTintList(android.content.res.ColorStateList.valueOf(p.primary));
        Button createBtn = Ui.button(this, "Create VM", true);
        create.addView(name); create.addView(image); create.addView(cpu); create.addView(memory); create.addView(disk);
        create.addView(tags); create.addView(env); create.addView(integrations); create.addView(comment); create.addView(setupScript); create.addView(prompt);
        create.addView(noEmail); create.addView(createBtn);
        root.addView(create);
        createBtn.setOnClickListener(v -> createVm());

        setContentView(scroll);
    }

    private void updateLoginStatus() {
        if (status == null) return;
        if (AppSettings.hasToken(this)) {
            status.setText("Configured endpoint: " + AppSettings.endpoint(this));
        } else {
            status.setText("Not logged in. Open Settings and paste an exe.dev API token.");
        }
    }

    private void testLogin() {
        runTask("Testing login...", () -> new ApiClient(this).whoami(), result -> setStatus("Login OK: " + shorten(result, 220)));
    }

    private void refreshVms() {
        setListMessage("Loading VMs...");
        executor.submit(() -> {
            try {
                List<ApiClient.Vm> vms = new ApiClient(this).listVms();
                main.post(() -> renderVms(vms));
            } catch (Exception e) {
                main.post(() -> setListMessage("Could not load VMs: " + e.getMessage()));
            }
        });
    }

    private void renderVms(List<ApiClient.Vm> vms) {
        vmList.removeAllViews();
        if (vms.isEmpty()) {
            setListMessage("No VMs returned by exe.dev.");
            return;
        }
        for (ApiClient.Vm vm : vms) vmList.addView(vmRow(vm));
    }

    private View vmRow(ApiClient.Vm vm) {
        AppSettings.Palette p = AppSettings.palette(this);
        LinearLayout row = Ui.card(this);
        TextView title = Ui.text(this, safe(vm.name, "Unnamed VM"), 17, p.text, Typeface.BOLD);
        row.addView(title);
        String info = "Status: " + safe(vm.status, "unknown")
                + "\nRegion: " + safe(vm.region, "unknown")
                + "\nSSH: " + sshCommand(vm)
                + (vm.httpsUrl == null || vm.httpsUrl.isEmpty() ? "" : "\nHTTPS: " + vm.httpsUrl);
        row.addView(Ui.text(this, info, 14, p.muted, Typeface.NORMAL));
        row.addView(Ui.text(this, "HTTPS opens exe.dev's default web preview, which dials port 8000. If you see 'Port 8000 unbound', start a web server on the VM first. The preview-server button below creates a tiny systemd service that serves a basic test page on port 8000.", 13, p.muted, Typeface.NORMAL));
        Button copySsh = Ui.button(this, "Copy SSH command", true);
        Button startPreview = Ui.button(this, "Start port 8000 preview server", false);
        Button openWeb = Ui.button(this, "Open HTTPS preview", false);
        Button details = Ui.button(this, "Copy VM JSON", false);
        row.addView(copySsh); row.addView(startPreview); row.addView(openWeb); row.addView(details);
        copySsh.setOnClickListener(v -> copy("SSH command", sshCommand(vm)));
        startPreview.setOnClickListener(v -> startPreviewServer(vm));
        openWeb.setOnClickListener(v -> {
            if (vm.httpsUrl == null || vm.httpsUrl.isEmpty()) Toast.makeText(this, "This VM has no HTTPS URL in the API response.", Toast.LENGTH_LONG).show();
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(vm.httpsUrl)));
        });
        details.setOnClickListener(v -> copy("VM JSON", vm.raw));
        return row;
    }

    private void createVm() {
        ApiClient.NewVmRequest r = new ApiClient.NewVmRequest();
        r.name = text(name); r.image = text(image); r.cpu = text(cpu); r.memory = text(memory); r.disk = text(disk);
        r.tagsCsv = text(tags); r.envLines = text(env); r.integrationsCsv = text(integrations); r.comment = text(comment);
        r.setupScript = text(setupScript); r.prompt = text(prompt); r.noEmail = noEmail.isChecked();
        String cmd = ApiClient.buildNewCommand(r);
        runTask("Creating VM with: " + cmd, () -> new ApiClient(this).exec(cmd), result -> {
            setStatus("Create VM response: " + shorten(result, 260));
            refreshVms();
        });
    }

    private void startPreviewServer(ApiClient.Vm vm) {
        String target = sshTarget(vm);
        if (target.isEmpty()) {
            setStatus("Cannot start preview server: this VM has no name or SSH destination in the API response.");
            return;
        }
        String script = "mkdir -p /opt/exe-preview"
                + " && printf '%s\\n' '<!doctype html><html><head><title>exe.dev preview</title></head><body><h1>exe.dev preview is running</h1><p>Started from the Android app on port 8000.</p></body></html>' > /opt/exe-preview/index.html"
                + " && printf '%s\\n' '[Unit]' 'Description=exe.dev Android preview server' 'After=network.target' '' '[Service]' 'WorkingDirectory=/opt/exe-preview' 'ExecStart=/usr/bin/python3 -m http.server 8000 --bind 0.0.0.0' 'Restart=always' '' '[Install]' 'WantedBy=multi-user.target' > /etc/systemd/system/exe-preview.service"
                + " && systemctl daemon-reload"
                + " && systemctl enable --now exe-preview.service"
                + " && systemctl is-active exe-preview.service";
        String cmd = "ssh " + ApiClient.shellQuote(target) + " sh -lc " + ApiClient.shellQuote(script);
        runTask("Starting preview server on " + target + ":8000...", () -> new ApiClient(this).exec(cmd), result -> {
            setStatus("Preview server started on port 8000. Now tap Open HTTPS preview. Response: " + shorten(result, 220));
            refreshVms();
        });
    }

    private interface ThrowingSupplier { String get() throws Exception; }
    private interface ResultConsumer { void accept(String value); }

    private void runTask(String message, ThrowingSupplier supplier, ResultConsumer consumer) {
        setStatus(message);
        executor.submit(() -> {
            try {
                String result = supplier.get();
                main.post(() -> consumer.accept(result));
            } catch (Exception e) {
                main.post(() -> {
                    String formatted = formatError(e);
                    setStatus("Error: " + formatted);
                    showPopup("Action failed", formatted);
                });
            }
        });
    }

    private String formatError(Exception e) {
        if (e instanceof ApiClient.ApiException) {
            ApiClient.ApiException api = (ApiClient.ApiException) e;
            if (api.statusCode == 403) {
                return "HTTP 403: this token is not allowed to run that exe.dev command.\n\nFor the port 8000 preview helper, generate a new token with the ssh command allowed:\n\nssh exe.dev ssh-key generate-api-key --cmds=whoami,ls,new,ssh --exp=30d\n\nThen paste it in Login / Settings.";
            }
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private void showPopup(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void setStatus(String value) { if (status != null) status.setText(value); }

    private void setListMessage(String value) {
        vmList.removeAllViews();
        vmList.addView(Ui.text(this, value, 14, AppSettings.palette(this).muted, Typeface.NORMAL));
    }

    private String sshCommand(ApiClient.Vm vm) {
        String target = sshTarget(vm);
        if (!target.isEmpty()) return "ssh " + target;
        return "ssh exe.dev";
    }

    private String sshTarget(ApiClient.Vm vm) {
        if (vm.sshDest != null && !vm.sshDest.isEmpty()) return vm.sshDest;
        if (vm.name != null && !vm.name.isEmpty()) return vm.name;
        return "";
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private static String text(EditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }
    private static String safe(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }
    private static String shorten(String value, int max) {
        if (value == null) return "";
        value = value.trim();
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
