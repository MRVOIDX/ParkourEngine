package dev.maghreb.parkour.license;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MCLicense {

    private static final Logger LOGGER = Logger.getLogger("MCLicense");

    private static final String API_BASE_URL = "https://api.mclicense.org";
    private static final String API_URL = API_BASE_URL + "/validate/%s/%s";
    private static final String HEARTBEAT_URL = API_BASE_URL + "/heartbeat/%s/%s";

    private static final int TIMEOUT_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    private static final String PUBLIC_KEY =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArhw7oQaOrgCzUxDi5D+N\n" +
            "TH9te0JYB1EvW7CEI40+n2drmHJ4+g0CXXYjJc5LyuePskSUPnnHf3UkRvi1GTUd\n" +
            "6Bqi2Jpeu+qbBfm3hg6rcyLUWo8d5MrBQDbVcIvKmQNegTaJGxFRpEFR9XOeHI1g\n" +
            "4dfF+hOfy+1rbEF4p4fgiz0irtKv8l3uSPOKVoEjTL9xnZx4MU5rIsn6W3jee04q\n" +
            "ESPJpCg8nmmZSuJ+9EzzoLnLnUc2/sBuqJ/jexpNfMrXIR11+L8DFqei7J2M7aKi\n" +
            "0KZvQNIqzqTPBCR9VLZPBjFu6cYT/E/WUjjFROuRhi+7Xsa6tKLqoiO4VwJSrn5L\n" +
            "zwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private static final boolean IS_FOLIA = isRunningFolia();

    private static String pmPlaceholder = "%%__POLYMART__%%";
    private static String pmLicense = "%%__LICENSE__%%";
    private static String pmUser = "%%__USER__%%";
    private static String bbbLicense = "%%__BBB_LICENSE__%%";

    private static boolean heartbeatRunning = false;
    private static String heartbeatPluginId;
    private static String heartbeatKey;
    private static String heartbeatSessionId;
    private static ScheduledTask foliaTask;
    private static BukkitTask bukkitTask;
    private static boolean listenerRegistered = false;

    private static boolean isRunningFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean validateKey(JavaPlugin plugin, String pluginId) {
        try {
            File licenseFile = new File(plugin.getDataFolder(), "mclicense.txt");
            String fileContent = "";
            if (!licenseFile.exists()) {
                plugin.getDataFolder().mkdirs();
                licenseFile.createNewFile();
            } else {
                fileContent = new String(Files.readAllBytes(Paths.get(licenseFile.getPath())), StandardCharsets.UTF_8).trim();
            }

            String key = fileContent;
            boolean usedHardcodedFallback = false;

            if (key.isEmpty()) {
                String hardcodedKey = getHardcodedLicense();
                if (hardcodedKey != null) {
                    key = hardcodedKey;
                    usedHardcodedFallback = true;
                    Files.write(licenseFile.toPath(), key.getBytes(StandardCharsets.UTF_8));
                } else {
                    LOGGER.info("License key is empty for " + plugin.getName() + "! Place your key in the 'mclicense.txt' file in the plugin folder and restart the server.");
                    return false;
                }
            }

            boolean isValid = validateLicenseWithServer(plugin, pluginId, key, licenseFile);

            if (isValid && !usedHardcodedFallback && !key.equals(fileContent)) {
                Files.write(licenseFile.toPath(), key.getBytes(StandardCharsets.UTF_8));
            }

            return isValid;
        } catch (Exception e) {
            LOGGER.info("License validation failed for " + plugin.getName() + " (System error)");
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeAndValidate(JavaPlugin plugin, String pluginId, String key) {
        try {
            File licenseFile = new File(plugin.getDataFolder(), "mclicense.txt");
            if (!licenseFile.exists()) {
                plugin.getDataFolder().mkdirs();
                licenseFile.createNewFile();
            }
            Files.write(licenseFile.toPath(), key.getBytes(StandardCharsets.UTF_8));
            return validateLicenseWithServer(plugin, pluginId, key, licenseFile);
        } catch (Exception e) {
            LOGGER.info("License write and validation failed for " + plugin.getName() + " (System error)");
            e.printStackTrace();
            return false;
        }
    }

    private static boolean validateLicenseWithServer(JavaPlugin plugin, String pluginId, String key, File licenseFile) {
        try {
            String sessionId = UUID.randomUUID().toString();
            String nonce = UUID.randomUUID().toString();

            String encodedPluginId = URLEncoder.encode(pluginId, StandardCharsets.UTF_8.toString()).replace("+", "%20");
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.toString()).replace("+", "%20");

            String baseUrl = String.format(API_URL, encodedPluginId, encodedKey)
                    + "?sessionId=" + sessionId
                    + "&nonce=" + nonce;

            String polymartUserId = getPolymartUserId();
            if (polymartUserId != null) {
                baseUrl += "&polymartUserId=" + URLEncoder.encode(polymartUserId, StandardCharsets.UTF_8.toString());
            }

            URL url = new URL(baseUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            String response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                response = sb.toString();
            }

            if (connection.getResponseCode() != 200) {
                try {
                    LOGGER.info("License validation failed for " + plugin.getName() + " (" + new JSONObject(response).getString("message") + ")");
                } catch (Exception e) {
                    LOGGER.info("License validation failed for " + plugin.getName() + " (Server error)");
                }
                return false;
            }

            JSONObject responseJson = new JSONObject(response);

            if (!responseJson.getString("nonce").equals(nonce)) {
                LOGGER.info("License validation failed for " + plugin.getName() + " (Nonce mismatch)");
                return false;
            }

            if (!responseJson.getString("key").equals(key) || !responseJson.getString("pluginId").equals(pluginId)) {
                LOGGER.info("License validation failed for " + plugin.getName() + " (Key or pluginId mismatch)");
                return false;
            }

            String signature = responseJson.getString("signature");
            JSONObject dataToVerify = new JSONObject();
            dataToVerify.put("key", responseJson.getString("key"));
            dataToVerify.put("pluginId", responseJson.getString("pluginId"));
            dataToVerify.put("status", responseJson.getString("status"));
            dataToVerify.put("message", responseJson.getString("message"));
            dataToVerify.put("nonce", responseJson.getString("nonce"));

            String publicKeyPEM = PUBLIC_KEY
                    .replace("-----BEGIN PUBLIC KEY-----\n", "")
                    .replace("\n-----END PUBLIC KEY-----", "")
                    .replaceAll("\n", "");

            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyPEM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(dataToVerify.toString().getBytes(StandardCharsets.UTF_8));

            if (!sig.verify(Base64.getDecoder().decode(signature))) {
                LOGGER.info("License validation failed for " + plugin.getName() + " (Signature mismatch)");
                return false;
            }

            startHeartbeat(plugin, pluginId, key, sessionId);
            LOGGER.info("License validation succeeded for " + plugin.getName() + "!");
            return true;

        } catch (Exception e) {
            LOGGER.info("License validation failed for " + plugin.getName() + " (System error)");
            e.printStackTrace();
            return false;
        }
    }

    protected static void startHeartbeat(JavaPlugin plugin, String pluginId, String key, String sessionId) {
        if (heartbeatRunning) killHeartbeat();

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(new ShutdownListenerImpl(plugin), plugin);
            listenerRegistered = true;
        }

        heartbeatPluginId = pluginId;
        heartbeatKey = key;
        heartbeatSessionId = sessionId;

        if (IS_FOLIA) {
            foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                    (task) -> sendHeartbeat(false),
                    HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        } else {
            bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                    () -> sendHeartbeat(false),
                    HEARTBEAT_INTERVAL_SECONDS * 20L, HEARTBEAT_INTERVAL_SECONDS * 20L);
        }

        heartbeatRunning = true;
    }

    protected static void sendHeartbeat(boolean isShutdown) {
        try {
            URL url = new URL(String.format(HEARTBEAT_URL, heartbeatPluginId, heartbeatKey));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("serverIp", heartbeatSessionId);
            if (isShutdown) payload.put("shutdown", true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private static void killHeartbeat() {
        sendHeartbeat(true);
        if (IS_FOLIA && foliaTask != null) {
            foliaTask.cancel();
            foliaTask = null;
        } else if (!IS_FOLIA && bukkitTask != null) {
            bukkitTask.cancel();
            bukkitTask = null;
        }
        heartbeatRunning = false;
    }

    private static String getHardcodedLicense() {
        if (!bbbLicense.startsWith("%%__")) return bbbLicense;
        if (pmPlaceholder.equals("1") && !pmLicense.startsWith("%%__")) return "pm_" + pmLicense;
        return null;
    }

    private static String getPolymartUserId() {
        if (pmPlaceholder.equals("1") && !pmUser.startsWith("%%__")) return pmUser;
        return null;
    }

    private static class ShutdownListenerImpl implements Listener {
        private final JavaPlugin activePlugin;

        ShutdownListenerImpl(JavaPlugin plugin) {
            this.activePlugin = plugin;
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin() == activePlugin) {
                try { sendHeartbeat(true); } catch (Exception ignored) {}
            }
        }
    }
}
