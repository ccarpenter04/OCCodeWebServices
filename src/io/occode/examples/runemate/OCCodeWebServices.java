package io.occode.examples.runemate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.runemate.game.api.hybrid.Environment;
import com.runemate.game.api.hybrid.local.Screen;
import com.runemate.game.api.script.framework.AbstractBot;

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
     * Initialize your web service session with your developer token and your bot instance.
     * @param token Developer token generated from https://occode.io -> Developer
     * @param bot Script instance
     */
    public OCCodeWebServices(@Nonnull String token, @Nonnull AbstractBot bot) {
        this.token = token;
        this.bot = bot;
        this.forumUsername = Environment.getForumName();
        this.scriptName = bot.getMetaData().getName();
        basicData.put("token", token);
        basicData.put("client", client);
        basicData.put("script", scriptName);
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

    private AbstractBot bot;
    private String token, client = "RUNEMATE", forumUsername, scriptName;
    private Gson gson = new GsonBuilder().create();

    private String server = "https://occode.io/services";

    // Supplier when to stop the thread [Recommended: when your bot is not running anymore].
    private Supplier<Boolean> shouldStop = () -> true;

    // Map for storing data. Data is later converted to json.
    private Map<String, Object> basicData = new LinkedHashMap<>(); // Add this in your map initialization
    private Map<String, Object> dataMap = new LinkedHashMap<>();
    private Map<String, Object> notificationMap = new LinkedHashMap<>();
    private Map<String, Object> customMap = new LinkedHashMap<>();
    private Map<String, Object> imageMap = new LinkedHashMap<>();
    private Map<String, Object> pauseMap = new LinkedHashMap<>();

    // Session ID
    private Long sessionID;

    /**
     * Returns your current sessions ID.
     * @return Session ID
     */
    public void setup(@Nonnull Runnable runnable) {
        new Thread(() -> {
            Timer timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (sessionID == -1) sessionID = getSessionID(); // Add this line in #setup function.
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
     * @param url           Send http request to this url.
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
        dataMap.put("bot", scriptName);
        dataMap.put("client", client);
        dataMap.put("experience", experience);
        dataMap.put("runtime", runtime);
        dataMap.put("login", login);
        dataMap.put("custom", customMap);
        String response = sendRequest(server + "/session", "POST", generateJson(dataMap));
        if (response != null) {
            if (response.contains("1")) sendScreenshot();
            switch (response.split(":")[0]) {
                case "run":
                    if (!bot.isRunning() && !bot.isStopped()) bot.resume();
                    break;
                case "pause":
                    if (!bot.isPaused()) bot.pause();
                    break;
                case "stop":
                    bot.stop("Stopped due to WebServices request.");
                    break;
            }
        }
        customMap.clear();
    }

    /**
     * Scales the image to the boundary size if the image boundaries are greater than the maximum given boundary.
     * @param imageSize Image size
     * @param boundary Max size
     * @return Scaled dimension
     */
    private Dimension getScaledDimension(Dimension imageSize, Dimension boundary) {
        int ow = imageSize.width;
        int oh = imageSize.height;

        int bw = boundary.width;
        int bh = boundary.height;

        int nw = ow;
        int nh = oh;

        // first check if we need to scale width
        if (ow > bw) {
            //scale width to fit
            nw = bw;
            //scale height to maintain aspect ratio
            nh = nw * oh / ow;
        }

        // then check if we need to scale even with the new height
        if (nh > bh) {
            //scale height to fit instead
            nh = bh;
            //scale width to maintain aspect ratio
            nw = nh * ow / oh;
        }
        return new Dimension(nw, nh);
    }

    /**
     * Resizes the image to the determined dimension and returns it.
     * @param originalImage Image to be resized
     * @param dimension Dimensions of the new image
     * @return Resized image
     */
    private BufferedImage resizeImage(Image originalImage, Dimension dimension) {
        BufferedImage resizedImage = new BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImage.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(originalImage, 0, 0, dimension.width, dimension.height, null);
        g2.dispose();
        return resizedImage;
    }

    /**
     * Sends a screenshot to the server for the appropriate session.
     */
    private void sendScreenshot() {
        try {
            BufferedImage image = Screen.capture();
            if (image != null) {
                image = resizeImage(image, getScaledDimension(new Dimension(image.getWidth(), image.getHeight()), new Dimension(600, 400)));
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
     * Image in the form of base64 string
     * @param img Client image
     * @return Image in 64-bit string form
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
        notificationMap.put("bot", scriptName);
        notificationMap.put("client", client);
        notificationMap.put("type", type.op);
        return sendRequest(server + "/notification", "POST", generateJson(notificationMap));
    }

    /**
     * Sends an action to pause or resume the bot
     * @param pause True if pause
     */
    private void sendAction(boolean pause) {
        pauseMap.clear();
        pauseMap.put("token", token);
        pauseMap.put("client", "runemate");
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

    public enum NotificationType {
        @Expose GENERAL(0),
        @Expose ERROR(1),
        @Expose WARNING(2),
        @Expose INFORMATION(3),
        @Expose SUCCESS(4);

        private final int op;

        NotificationType(final int op) {
            this.op = op;
        }

        public int getOp() {
            return op;
        }
    }
}