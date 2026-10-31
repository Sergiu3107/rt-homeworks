package ssatr.device;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<AlarmEvent> alarmEvents;

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<AlarmEvent> alarmEvents) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
    }

    @Override
    public void run() {
        // TODO 2: implementați taskul sporadic:
        //   - așteptați un eveniment: alarmEvents.take()
        //   - dacă de la ultima eliberare au trecut mai puțin de
        //     MIN_INTERARRIVAL_MS, respingeți evenimentul (afișați un mesaj)
        //   - altfel publicați pe Config.alarmTopic(deviceId) mesajul
        //     "deviceId;timestamp;temperatura;prag"
        //     (pragul curent poate fi transmis împreună cu temperatura sau
        //      citit din PeriodicTelemetryTask; alegeți și justificați în raport)
        //
        // Vezi exemplul exemple/01-taskuri/.../SporadicTaskExample.java
        long lastReleaseNanos = Long.MIN_VALUE;
        long minimumInterarrivalNanos = TimeUnit.MILLISECONDS.toNanos(MIN_INTERARRIVAL_MS);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                AlarmEvent event = alarmEvents.take();
                long arrivalNanos = event.arrivalTimeNanos();

                if (lastReleaseNanos != Long.MIN_VALUE
                        && arrivalNanos - lastReleaseNanos < minimumInterarrivalNanos) {
                    System.out.printf("[alarm] REJECTED: interval below %d ms%n", MIN_INTERARRIVAL_MS);
                    continue;
                }

                lastReleaseNanos = arrivalNanos;
                String message = String.format(Locale.US, "%s;%d;%.2f;%.2f",
                        deviceId, System.currentTimeMillis(), event.temperature(), event.threshold());
                mqtt.publish(Config.alarmTopic(deviceId), message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

record AlarmEvent(double temperature, double threshold, long arrivalTimeNanos) {
}
