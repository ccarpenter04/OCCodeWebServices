package io.occode.examples.rspeer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.rspeer.RSPeer;
import org.rspeer.runetek.providers.RSClient;
import org.rspeer.script.Script;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Supplier;

/**
 * Created by IntelliJ IDEA.
 * User: Mihael Bercic
 * Date: 3. 04. 2019
 * Time: 19:47
 */
public class OCCodeWebServices {

    /**
     * Initialize your web service session with your developer token and your script instance.
     * @param token Developer token generated from http://173.212.213.69/dash/dashboard -> Developer
     * @param script Script instance
     */
    public OCCodeWebServices(@Nonnull String token, @Nonnull Script script) {
        this.token = token;
        this.script = script;
        this.forumUsername = Script.getRSPeerUser().getUsername();
        this.scriptName = script.getName();
        basicData.put("token", token);
        basicData.put("client", client);
        sessionID = getSessionID();
    }

    /**
     * Returns your current sessions ID.
     * @return Session ID
     */
    private long getSessionID() {
        try {
            return Long.parseLong(Objects.requireNonNull(sendRequest(server + "/id", "POST", generateJson(basicData))));
        } catch (Exception e) {
            return -1;
        }
    }

    private Script script;
    private String token, client = "RSPEER", forumUsername, scriptName;
    private Gson gson = new GsonBuilder().create();

    // SOON TO BE https://occode.com/services.
    private final String server = "http://173.212.213.69/services";

    // Supplier when to stop the thread [Recommended: when your script is not running anymore].
    private Supplier<Boolean> shouldStop = () -> true;

    // Map for storing data. Data is later converted to json.
    private Map<String, Object> basicData = new LinkedHashMap<>();
    private Map<String, Object> dataMap = new LinkedHashMap<>();
    private Map<String, Object> notificationMap = new LinkedHashMap<>();
    private Map<String, Object> customMap = new LinkedHashMap<>();
    private Map<String, Object> imageMap = new LinkedHashMap<>();
    private Map<String, Object> pauseMap = new LinkedHashMap<>();

    // Get session ID from our server.
    private Long sessionID;

