package ssatr.device;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        // TODO 3: implementați taskul aperiodic:
        //   - preluați comenzile din coadă: commands.take()
        //   - separați numele comenzii de parametru: command.split(" ")
        //   - executați comanda (periodic.setPeriodMs(...), sensor.reset() etc.)
        //   - publicați confirmarea pe Config.statusTopic(deviceId)
        //   - o comandă necunoscută sau un parametru invalid nu trebuie să
        //     oprească taskul: publicați "ERROR <motiv>"
        //
        // Vezi exemplul exemple/01-taskuri/.../AperiodicTaskExample.java
        while (!Thread.currentThread().isInterrupted()) {
            String command;
            try {
                command = commands.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            String response;
            try {
                response = execute(command);
            } catch (IllegalArgumentException e) {
                response = "ERROR " + e.getMessage();
            }

            String message = String.format(Locale.US, "%s;%d;%s",
                    deviceId, System.currentTimeMillis(), response);
            mqtt.publish(Config.statusTopic(deviceId), message);
        }
    }

    private String execute(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty command");
        }

        String[] tokens = trimmed.split("\\s+");
        return switch (tokens[0]) {
            case "SET_INTERVAL" -> setInterval(tokens);
            case "SET_THRESHOLD" -> setThreshold(tokens);
            case "RESET" -> reset(tokens);
            case "STATUS" -> status(tokens);
            default -> throw new IllegalArgumentException("unknown command: " + tokens[0]);
        };
    }

    private String setInterval(String[] tokens) {
        requireArgumentCount(tokens, 2, "SET_INTERVAL requires one positive millisecond value");
        long periodMs = parseLong(tokens[1], "interval");
        if (periodMs <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }
        periodic.setPeriodMs(periodMs);
        return "OK SET_INTERVAL " + periodMs;
    }

    private String setThreshold(String[] tokens) {
        requireArgumentCount(tokens, 2, "SET_THRESHOLD requires one numeric value");
        double threshold = parseFiniteDouble(tokens[1], "threshold");
        periodic.setThreshold(threshold);
        return String.format(Locale.US, "OK SET_THRESHOLD %.2f", threshold);
    }

    private String reset(String[] tokens) {
        requireArgumentCount(tokens, 1, "RESET does not accept parameters");
        sensor.reset();
        return "OK RESET";
    }

    private String status(String[] tokens) {
        requireArgumentCount(tokens, 1, "STATUS does not accept parameters");
        return String.format(Locale.US, "OK STATUS periodMs=%d threshold=%.2f",
                periodic.getPeriodMs(), periodic.getThreshold());
    }

    private static void requireArgumentCount(String[] tokens, int expected, String message) {
        if (tokens.length != expected) {
            throw new IllegalArgumentException(message);
        }
    }

    private static long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + fieldName);
        }
    }

    private static double parseFiniteDouble(String value, String fieldName) {
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + fieldName);
        }
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        return number;
    }
}
