import com.fazecast.jSerialComm.SerialPort;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows selected repair station's usage status,
 * reads Goodwin Hall's status from an Arduino serial port.
 */
public final class BikeRepairStationStatus {
    // Only Goodwin Hall works! Others are placeholders for proof of concept.
    private static final String[] LOCATIONS = {
            "Dietrick Hall",
            "McComas Hall",
            "War Memorial Hall",
            "Pamplin Hall",
            "Perry Street Garage",
            "Squires Plaza",
            "Venture Out Center",
            "Duck Pond Gazebo",
            "Goodwin Hall"
    };
    private static final Pattern STATUS_LINE =
            Pattern.compile("(?:Toggle value:\\s*)?([01])");

    // Swing controls, updated on event-dispatch thread
    private final JComboBox<String> locationSelector = new JComboBox<>(LOCATIONS);
    private final JComboBox<SerialPortOption> portSelector = new JComboBox<>();
    private final JLabel statusLabel = new JLabel("Not in use", JLabel.CENTER);
    private final JLabel connectionLabel = new JLabel("Select an Arduino serial port");

    // Serial IO runs on one worker thread
    private final ScheduledExecutorService serialExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final StringBuilder serialLine = new StringBuilder();

    private SerialPort activePort;
    // Worker thread writes, Swing thread reads
    private volatile Boolean goodwinInUse;

