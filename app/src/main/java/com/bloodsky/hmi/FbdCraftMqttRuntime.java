package com.bloodsky.hmi;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Operator-scoped Craft MQTT 3.1.1 clients. Each original server number is isolated. */
public final class FbdCraftMqttRuntime implements AutoCloseable {
    private static final ScheduledExecutorService RETRIES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "bloodsky-mqtt-retry"); thread.setDaemon(true); return thread;
    });
    public interface MessageListener { void onMessage(String topic, String payload); }
    public static final class Broker {
        public final String uri, username, password;
        public Broker(String uri, String username, String password) {
            this.uri = uri == null ? "" : uri.trim();
            this.username = username == null ? "" : username;
            this.password = password == null ? "" : password;
        }
        public boolean isConfigured() { return validUri(uri); }
    }
    private static final class ClientSlot {
        final int number;
        final Broker broker;
        final Map<String, MessageListener> subscriptions = new LinkedHashMap<>();
        MqttAsyncClient client;
        MqttConnectOptions options;
        volatile boolean retired;
        volatile int retryAttempt;
        ClientSlot(int number, Broker broker) { this.number = number; this.broker = broker; }
    }

    private final ConcurrentHashMap<Integer, ClientSlot> slots = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public static boolean validUri(String value) {
        if (value == null || value.length() > 255) return false;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String path = uri.getRawPath();
            return ("tcp".equalsIgnoreCase(scheme) || "ssl".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getPort() >= 1 && uri.getPort() <= 65535
                    && uri.getRawUserInfo() == null && (path == null || path.isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (Exception invalid) { return false; }
    }

    public synchronized void connect(int number, Broker broker,
                                     Map<String, MessageListener> subscriptions) {
        if (closed || number < 1 || number > 2 || broker == null || !broker.isConfigured()) return;
        closeSlot(slots.remove(number));
        ClientSlot slot = new ClientSlot(number, broker);
        if (subscriptions != null) for (Map.Entry<String, MessageListener> entry : subscriptions.entrySet()) {
            String topic = entry.getKey();
            if (validTopicFilter(topic) && entry.getValue() != null) slot.subscriptions.put(topic, entry.getValue());
        }
        slots.put(number, slot);
        try {
            String clientId = "BLOODSKY-" + number + "-" + UUID.randomUUID();
            slot.client = new MqttAsyncClient(broker.uri, clientId, new MemoryPersistence());
            slot.client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    slot.retryAttempt = 0;
                    subscribe(slot);
                }
                @Override public void connectionLost(Throwable cause) {}
                @Override public void messageArrived(String topic, MqttMessage message) {
                    ArrayList<MessageListener> listeners = new ArrayList<>();
                    synchronized (slot) {
                        for (Map.Entry<String, MessageListener> entry : slot.subscriptions.entrySet())
                            if (topicMatches(entry.getKey(), topic)) listeners.add(entry.getValue());
                    }
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    for (MessageListener listener : listeners) listener.onMessage(topic, payload);
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            options.setKeepAliveInterval(30);
            if (!broker.username.isEmpty() || !broker.password.isEmpty()) {
                options.setUserName(broker.username);
                options.setPassword(broker.password.toCharArray());
            }
            slot.options = options;
            slot.client.connect(options, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {}
                @Override public void onFailure(IMqttToken token, Throwable error) { scheduleRetry(slot); }
            });
        } catch (MqttException | RuntimeException ignored) { closeSlot(slots.remove(number)); }
    }

    private static void subscribe(ClientSlot slot) {
        MqttAsyncClient client = slot.client;
        if (client == null || !client.isConnected()) return;
        ArrayList<Map.Entry<String, MessageListener>> snapshot;
        synchronized (slot) { snapshot = new ArrayList<>(slot.subscriptions.entrySet()); }
        for (Map.Entry<String, MessageListener> entry : snapshot) {
            try { client.subscribe(entry.getKey(), 2, null, null); }
            catch (MqttException ignored) {}
        }
    }

    private void scheduleRetry(ClientSlot slot) {
        if (closed || slot.retired || slots.get(slot.number) != slot || slot.client == null) return;
        int delaySeconds = Math.min(30, 1 << Math.min(slot.retryAttempt++, 5));
        RETRIES.schedule(() -> {
            if (closed || slot.retired || slots.get(slot.number) != slot) return;
            try {
                slot.client.connect(slot.options, null, new IMqttActionListener() {
                    @Override public void onSuccess(IMqttToken token) {}
                    @Override public void onFailure(IMqttToken token, Throwable error) { scheduleRetry(slot); }
                });
            } catch (MqttException | RuntimeException ignored) { scheduleRetry(slot); }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public boolean publish(int number, String topic, String payload, boolean retain) {
        if (closed || !validTopic(topic) || payload == null
                || payload.getBytes(StandardCharsets.UTF_8).length > 65536) return false;
        ClientSlot slot = slots.get(number);
        if (slot == null || slot.client == null || !slot.client.isConnected()) return false;
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(2);
        message.setRetained(retain);
        try { slot.client.publish(topic, message, null, null); return true; }
        catch (MqttException ignored) { return false; }
    }

    public static boolean validTopic(String topic) {
        return topic != null && !topic.isEmpty() && topic.getBytes(StandardCharsets.UTF_8).length <= 65535
                && topic.indexOf('#') < 0 && topic.indexOf('+') < 0 && topic.indexOf('\u0000') < 0;
    }

    public static boolean validTopicFilter(String topic) {
        if (topic == null || topic.isEmpty() || topic.getBytes(StandardCharsets.UTF_8).length > 65535
                || topic.indexOf('\u0000') >= 0) return false;
        String[] levels = topic.split("/", -1);
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            if (level.indexOf('#') >= 0 && !("#".equals(level) && i == levels.length - 1)) return false;
            if (level.indexOf('+') >= 0 && !"+".equals(level)) return false;
        }
        return true;
    }

    public static boolean topicMatches(String filter, String topic) {
        if (!validTopicFilter(filter) || topic == null || topic.isEmpty() || topic.indexOf('\u0000') >= 0) return false;
        if (topic.startsWith("$") && (filter.startsWith("+") || filter.startsWith("#"))) return false;
        String[] filters = filter.split("/", -1), levels = topic.split("/", -1);
        for (int i = 0, j = 0; i < filters.length; i++, j++) {
            if ("#".equals(filters[i])) return i == filters.length - 1;
            if (j >= levels.length || (!"+".equals(filters[i]) && !filters[i].equals(levels[j]))) return false;
        }
        return filters.length == levels.length;
    }

    /** Craft decodes subscribed payloads as BOOL, Integer or Float from UTF-8 text. */
    public static Double parsePayload(String payload, int type) {
        if (payload == null || payload.trim().isEmpty()) return null;
        String value = payload.trim();
        try {
            if (type == 1) {
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) return 1.0;
                if ("false".equalsIgnoreCase(value) || "0".equals(value)) return 0.0;
                return null;
            }
            if (type == 2) {
                long integer = Long.parseLong(value);
                if (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE) return null;
                return (double) integer;
            }
            if (type == 3) {
                float number = Float.parseFloat(value);
                return Float.isFinite(number) ? (double) number : null;
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    @Override public synchronized void close() {
        closed = true;
        ArrayList<ClientSlot> snapshot = new ArrayList<>(slots.values());
        slots.clear();
        Thread closer = new Thread(() -> {
            for (ClientSlot slot : snapshot) { slot.retired = true; closeSlot(slot); }
        }, "bloodsky-mqtt-close");
        closer.setDaemon(true);
        closer.start();
    }
    private static void closeSlot(ClientSlot slot) {
        if (slot == null || slot.client == null) return;
        slot.retired = true;
        try { if (slot.client.isConnected()) slot.client.disconnectForcibly(100, 1000, false); }
        catch (Exception ignored) {}
        try { slot.client.close(true); } catch (Exception ignored) {}
    }
}
