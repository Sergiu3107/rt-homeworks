package ssatr.service;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ultima stare cunoscută a fiecărui device: ultima temperatură, momentul
 * ultimului mesaj, numărul de alarme etc.
 *
 * Este actualizată de thread-urile MessageProcessor și citită de CommandConsole,
 * deci accesul trebuie sincronizat.
 */
public class Dashboard {

    // TODO 5: alegeți structura de date pentru starea device-urilor
    //   (de exemplu un Map<String, ...> cu cheia deviceId)
    //   și adăugați metodele de actualizare apelate din MessageProcessor.
    private final Map<String, DeviceState> devices = new TreeMap<>();

    public synchronized void updateTelemetry(String deviceId, long timestamp, double temperature) {
        DeviceState state = devices.computeIfAbsent(deviceId, ignored -> new DeviceState());
        state.updateLatestMessage(timestamp, temperature);
    }

    public synchronized void updateAlarm(String deviceId, long timestamp) {
        DeviceState state = devices.computeIfAbsent(deviceId, ignored -> new DeviceState());
        state.alarmCount++;
        state.updateLatestMessage(timestamp, null);
    }

    public synchronized void updateStatus(String deviceId, long timestamp) {
        DeviceState state = devices.computeIfAbsent(deviceId, ignored -> new DeviceState());
        state.updateLatestMessage(timestamp, null);
    }

    /** Afișează un tabel cu starea tuturor device-urilor. */
    public synchronized void print() {
        // TODO 5: afișați câte un rând pentru fiecare device, de exemplu:
        //   DEVICE     TEMP    ULTIMUL MESAJ   ALARME
        //   device-1   23.41   acum 1 s        2
        System.out.printf("%-16s %-10s %-18s %s%n", "DISPOZITIV", "TEMP.", "ULTIMUL MESAJ", "ALARME");
        long now = System.currentTimeMillis();
        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
            DeviceState state = entry.getValue();
            String temperature = state.temperature == null
                    ? "--"
                    : String.format(Locale.US, "%.2f", state.temperature);
            long ageSeconds = Math.max(0, (now - state.lastMessageTimestamp) / 1000);
            System.out.printf("%-16s %-10s %-18s %d%n",
                    entry.getKey(), temperature, ageSeconds + " s in urma", state.alarmCount);
        }
    }

    private static final class DeviceState {
        private Double temperature;
        private long lastMessageTimestamp = Long.MIN_VALUE;
        private long alarmCount;

        private void updateLatestMessage(long timestamp, Double newTemperature) {
            if (timestamp >= lastMessageTimestamp) {
                lastMessageTimestamp = timestamp;
                if (newTemperature != null) {
                    temperature = newTemperature;
                }
            }
        }
    }
}