    /**
     * Builds and displays location selector,
     * status, and serial-port controls.
     */
    private BikeRepairStationStatus() {
        JFrame frame = new JFrame("Bike Repair Station Status");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Put both selectors above the large station-status label.
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel selectors = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectors.add(new JLabel("Location:"));
        selectors.add(locationSelector);
        selectors.add(new JLabel("Arduino port:"));
        selectors.add(portSelector);
        JButton refreshButton = new JButton("Refresh ports");
        selectors.add(refreshButton);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 24f));
        statusLabel.setPreferredSize(new Dimension(560, 100));
        content.add(selectors, BorderLayout.NORTH);
        content.add(statusLabel, BorderLayout.CENTER);
        content.add(connectionLabel, BorderLayout.SOUTH);

        // Location changes update the display immediately
        // Port changes connect in the worker
        locationSelector.addActionListener(event -> updateDisplayedStatus());
        portSelector.addActionListener(event -> {
            SerialPortOption selected = (SerialPortOption) portSelector.getSelectedItem();
            if (selected != null) {
                serialExecutor.execute(() -> connectTo(selected.port));
            }
        });
        refreshButton.addActionListener(event -> refreshPorts());

        frame.add(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            /**
             * Releases serial device and stops polling worker when window closes.
             */
            @Override
            public void windowClosed(WindowEvent event) {
                serialExecutor.execute(() -> {
                    if (activePort != null) {
                        activePort.closePort();
                        activePort = null;
                    }
                });
                serialExecutor.shutdown();
            }
        });

        // Discover ports once at startup,
        // then poll for incoming serial bytes every 100 ms.
        refreshPorts();
        updateDisplayedStatus();
        serialExecutor.scheduleAtFixedRate(this::pollSerial, 0, 100, TimeUnit.MILLISECONDS);
        frame.setVisible(true);
    }

    /**
     * Rebuilds serial-port list and keeps
     * current selection if still available.
     */
    private void refreshPorts() {
        SerialPortOption previouslySelected = (SerialPortOption) portSelector.getSelectedItem();
        portSelector.removeAllItems();
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            // null represents visible "no ports" placeholder
            portSelector.addItem(new SerialPortOption(null, "No serial ports found"));
            return;
        }

        SerialPortOption matchingPort = null;
        for (SerialPort port : ports) {
            SerialPortOption option = new SerialPortOption(
                    port, port.getSystemPortName() + " - " + port.getDescriptivePortName());
            portSelector.addItem(option);
            if (previouslySelected != null
                    && previouslySelected.port != null
                    && previouslySelected.port.getSystemPortName().equals(port.getSystemPortName())) {
                matchingPort = option;
            }
        }
        if (matchingPort != null) {
            portSelector.setSelectedItem(matchingPort);
        } else {
            portSelector.setSelectedIndex(0);
        }
    }

    /**
     * Closes the current connection and opens the requested port at 9600 baud.
     *
     * @param nextPort serial port to open, or {@code null} if none is selected
     */
    private void connectTo(SerialPort nextPort) {
        if (activePort != null) {
            activePort.closePort();
            activePort = null;
        }
        serialLine.setLength(0);
        goodwinInUse = null;
        // Goodwin status is unknown until valid value is received
        SwingUtilities.invokeLater(this::updateDisplayedStatus);
        if (nextPort == null) {
            showConnectionStatus("No serial port selected");
            return;
        }

        nextPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        nextPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        if (!nextPort.openPort()) {
            showConnectionStatus("Could not open " + nextPort.getSystemPortName());
            return;
        }
        activePort = nextPort;
        showConnectionStatus("Connected to " + nextPort.getSystemPortName() + " at 9600 baud");
    }

    /**
     * Reads available bytes without blocking and
     * dispatches each complete line for parsing.
     */
    private void pollSerial() {
        if (activePort == null) {
            return;
        }
        int available = activePort.bytesAvailable();
        if (available <= 0) {
            return;
        }

        byte[] bytes = new byte[available];
        int bytesRead = activePort.readBytes(bytes, bytes.length);
        if (bytesRead < 0) {
            showConnectionStatus("Error reading from " + activePort.getSystemPortName());
            activePort.closePort();
            activePort = null;
            return;
        }
        String text = new String(bytes, 0, bytesRead, StandardCharsets.US_ASCII);
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\n' || character == '\r') {
                // Arduino println ends a status line; CR/LF pairs may produce an empty line.
                handleSerialLine(serialLine.toString().trim());
                serialLine.setLength(0);
            } else if (serialLine.length() < 128) {
                serialLine.append(character);
            } else {
                // Discard an unexpectedly long line rather than growing the buffer indefinitely.
                serialLine.setLength(0);
            }
        }
    }

    /**
     * Updates the cached Goodwin Hall state when a line contains a valid 0 or 1.
     *
     * @param line complete, trimmed line received from the Arduino
     */
    private void handleSerialLine(String line) {
        Matcher matcher = STATUS_LINE.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        goodwinInUse = "1".equals(matcher.group(1));
        SwingUtilities.invokeLater(this::updateDisplayedStatus);
    }

    /** Displays other locations as unused and
     * Goodwin Hall from the latest Arduino value.
     */
    private void updateDisplayedStatus() {
        boolean isGoodwinHall = "Goodwin Hall".equals(locationSelector.getSelectedItem());
        if (!isGoodwinHall) {
            statusLabel.setText("Not in use");
        } else if (goodwinInUse == null) {
            statusLabel.setText("Waiting for Arduino...");
        } else {
            statusLabel.setText(goodwinInUse ? "In use" : "Not in use");
        }
    }

    /**
     * Changes the connection message on the Swing event-dispatch thread.
     *
     * @param message text describing the current serial connection state
     */
    private void showConnectionStatus(String message) {
        SwingUtilities.invokeLater(() -> connectionLabel.setText(message));
    }

    /**
     * Starts Swing app on event-dispatch thread
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(BikeRepairStationStatus::new);
    }

    /**
     * Holds a detected serial port and
     * label shown for it in port selector.
     */
    private static final class SerialPortOption {
        private final SerialPort port;
        private final String label;

        /**
         * Creates a display option for a port or no-ports placeholder
         *
         * @param port serial port represented by this option, or {@code null}
         * @param label text shown in the selector
         */
        private SerialPortOption(SerialPort port, String label) {
            this.port = port;
            this.label = label;
        }

        /**
         * Returns text Swing displays for this port option
         * 
         * @return label shown in the selector
         */
        @Override
        public String toString() {
            return label;
        }
    }
}
