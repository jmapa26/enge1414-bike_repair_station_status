import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class App {
    private static final String MOTION_SENSOR_STATION = "Station 1 (motion sensor)";
    private static final String[] STATIONS = {
        MOTION_SENSOR_STATION,
        "Station 2 (static)",
        "Station 3 (static)"
    };

    private final JFrame frame = new JFrame("Bike Repair Station Status");
    private final JComboBox<String> stationSelector = new JComboBox<>(STATIONS);
    private final JComboBox<String> portSelector = new JComboBox<>();
    private final JButton refreshPortsButton = new JButton("Refresh");
    private final JButton connectionButton = new JButton("Connect");
    private final JLabel statusValue = new JLabel("", SwingConstants.CENTER);
    private final JLabel statusDetail = new JLabel("", SwingConstants.CENTER);
    private final JLabel connectionDetail = new JLabel("Not connected");

    private volatile long connectionId;
    private volatile InputStream serialInput;
    private volatile boolean connected;
    private Boolean ledOn;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(460, 360));
        frame.setLocationByPlatform(true);

        JPanel content = new JPanel(new BorderLayout(16, 20));
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        content.add(createHeader(), BorderLayout.NORTH);
        content.add(createMainContent(), BorderLayout.CENTER);
        content.add(createConnectionPanel(), BorderLayout.SOUTH);
        frame.setContentPane(content);

        stationSelector.addActionListener(event -> updateStationStatus());
        refreshPortsButton.addActionListener(event -> refreshPorts());
        connectionButton.addActionListener(event -> {
            if (connected) {
                disconnect();
            } else {
                connect();
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                disconnect();
            }
        });

        refreshPorts();
        updateStationStatus();
        frame.pack();
        frame.setVisible(true);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new GridLayout(0, 1, 0, 6));
        header.setOpaque(false);
        JLabel title = new JLabel("Bike Repair Station Status");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Choose a station to check its availability.");
        subtitle.setForeground(new Color(90, 90, 90));
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JPanel createMainContent() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 10));
        panel.setOpaque(false);

        JLabel stationLabel = new JLabel("Location");
        stationSelector.setMaximumRowCount(STATIONS.length);
        panel.add(stationLabel);
        panel.add(stationSelector);

        JPanel statusCard = new JPanel(new GridLayout(0, 1, 0, 8));
        statusCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(215, 220, 225)),
            BorderFactory.createEmptyBorder(18, 12, 18, 12)
        ));
        statusValue.setFont(statusValue.getFont().deriveFont(Font.BOLD, 25f));
        statusCard.add(statusValue);
        statusCard.add(statusDetail);
        panel.add(statusCard);
        return panel;
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 8));
        panel.setOpaque(false);

        JLabel label = new JLabel("Arduino serial port");
        portSelector.setEditable(true);
        portSelector.setPreferredSize(new Dimension(220, 28));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.setOpaque(false);
        controls.add(portSelector);
        controls.add(refreshPortsButton);
        controls.add(connectionButton);

        panel.add(label, BorderLayout.NORTH);
        panel.add(controls, BorderLayout.CENTER);
        panel.add(connectionDetail, BorderLayout.SOUTH);
        return panel;
    }

    private void updateStationStatus() {
        String station = (String) stationSelector.getSelectedItem();
        if (!MOTION_SENSOR_STATION.equals(station)) {
            statusValue.setText("Not in use");
            statusValue.setForeground(new Color(35, 125, 75));
            statusDetail.setText("Static placeholder; no live sensor is connected.");
            return;
        }

        if (!connected) {
            statusValue.setText("Not connected");
            statusValue.setForeground(new Color(100, 100, 100));
            statusDetail.setText("Connect the Arduino to see live status.");
        } else if (ledOn == null) {
            statusValue.setText("Waiting for Arduino...");
            statusValue.setForeground(new Color(150, 100, 0));
            statusDetail.setText("Waiting for the first LED state message.");
        } else if (ledOn) {
            statusValue.setText("In use");
            statusValue.setForeground(new Color(185, 45, 45));
            statusDetail.setText("Motion detected; the Arduino LED is on.");
        } else {
            statusValue.setText("Not in use");
            statusValue.setForeground(new Color(35, 125, 75));
            statusDetail.setText("No motion; the Arduino LED is off.");
        }
    }

    private void refreshPorts() {
        String selectedPort = String.valueOf(portSelector.getEditor().getItem()).trim();
        List<String> ports = findSerialPorts();
        portSelector.removeAllItems();
        for (String port : ports) {
            portSelector.addItem(port);
        }
        if (!selectedPort.isEmpty()) {
            portSelector.getEditor().setItem(selectedPort);
        } else if (!ports.isEmpty()) {
            portSelector.setSelectedIndex(0);
        }
    }

    private List<String> findSerialPorts() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return findWindowsSerialPorts();
        }

        File devDirectory = new File("/dev");
        String[] names = devDirectory.list((directory, name) ->
            name.startsWith("cu.") ||
            name.startsWith("ttyACM") ||
            name.startsWith("ttyUSB")
        );
        if (names == null) {
            return Collections.emptyList();
        }

        List<String> ports = new ArrayList<>();
        for (String name : names) {
            ports.add("/dev/" + name);
        }
        Collections.sort(ports);
        return ports;
    }

    private List<String> findWindowsSerialPorts() {
        List<String> ports = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(
                "reg", "query", "HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM", "/s"
            ).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ports;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : output.split("\\R")) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length > 0) {
                    String value = fields[fields.length - 1].trim();
                    if (value.matches("(?i)COM\\d+") && !ports.contains(value)) {
                        ports.add(value.toUpperCase());
                    }
                }
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        Collections.sort(ports);
        return ports;
    }

    private void connect() {
        String port = String.valueOf(portSelector.getEditor().getItem()).trim();
        if (port.isEmpty()) {
            connectionDetail.setText("Select or enter an Arduino serial port.");
            return;
        }

        long id = ++connectionId;
        ledOn = null;
        connected = false;
        connectionButton.setEnabled(false);
        connectionDetail.setText("Connecting to " + port + "...");
        updateStationStatus();

        Thread reader = new Thread(() -> openAndReadSerial(port, id), "arduino-serial-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void openAndReadSerial(String port, long id) {
        try {
            configureSerialPort(port);
            String devicePath = port;
            if (System.getProperty("os.name").toLowerCase().contains("win")
                && port.matches("(?i)COM\\d+")) {
                devicePath = "\\\\.\\" + port;
            }
            InputStream input = new FileInputStream(devicePath);
            if (id != connectionId) {
                input.close();
                return;
            }

            serialInput = input;
            SwingUtilities.invokeLater(() -> {
                if (id != connectionId) {
                    return;
                }
                connected = true;
                connectionButton.setEnabled(true);
                connectionButton.setText("Disconnect");
                connectionDetail.setText("Connected to " + port);
                updateStationStatus();
            });

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                String line;
                while (id == connectionId && (line = reader.readLine()) != null) {
                    if ("LED:ON".equals(line)) {
                        publishLedState(true, id);
                    } else if ("LED:OFF".equals(line)) {
                        publishLedState(false, id);
                    }
                }
            }
            if (id == connectionId) {
                endConnection(id, "Arduino serial connection closed.");
            }
        } catch (IOException | InterruptedException exception) {
            if (id == connectionId) {
                endConnection(id, "Serial connection failed: " + exception.getMessage());
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void configureSerialPort(String port) throws IOException, InterruptedException {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            List<String> command = new ArrayList<>();
            command.add("mode");
            command.add(port);
            command.add("baud=9600");
            command.add("parity=n");
            command.add("data=8");
            command.add("stop=1");

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out while configuring " + port + ".");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IOException(output.isEmpty() ? "Could not configure " + port : output);
            }
            return;
        }

        List<String> command = new ArrayList<>();
        command.add("stty");
        command.add(osName.contains("mac") ? "-f" : "-F");
        command.add(port);
        command.add("9600");
        command.add("raw");
        command.add("-echo");

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out while configuring the serial port.");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException(output.isEmpty() ? "Could not configure " + port : output);
        }
    }

    private void publishLedState(boolean isOn, long id) {
        SwingUtilities.invokeLater(() -> {
            if (id == connectionId) {
                ledOn = isOn;
                updateStationStatus();
            }
        });
    }

    private void endConnection(long id, String message) {
        SwingUtilities.invokeLater(() -> {
            if (id != connectionId) {
                return;
            }
            connectionId++;
            connected = false;
            serialInput = null;
            connectionButton.setEnabled(true);
            connectionButton.setText("Connect");
            connectionDetail.setText(message);
            updateStationStatus();
        });
    }

    private void disconnect() {
        connectionId++;
        connected = false;
        ledOn = null;
        InputStream input = serialInput;
        serialInput = null;
        if (input != null) {
            try {
                input.close();
            } catch (IOException exception) {
                connectionDetail.setText("Could not close serial port: " + exception.getMessage());
            }
        }
        connectionButton.setEnabled(true);
        connectionButton.setText("Connect");
        connectionDetail.setText("Not connected");
        updateStationStatus();
    }
}