    /**
     * Configures a method body to run every second.
     * @param runnable Runnable method
     */
    public void setup(@Nonnull Runnable runnable) {
        new Thread(() -> {
            Timer timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (shouldStop.get()) {
                        timer.cancel();
                        timer.purge();
                    } else runnable.run();
                }
            };
            timer.scheduleAtFixedRate(timerTask, 0, 1000);
        }).start();
    }

    /**
     * Sends http request using set request method.
     *
     * @param url           end http request to this url.
     * @param requestMethod POST or GET.
     * @param body          Send this in request body.
     * @return Server output.
     */
    private String sendRequest(@Nonnull String url, @Nonnull String requestMethod, String body) {
        try {
            URL sURL = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) sURL.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod(requestMethod);
            connection.getOutputStream().write((body + "\r\n").getBytes(Charset.forName("UTF-8")));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String response = reader.readLine();
                checkResponse(response);
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Validates the response code by the Server
     * @param response Response code
     */
    private void checkResponse(String response) {
        try {
            int responseCode = Integer.parseInt(response);
            String message = responses.get(responseCode);
            System.out.println("Server response message: \n [" + responseCode + "] " + message);
        } catch (Exception ignored) {
        }
    }

    /**
     * Set a condition for the web service to stop listening at.
     * @param supplier Stop condition
     */
    public void setWhenToStop(Supplier<Boolean> supplier) {
        shouldStop = supplier;
    }

    /**
     * Adds a custom metric to measure throughout the web session. Used to render a graph for on the session's data view.
     * @param name Name of the metric you're tracking
     * @param value Metric value
     */
    public void addCustomMetric(@Nonnull String name, @Nonnull Object value) {
        customMap.put(name, value);
    }

    /**
     * Update the session for a user who has no login/displayname specified.
     * @param botStatus Bot status
     * @param experience Total experience gained
     * @param runtime Total runtime
     */
    public void update(@Nonnull String botStatus, int experience, long runtime) {
        update(botStatus, experience, runtime, "");
    }

    /**
     * Update the session with the new status, total experience, total runtime, and with the username/alias/displayname they have logged in under.
     * @param botStatus Bot status
     * @param experience Total experience gained
     * @param runtime Total runtime
     * @param login Username/alias/displayname
     */
    public void update(@Nonnull String botStatus, int experience, long runtime, @Nonnull String login) {
        dataMap.clear();
        dataMap.put("token", token);
        dataMap.put("sid", sessionID);
        dataMap.put("user", forumUsername);
        dataMap.put("status", botStatus);
        dataMap.put("script", scriptName);
        dataMap.put("client", client);
        dataMap.put("experience", experience);
        dataMap.put("runtime", runtime);
        dataMap.put("login", login);
        dataMap.put("custom", customMap);
        String response = sendRequest(server + "/session", "POST", generateJson(dataMap));
        if (response != null) {
            if (response.contains("1")) sendScreenshot();
            switch (response) {
                case "run":
                    if (!script.isAlive() && !script.isStopping()) script.setPaused(false);
                    break;
                case "pause":
                    if (!script.isPaused()) script.setPaused(true);
                    break;
                case "stop":
                    script.setStopping(true);
                    break;
            }
        }
        customMap.clear();
    }


    /**
     * Sends a screenshot to the server for the appropriate session.
     */
    private void sendScreenshot() {
        try {
            RSClient client = RSPeer.getClient();
            BufferedImage image = toBufferedImage(client.getCanvas().createImage(client.getCanvasWidth(), client.getCanvasHeight()));
            if (image != null) {
                imageMap.clear();
                imageMap.put("sid", sessionID);
                imageMap.put("token", token);
                imageMap.put("username", forumUsername);
                imageMap.put("client", client);
                imageMap.put("image", imgToBase64String(image));
                sendRequest(server + "/screenshot", "POST", generateJson(imageMap));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a screenshot for the current game session.
     * @param img Client image
     * @return Image with background
     */
    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();
        return bimage;
    }

    /**
     * Image in the form of base64 string
     * @param img Client image
     * @return
     */
    private String imgToBase64String(final BufferedImage img) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", os);
            return Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (final IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Converts a Map to a Json string
     * @param data Data map
     * @return Json interpretation of a Map
     */
    private String generateJson(Map<String, Object> data) {
        return gson.toJson(data);
    }

    /**
     * Sends a notification to the server with a custom title, message, and type.
     * @param title Message title
     * @param message Message body
     * @param type Message type
     * @return Server response
     */
    public String sendNotification(String title, String message, NotificationType type) {
        notificationMap.clear();
        notificationMap.put("sid", sessionID);
        notificationMap.put("token", token);
        notificationMap.put("title", title);
        notificationMap.put("message", message);
        notificationMap.put("username", forumUsername);
        notificationMap.put("script", scriptName);
        notificationMap.put("client", client);
        notificationMap.put("type", type);
        return sendRequest(server + "/notification", "POST", generateJson(notificationMap));
    }

    /**
     * Sends an action to pause or resume the bot
     * @param pause True if pause
     */
    private void sendAction(boolean pause) {
        pauseMap.clear();
        pauseMap.put("token", token);
        pauseMap.put("client", "rspeer");
        pauseMap.put("sid", sessionID);
        sendRequest(server + (pause ? "/pause" : "/resume"), "POST", generateJson(pauseMap));
    }

    /**
     * Pauses the bot
     */
    public void onPause() {
        sendAction(true);
    }

    /**
     * Resumes the bot
     */
    public void onResume() {
        sendAction(false);
    }

    /**
     * Handle responses with helpful messages
     */
    private Map<Integer, String> responses = new HashMap<Integer, String>() {{
        put(200, "[OK] Everything works as expected.");
        put(400, "[Invalid format] Invalid body format.");
        put(401, "[Unauthorized] Unauthorized access. Probably invalid token.");
        put(403, "[Forbidden] It is forbidden for you to do that.");
        put(429, "[Too Many Requests] You posted that too many times.");
        put(503, "[Service Unavailable] Server is currently not accepting any requests. Probably under maintenance.");
    }};

    /**
     * Generic Enum for holding notification types.
     */
    public enum NotificationType {
        GENERAL, ERROR, WARNING, INFORMATION, SUCCESS
    }
}