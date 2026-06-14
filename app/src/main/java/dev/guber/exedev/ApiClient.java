package dev.guber.exedev;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ApiClient {
    private final String endpoint;
    private final String token;

    public ApiClient(Context context) {
        this.endpoint = AppSettings.endpoint(context);
        this.token = AppSettings.token(context);
    }

    public String exec(String command) throws Exception {
        if (token.isEmpty()) throw new IllegalStateException("Missing API token. Open Settings and paste an exe.dev bearer token.");
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(35000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        conn.setRequestProperty("User-Agent", "ExeDevAndroid/1.0");
        byte[] body = command.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = read(stream);
        if (code < 200 || code >= 300) {
            throw new ApiException(code, response == null || response.isEmpty() ? conn.getResponseMessage() : response);
        }
        return response == null ? "" : response.trim();
    }

    public String whoami() throws Exception { return exec("whoami"); }
    public String listRaw() throws Exception { return exec("ls -l"); }
    public List<Vm> listVms() throws Exception { return parseVms(listRaw()); }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static List<Vm> parseVms(String raw) throws JSONException {
        List<Vm> out = new ArrayList<>();
        Object root = raw.trim().startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
        JSONArray arr;
        if (root instanceof JSONArray) arr = (JSONArray) root;
        else {
            JSONObject obj = (JSONObject) root;
            if (obj.has("vms")) arr = obj.getJSONArray("vms");
            else if (obj.has("data")) arr = obj.getJSONArray("data");
            else {
                arr = new JSONArray();
                arr.put(obj);
            }
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Vm vm = new Vm();
            vm.name = first(o, "vm_name", "name", "id");
            vm.status = first(o, "status", "state");
            vm.region = first(o, "region_display", "region");
            vm.sshDest = first(o, "ssh_dest", "ssh", "host");
            vm.httpsUrl = first(o, "https_url", "url", "web_url");
            vm.raw = o.toString(2);
            out.add(vm);
        }
        return out;
    }

    private static String first(JSONObject o, String... keys) {
        for (String key : keys) {
            String v = o.optString(key, "");
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    public static String buildNewCommand(NewVmRequest r) {
        StringBuilder cmd = new StringBuilder("new");
        addFlag(cmd, "--name", r.name);
        addFlag(cmd, "--image", r.image);
        addFlag(cmd, "--cpu", r.cpu);
        addFlag(cmd, "--memory", r.memory);
        addFlag(cmd, "--disk", r.disk);
        addRepeated(cmd, "--tag", r.tagsCsv);
        addRepeated(cmd, "--env", r.envLines);
        addRepeated(cmd, "--integration", r.integrationsCsv);
        addFlag(cmd, "--comment", r.comment);
        addFlag(cmd, "--setup-script", encodeCommandText(r.setupScript));
        addFlag(cmd, "--prompt", encodeCommandText(r.prompt));
        if (r.noEmail) cmd.append(" --no-email");
        return cmd.toString();
    }

    private static String encodeCommandText(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    private static void addRepeated(StringBuilder cmd, String flag, String value) {
        if (value == null || value.trim().isEmpty()) return;
        String[] parts = value.contains("\n") ? value.split("\\R") : value.split(",");
        for (String part : parts) addFlag(cmd, flag, part.trim());
    }

    private static void addFlag(StringBuilder cmd, String flag, String value) {
        if (value == null || value.trim().isEmpty()) return;
        cmd.append(' ').append(flag).append(' ').append(shellQuote(value.trim()));
    }

    public static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static final class ApiException extends Exception {
        public final int statusCode;
        ApiException(int statusCode, String message) {
            super("HTTP " + statusCode + ": " + message);
            this.statusCode = statusCode;
        }
    }

    public static final class Vm {
        public String name = "";
        public String status = "";
        public String region = "";
        public String sshDest = "";
        public String httpsUrl = "";
        public String raw = "";
    }

    public static final class NewVmRequest {
        public String name = "";
        public String image = "";
        public String cpu = "";
        public String memory = "";
        public String disk = "";
        public String tagsCsv = "";
        public String envLines = "";
        public String integrationsCsv = "";
        public String comment = "";
        public String setupScript = "";
        public String prompt = "";
        public boolean noEmail;
    }
}
