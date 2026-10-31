package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        // TODO 4: într-o buclă:
        //   - preluați un mesaj: inbox.take()
        //   - salvați mesajul în fișierul CSV: storage.append(kind, mesaj)
        //     (CsvStorage este gata implementată)
        //   - actualizați dashboard-ul, în funcție de kind
        //   - alarmele și confirmările se afișează imediat în consolă
        //   - un mesaj greșit (format invalid) nu trebuie să oprească thread-ul
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = inbox.take();
                storage.append(kind, message);

                try {
                    process(message);
                } catch (IllegalArgumentException e) {
                    System.out.printf("[mesaj %s invalid] %s%n", kind, message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void process(String message) {
        String[] fields = message.split(";", -1);
        if (fields[0].isBlank()) {
            throw new IllegalArgumentException("Missing device ID");
        }

        String deviceId = fields[0];
        switch (kind) {
            case "telemetry" -> processTelemetry(fields, deviceId);
            case "alarm" -> processAlarm(fields, deviceId, message);
            case "status" -> processStatus(fields, deviceId, message);
            default -> throw new IllegalArgumentException("Unknown message type: " + kind);
        }
    }

    private void processTelemetry(String[] fields, String deviceId) {
        requireFieldCount(fields, 3);
        long timestamp = parseTimestamp(fields[1]);
        double temperature = parseNumber(fields[2], "temperature");
        dashboard.updateTelemetry(deviceId, timestamp, temperature);
    }

    private void processAlarm(String[] fields, String deviceId, String message) {
        requireFieldCount(fields, 4);
        long timestamp = parseTimestamp(fields[1]);
        parseNumber(fields[2], "temperature");
        parseNumber(fields[3], "threshold");
        dashboard.updateAlarm(deviceId, timestamp);
        System.out.printf("[ALARMA] %s%n", message);
    }

    private void processStatus(String[] fields, String deviceId, String message) {
        requireFieldCount(fields, 3);
        long timestamp = parseTimestamp(fields[1]);
        if (fields[2].isBlank()) {
            throw new IllegalArgumentException("Missing status text");
        }
        dashboard.updateStatus(deviceId, timestamp);
        System.out.printf("[STARE] %s%n", message);
    }

    private static void requireFieldCount(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("Incorrect number of fields");
        }
    }

    private static long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
    }

    private static double parseNumber(String value, String fieldName) {
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("Invalid " + fieldName);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
    }
}
