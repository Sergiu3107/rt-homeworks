package ssatr.service;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consola operatorului: citește comenzi de la tastatură.
 *
 *   show                               afișează dashboard-ul
 *   <deviceId> <COMANDA> [parametru]   trimite o comandă unui device, de exemplu
 *                                      device-1 SET_INTERVAL 1000
 *   help                               afișează comenzile
 *   exit                               oprește serviciul
 */
public class CommandConsole implements Runnable {

    private static final Pattern DEVICE_COMMAND_PATTERN = Pattern.compile(
            "([^/+#\\s]+)\\s+(SET_INTERVAL\\s+\\S+|SET_THRESHOLD\\s+\\S+|RESET|STATUS)");

    private final MqttConnection mqtt;
    private final Dashboard dashboard;

    public CommandConsole(MqttConnection mqtt, Dashboard dashboard) {
        this.mqtt = mqtt;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        printHelp();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            } else if (line.equals("exit")) {
                break;
            } else if (line.equals("help")) {
                printHelp();
            } else if (line.equals("show")) {
                dashboard.print();
            } else {
                sendCommand(line);
            }
        }
    }

    private void sendCommand(String line) {
        // TODO 6: separați deviceId de restul liniei
        //   ("device-1 SET_INTERVAL 1000" -> "device-1" și "SET_INTERVAL 1000")
        //   și publicați comanda pe Config.commandTopic(deviceId).
        //   Afișați un mesaj de eroare dacă linia nu are formatul corect.
        Matcher matcher = DEVICE_COMMAND_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            System.out.println("Comandă invalidă. Tastați help pentru a vedea comenzile disponibile.");
            return;
        }

        String deviceId = matcher.group(1);
        String command = matcher.group(2).replaceAll("\\s+", " ");
        mqtt.publish(Config.commandTopic(deviceId), command);
        System.out.println("Comandă trimisă către " + deviceId + ": " + command);
    }

    private void printHelp() {
        System.out.println("Comenzi: show | <deviceId> SET_INTERVAL <ms> | <deviceId> SET_THRESHOLD <valoare>");
        System.out.println("          <deviceId> RESET | <deviceId> STATUS | help | exit");
    }
}
