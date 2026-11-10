import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Professional Swing dashboard for the Hospital Emergency Room Simulation.
 *
 * Features:
 * - Gradient header with live status pill and elapsed clock
 * - Animated KPI cards, semaphore gauges and a live trend chart
 * - Treatment rooms with pulsing state + real treatment progress bars
 * - Doctors table with status dots and progress column (updated in place, no
 * flicker)
 * - Colour-coded terminal mirror with timestamps, filter, auto-scroll and "Save
 * Log"
 * - Keyboard shortcuts: Ctrl+Enter = Start, Ctrl+R = Reset
 */
public class HospitalGUI extends JFrame {

    // ---------- Patterns matching the exact Hospital.log(...) messages ----------
    private static final Pattern PAT_ARRIVED = Pattern.compile("^P(\\d+) arrived\\.$");
    private static final Pattern PAT_ENTERED = Pattern.compile("^P(\\d+) entered waiting room$");
    private static final Pattern PAT_RETURNED = Pattern.compile("^P(\\d+) returned to waiting room \\(timeout\\)$");
    private static final Pattern PAT_LEFT = Pattern.compile("^P(\\d+) left\\.$");
    private static final Pattern PAT_DOC_AVAILABLE = Pattern.compile("^Doctor (\\d+) is now available$");
    private static final Pattern PAT_DOC_ASSIGNED = Pattern.compile("^Doctor (\\d+) is assigned to P(\\d+)$");
    private static final Pattern PAT_DOC_TRYING = Pattern
            .compile("^Doctor (\\d+) is trying to acquire a treatment room\\.$");
    private static final Pattern PAT_DOC_ACQUIRED = Pattern.compile("^Doctor (\\d+) acquired a treatment room\\.$");
    private static final Pattern PAT_DOC_TREATING = Pattern
            .compile("^Doctor (\\d+) is treating P(\\d+) for (\\d+) ms\\.$");
    private static final Pattern PAT_DOC_FINISHED = Pattern.compile("^Doctor (\\d+) finished treating P(\\d+)\\.$");
    private static final Pattern PAT_DOC_RELEASED = Pattern.compile("^Doctor (\\d+) released the treatment room\\.$");
    private static final Pattern PAT_DOC_FAILED = Pattern
            .compile("^Doctor (\\d+) could not acquire a treatment room\\.$");
    private static final Pattern PAT_DOC_RETURNING = Pattern
            .compile("^Doctor (\\d+) returning P(\\d+) to the waiting room\\.$");

    // ---------- Palette ----------
    private static final Color BG_MAIN = new Color(238, 242, 247);
    private static final Color CARD = Color.WHITE;
    private static final Color BORDER = new Color(218, 225, 234);
    private static final Color TEXT = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color PRIMARY_BG = new Color(219, 234, 254);
    private static final Color GREEN = new Color(22, 163, 74);
    private static final Color GREEN_BG = new Color(220, 252, 231);
    private static final Color RED = new Color(220, 38, 38);
    private static final Color RED_BG = new Color(254, 226, 226);
    private static final Color AMBER = new Color(217, 119, 6);
    private static final Color AMBER_BG = new Color(254, 243, 199);
    private static final Color PURPLE = new Color(124, 58, 237);
    private static final Color TRACK = new Color(226, 232, 240);

    private static double pulsePhase = 0;

    // ---------- Controls ----------
    private final JSpinner capacitySpinner = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    private final JSpinner doctorsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
    private final JSpinner patientsSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 300, 1));
    private final FlatButton startButton = new FlatButton("\u25B6  Start Simulation", PRIMARY, Color.WHITE);
    private final FlatButton resetButton = new FlatButton("\u21BB  Reset", new Color(226, 232, 240), TEXT);
    private final StatusPill statusPill = new StatusPill();
    private final JLabel clockLabel = new JLabel("00:00.0");

    // ---------- KPI + gauges + chart ----------
    private final KpiCard kpiOutside = new KpiCard("OUTSIDE", AMBER);
    private final KpiCard kpiWaiting = new KpiCard("WAITING ROOM", PRIMARY);
    private final KpiCard kpiTreating = new KpiCard("IN TREATMENT", RED);
    private final KpiCard kpiTreated = new KpiCard("DISCHARGED", GREEN);
    private final KpiCard kpiTimeouts = new KpiCard("TIMEOUTS", PURPLE);
    private final Gauge gaugeEmpty = new Gauge("emptySlots", PRIMARY);
    private final Gauge gaugeFull = new Gauge("fullSlots", AMBER);
    private final Gauge gaugeRoom = new Gauge("roomSemaphore", RED);
    private final TrendChart trendChart = new TrendChart();

    // ---------- Visual ER panels ----------
    private final JPanel outsidePanel = chipRow();
    private final JPanel waitingPanel = chipRow();
    private final JPanel treatedPanel = chipRow();
    private final JPanel roomsContainer = new JPanel();
    private final List<RoomCard> roomCards = new ArrayList<>();
    private final JLabel fullWarning = new JLabel(" ");
    private final JLabel roomsTitle = new JLabel();

    private final DefaultTableModel doctorModel = new DefaultTableModel(
            new String[] { "Doctor", "Patient", "Current State", "Progress" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable doctorTable = new JTable(doctorModel);

    // ---------- Terminal ----------
    private final JTextPane logPane = new JTextPane();
    private final JTextField filterField = new JTextField(12);
    private final JCheckBox timestampBox = new JCheckBox("Timestamps", true);
    private final JCheckBox autoScrollBox = new JCheckBox("Auto-scroll", true);
    private final List<LogLine> logLines = new ArrayList<>();

    private static class LogLine {
        final double sec;
        final String text;

        LogLine(double sec, String text) {
            this.sec = sec;
            this.text = text;
        }
    }

    // ---------- Simulation state ----------
    private final PrintStream originalSystemOut;
    private volatile boolean isRunning = false;
    private volatile boolean simulationCompleted = false;

    private int totalCapacity = 0, totalDoctors = 0, totalPatients = 0;
    private int timeouts = 0, lastWait = 0, lastOcc = 0;
    private long startMillis = 0, endMillis = 0;

    private final List<Integer> outsideQueue = new ArrayList<>();
    private final List<Integer> waitingQueue = new ArrayList<>();
    private final List<Integer> treatedList = new ArrayList<>();
    private final Map<Integer, String> doctorStates = new LinkedHashMap<>();
    private final Map<Integer, String> doctorPatients = new LinkedHashMap<>();
    /** doctorId -> {startMillis, durationMs} for live treatment progress. */
    private final Map<Integer, long[]> treatInfo = new HashMap<>();

    // snapshots used to rebuild panels only when something changed
    private List<Integer> shownOutside = null, shownWaiting = null, shownTreated = null;
    private int shownCapacity = -1;

    private Hospital activeHospital;
    private WaitingRoom activeWaitingRoom;
    private TreatmentRoomPool activeTreatmentRoomPool;
    private Semaphore activeEmptySlots, activeFullSlots, activeRoomSemaphore;

    private javax.swing.Timer animTimer;
    private Thread simulationThread;
    private int tickCount = 0;

    // =====================================================================
    public HospitalGUI(PrintStream originalSystemOut) {
        super("Hospital Emergency Room Simulation");
        this.originalSystemOut = originalSystemOut;

        initLookAndFeel();
        buildLayout();
        setupEvents();
        initEmptyRooms();
        refreshLiveObjects();
        refreshAllVisualPanels();

        animTimer = new javax.swing.Timer(50, e -> onTick());
        animTimer.start();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1320, 840);
        setMinimumSize(new Dimension(1080, 700));
        setLocationRelativeTo(null);
    }

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    // LAYOUT
    // =====================================================================
    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(BG_MAIN);
        root.setBorder(new EmptyBorder(0, 0, 10, 0));
        setContentPane(root);

        // ----- Header banner -----
        root.add(buildHeader(), BorderLayout.NORTH);

        // ----- Body -----
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 12, 0, 12));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildControlBar());
        top.add(Box.createVerticalStrut(10));
        JPanel kpiRow = new JPanel(new GridLayout(1, 5, 10, 0));
        kpiRow.setOpaque(false);
        kpiRow.add(kpiOutside);
        kpiRow.add(kpiWaiting);
        kpiRow.add(kpiTreating);
        kpiRow.add(kpiTreated);
        kpiRow.add(kpiTimeouts);
        top.add(kpiRow);
        body.add(top, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeft(), buildRight());
        split.setResizeWeight(0.55);
        split.setDividerLocation(700);
        split.setBorder(null);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        split.setDividerSize(8);
        body.add(split, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
    }

    private JComponent buildHeader() {
        JPanel banner = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), getWidth(), 0, new Color(30, 64, 175)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        banner.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("\u271A  Hospital Emergency Room Simulation");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel(
                "Producer\u2013Consumer  \u2022  Bounded Buffer  \u2022  Semaphores  \u2022  Resource Pool");
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 12f));
        sub.setForeground(new Color(191, 219, 254));
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        left.add(title);
        left.add(sub);

        clockLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        clockLabel.setForeground(Color.WHITE);
        JLabel clockCaption = new JLabel("ELAPSED");
        clockCaption.setFont(clockCaption.getFont().deriveFont(Font.BOLD, 10f));
        clockCaption.setForeground(new Color(147, 197, 253));
        JPanel clockBox = new JPanel(new GridLayout(2, 1));
        clockBox.setOpaque(false);
        clockBox.add(clockCaption);
        clockBox.add(clockLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 4));
        right.setOpaque(false);
        right.add(clockBox);
        right.add(statusPill);

        banner.add(left, BorderLayout.WEST);
        banner.add(right, BorderLayout.EAST);
        return banner;
    }

    private JComponent buildControlBar() {
        RoundedPanel bar = new RoundedPanel(new FlowLayout(FlowLayout.LEFT, 12, 8), 14);
        styleSpinner(capacitySpinner);
        styleSpinner(doctorsSpinner);
        styleSpinner(patientsSpinner);
        bar.add(labeled("Waiting Room Capacity", capacitySpinner));
        bar.add(labeled("Doctors", doctorsSpinner));
        bar.add(labeled("Patients", patientsSpinner));
        bar.add(Box.createHorizontalStrut(10));
        bar.add(startButton);
        bar.add(resetButton);
        JLabel hint = new JLabel("Ctrl+Enter: Start   |   Ctrl+R: Reset");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        bar.add(Box.createHorizontalStrut(10));
        bar.add(hint);
        return bar;
    }

    private JPanel labeled(String text, JComponent comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        p.add(l);
        p.add(comp);
        return p;
    }

    private void styleSpinner(JSpinner s) {
        s.setPreferredSize(new Dimension(70, 30));
        s.setFont(s.getFont().deriveFont(Font.BOLD, 13f));
    }

    private JComponent buildLeft() {
        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(0, 0, 0, 6));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 8, 0);

        // 1. Outside
        fullWarning.setForeground(RED);
        fullWarning.setFont(fullWarning.getFont().deriveFont(Font.BOLD, 11f));
        fullWarning.setBorder(new EmptyBorder(0, 14, 6, 14));
        JPanel outsideBody = new JPanel(new BorderLayout());
        outsideBody.setOpaque(false);
        outsideBody.add(hScroll(outsidePanel), BorderLayout.CENTER);
        outsideBody.add(fullWarning, BorderLayout.SOUTH);
        addRow(left, gc, section(new JLabel("1  \u2022  Arriving / Outside Waiting Room"), outsideBody), 0, 100);

        // 2. Waiting room
        addRow(left, gc, section(new JLabel("2  \u2022  Waiting Room (Bounded Buffer)"), hScroll(waitingPanel)), 0,
                108);

        // 3. Rooms
        roomsContainer.setOpaque(false);
        roomsContainer.setBorder(new EmptyBorder(0, 10, 10, 10));
        addRow(left, gc, section(roomsTitle, roomsContainer), 0, 138);

        // 4. Doctors
        setupDoctorTable();
        JScrollPane docScroll = new JScrollPane(doctorTable);
        docScroll.setBorder(new EmptyBorder(0, 8, 8, 8));
        docScroll.getViewport().setBackground(CARD);
        docScroll.setOpaque(false);
        addRow(left, gc, section(new JLabel("4  \u2022  Doctors (Consumer Threads)"), docScroll), 1, 160);

        // 5. Treated
        gc.insets = new Insets(0, 0, 0, 0);
        addRow(left, gc, section(new JLabel("5  \u2022  Discharged Patients (Treated & Left)"), hScroll(treatedPanel)),
                0, 92);
        return left;
    }

    private int gridRow = 0;

    private void addRow(JPanel parent, GridBagConstraints gc, JComponent c, double weighty, int prefH) {
        c.setPreferredSize(new Dimension(300, prefH));
        gc.gridy = gridRow++;
        gc.weighty = weighty;
        parent.add(c, gc);
    }

    private JComponent buildRight() {
        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 6, 0, 0));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 8, 0);

        // Semaphores
        JPanel gauges = new JPanel(new GridLayout(1, 3, 10, 0));
        gauges.setOpaque(false);
        gauges.setBorder(new EmptyBorder(0, 10, 10, 10));
        gauges.add(gaugeEmpty);
        gauges.add(gaugeFull);
        gauges.add(gaugeRoom);
        JComponent semSec = section(new JLabel("Semaphores (live)"), gauges);
        semSec.setPreferredSize(new Dimension(300, 100));
        gc.gridy = 0;
        gc.weighty = 0;
        right.add(semSec, gc);

        // Chart
        JPanel chartBody = new JPanel(new BorderLayout());
        chartBody.setOpaque(false);
        chartBody.setBorder(new EmptyBorder(0, 10, 10, 10));
        chartBody.add(trendChart, BorderLayout.CENTER);
        JComponent chartSec = section(new JLabel("Occupancy Over Time"), chartBody);
        chartSec.setPreferredSize(new Dimension(300, 190));
        gc.gridy = 1;
        gc.weighty = 0;
        right.add(chartSec, gc);

        // Terminal
        logPane.setEditable(false);
        logPane.setBackground(new Color(15, 23, 42));
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logPane.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane logScroll = new JScrollPane(logPane);
        logScroll.setBorder(new EmptyBorder(0, 8, 8, 8));
        logScroll.setOpaque(false);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        JLabel fl = new JLabel("Filter:");
        fl.setForeground(MUTED);
        tools.add(fl);
        tools.add(filterField);
        timestampBox.setOpaque(false);
        autoScrollBox.setOpaque(false);
        tools.add(timestampBox);
        tools.add(autoScrollBox);
        FlatButton save = new FlatButton("Save Log", new Color(226, 232, 240), TEXT);
        save.setBorder(new EmptyBorder(4, 12, 4, 12));
        save.addActionListener(e -> saveLog());
        tools.add(save);

        JPanel termBody = new JPanel(new BorderLayout(0, 6));
        termBody.setOpaque(false);
        termBody.setBorder(new EmptyBorder(0, 10, 0, 10));
        termBody.add(tools, BorderLayout.NORTH);
        JPanel logWrap = new JPanel(new BorderLayout());
        logWrap.setOpaque(false);
        logWrap.add(logScroll);
        termBody.add(logWrap, BorderLayout.CENTER);
        JComponent termSec = section(new JLabel("Terminal Screen (Live System.out)"), termBody);
        gc.gridy = 2;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        right.add(termSec, gc);
        return right;
    }

    private JComponent section(JLabel header, JComponent content) {
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setForeground(TEXT);
        header.setBorder(new EmptyBorder(10, 14, 6, 14));
        RoundedPanel p = new RoundedPanel(new BorderLayout(), 14);
        p.add(header, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private JPanel chipRow() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(2, 6, 2, 6));
        return p;
    }

    private JScrollPane hScroll(JPanel inner) {
        JScrollPane sp = new JScrollPane(inner, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(new EmptyBorder(0, 8, 6, 8));
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        return sp;
    }

    private void setupDoctorTable() {
        doctorTable.setRowHeight(30);
        doctorTable.setShowGrid(false);
        doctorTable.setIntercellSpacing(new Dimension(0, 0));
        doctorTable.setFillsViewportHeight(true);
        doctorTable.getTableHeader().setReorderingAllowed(false);
        doctorTable.getTableHeader().setFont(doctorTable.getFont().deriveFont(Font.BOLD, 12f));
        doctorTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        doctorTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        doctorTable.getColumnModel().getColumn(2).setPreferredWidth(260);
        doctorTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        DoctorRenderer dr = new DoctorRenderer();
        for (int i = 0; i < 3; i++) {
            doctorTable.getColumnModel().getColumn(i).setCellRenderer(dr);
        }
        doctorTable.getColumnModel().getColumn(3).setCellRenderer(new BarRenderer());
    }

    private void setupEvents() {
        startButton.addActionListener(e -> onStartClicked());
        resetButton.addActionListener(e -> onResetClicked());
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                rerenderLog();
            }

            public void removeUpdate(DocumentEvent e) {
                rerenderLog();
            }

            public void changedUpdate(DocumentEvent e) {
                rerenderLog();
            }
        });
        timestampBox.addActionListener(e -> rerenderLog());

        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent_CTRL()), "start");
        rp.getActionMap().put("start", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onStartClicked();
            }
        });
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent_CTRL()), "reset");
        rp.getActionMap().put("reset", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onResetClicked();
            }
        });
    }

    private static int InputEvent_CTRL() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    // =====================================================================
    // ACTIONS
    // =====================================================================
    private void initEmptyRooms() {
        roomsContainer.removeAll();
        roomCards.clear();
        int n = TreatmentRoomPool.total_rooms;
        roomsContainer.setLayout(new GridLayout(1, Math.max(1, n), 12, 0));
        for (int i = 1; i <= n; i++) {
            RoomCard card = new RoomCard(i);
            roomCards.add(card);
            roomsContainer.add(card);
        }
        roomsTitle.setText("3  \u2022  Treatment Room Pool (" + n + " Rooms)");
        roomsContainer.revalidate();
        roomsContainer.repaint();
    }

    private void onStartClicked() {
        if (isRunning) {
            return;
        }
        try {
            capacitySpinner.commitEdit();
            doctorsSpinner.commitEdit();
            patientsSpinner.commitEdit();
        } catch (ParseException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid positive integers.",
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int cap = (Integer) capacitySpinner.getValue();
        int docs = (Integer) doctorsSpinner.getValue();
        int pats = (Integer) patientsSpinner.getValue();
        startSimulationRun(cap, docs, pats, true);
    }

    private void onResetClicked() {
        if (isRunning) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Simulation is still running. Interrupt threads and reset?",
                    "Confirm Reset", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            stopRunningThreads();
        }
        resetStateUI();
    }

    private void resetStateUI() {
        outsideQueue.clear();
        waitingQueue.clear();
        treatedList.clear();
        doctorStates.clear();
        doctorPatients.clear();
        treatInfo.clear();
        doctorModel.setRowCount(0);

        totalCapacity = totalDoctors = totalPatients = 0;
        timeouts = lastWait = lastOcc = 0;
        startMillis = endMillis = 0;
        simulationCompleted = false;
        shownOutside = shownWaiting = shownTreated = null;
        shownCapacity = -1;

        activeHospital = null;
        activeWaitingRoom = null;
        activeTreatmentRoomPool = null;
        activeEmptySlots = activeFullSlots = activeRoomSemaphore = null;

        logLines.clear();
        logPane.setText("");
        trendChart.clear();
        fullWarning.setText(" ");
        clockLabel.setText("00:00.0");
        statusPill.set("READY", PRIMARY_BG, PRIMARY);

        initEmptyRooms();
        refreshLiveObjects();
        refreshAllVisualPanels();
    }

    public void startSimulationRun(int capacity, int numDoctors, int numPatients, boolean printPromptEcho) {
        resetStateUI();

        this.totalCapacity = capacity;
        this.totalDoctors = numDoctors;
        this.totalPatients = numPatients;
        this.isRunning = true;
        this.simulationCompleted = false;
        this.startMillis = System.currentTimeMillis();

        capacitySpinner.setValue(capacity);
        doctorsSpinner.setValue(numDoctors);
        patientsSpinner.setValue(numPatients);
        setInputsEnabled(false);
        statusPill.set("RUNNING", AMBER_BG, AMBER);

        for (int i = 1; i <= numDoctors; i++) {
            doctorStates.put(i, "Initializing...");
            doctorPatients.put(i, "-");
        }
        refreshLiveObjects();
        refreshAllVisualPanels();

        installTeeSystemOut();

        if (printPromptEcho) {
            System.out.println("Waiting room capacity: " + capacity);
            System.out.println("Number of doctors: " + numDoctors);
            System.out.println("Number of patients: " + numPatients);
            Hospital.space();
        } else {
            appendLog("Waiting room capacity: " + capacity);
            appendLog("Number of doctors: " + numDoctors);
            appendLog("Number of patients: " + numPatients);
            appendLog("");
        }

        activeHospital = new Hospital(capacity, numDoctors, numPatients);
        attachReflectionReferences(activeHospital);

        simulationThread = new Thread(() -> {
            try {
                activeHospital.startSimulation();
                try {
                    Thread.sleep(120);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                final Thread finished = Thread.currentThread();
                SwingUtilities.invokeLater(() -> {
                    if (simulationThread != finished) {
                        return;
                    }
                    isRunning = false;
                    simulationCompleted = true;
                    endMillis = System.currentTimeMillis();
                    refreshLiveObjects();
                    refreshAllVisualPanels();
                    statusPill.set("COMPLETED", GREEN_BG, GREEN);
                    setInputsEnabled(true);
                    System.setOut(originalSystemOut);
                });
            }
        }, "Hospital-Simulation-Runner");
        simulationThread.start();
    }

    private void setInputsEnabled(boolean on) {
        startButton.setEnabled(on);
        capacitySpinner.setEnabled(on);
        doctorsSpinner.setEnabled(on);
        patientsSpinner.setEnabled(on);
    }

    private void attachReflectionReferences(Hospital hospital) {
        try {
            activeWaitingRoom = (WaitingRoom) getPrivateField(Hospital.class, hospital, "waitingRoom");
            activeTreatmentRoomPool = (TreatmentRoomPool) getPrivateField(Hospital.class, hospital,
                    "treatmentRoomPool");
            if (activeWaitingRoom != null) {
                activeEmptySlots = (Semaphore) getPrivateField(WaitingRoom.class, activeWaitingRoom, "emptySlots");
                activeFullSlots = (Semaphore) getPrivateField(WaitingRoom.class, activeWaitingRoom, "fullSlots");
            }
            if (activeTreatmentRoomPool != null) {
                activeRoomSemaphore = (Semaphore) getPrivateField(
                        TreatmentRoomPool.class, activeTreatmentRoomPool, "roomSemaphore");
            }
        } catch (Exception ignored) {
            // log-driven visualization still works
        }
    }

    private Object getPrivateField(Class<?> clazz, Object target, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private void stopRunningThreads() {
        isRunning = false;
        endMillis = System.currentTimeMillis();
        if (activeHospital != null) {
            try {
                Doctor[] docs = (Doctor[]) getPrivateField(Hospital.class, activeHospital, "doctors");
                if (docs != null) {
                    for (Doctor d : docs) {
                        if (d != null)
                            d.interrupt();
                    }
                }
                Patient[] pats = (Patient[]) getPrivateField(Hospital.class, activeHospital, "patients");
                if (pats != null) {
                    for (Patient p : pats) {
                        if (p != null)
                            p.interrupt();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (simulationThread != null) {
            simulationThread.interrupt();
        }
        System.setOut(originalSystemOut);
        setInputsEnabled(true);
    }

    private void installTeeSystemOut() {
        TeeLineOutputStream teeOut = new TeeLineOutputStream(originalSystemOut);
        System.setOut(new PrintStream(teeOut, true, StandardCharsets.UTF_8));
    }

    private void saveLog() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("hospital_simulation_log.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (LogLine l : logLines) {
            sb.append(String.format("[%7.2fs] %s%n", l.sec, l.text));
        }
        try {
            Files.write(fc.getSelectedFile().toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save log: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =====================================================================
    // LOG HANDLING
    // =====================================================================
    private void appendLog(String text) {
        double sec = startMillis == 0 ? 0 : (System.currentTimeMillis() - startMillis) / 1000.0;
        LogLine ll = new LogLine(sec, text);
        logLines.add(ll);
        if (matchesFilter(ll)) {
            insertLogLine(ll);
            if (autoScrollBox.isSelected()) {
                logPane.setCaretPosition(logPane.getDocument().getLength());
            }
        }
    }

    private boolean matchesFilter(LogLine l) {
        String f = filterField.getText().trim().toLowerCase();
        return f.isEmpty() || l.text.toLowerCase().contains(f);
    }

    private void rerenderLog() {
        logPane.setText("");
        for (LogLine l : logLines) {
            if (matchesFilter(l)) {
                insertLogLine(l);
            }
        }
        if (autoScrollBox.isSelected()) {
            logPane.setCaretPosition(logPane.getDocument().getLength());
        }
    }

    private void insertLogLine(LogLine l) {
        StyledDocument doc = logPane.getStyledDocument();
        try {
            if (timestampBox.isSelected()) {
                SimpleAttributeSet ts = new SimpleAttributeSet();
                StyleConstants.setForeground(ts, new Color(100, 116, 139));
                doc.insertString(doc.getLength(), String.format("[%6.2fs] ", l.sec), ts);
            }
            SimpleAttributeSet st = new SimpleAttributeSet();
            StyleConstants.setForeground(st, logColor(l.text));
            if (l.text.startsWith("Simulation Completed")) {
                StyleConstants.setBold(st, true);
            }
            doc.insertString(doc.getLength(), l.text + "\n", (AttributeSet) st);
        } catch (BadLocationException ignored) {
        }
    }

    private Color logColor(String t) {
        if (t.startsWith("Simulation Completed"))
            return Color.WHITE;
        if (t.contains("is full") || t.contains("could not"))
            return new Color(251, 191, 36);
        if (t.contains("returning") || t.contains("returned"))
            return new Color(251, 146, 60);
        if (t.contains("finished") || t.contains("released") || t.contains(" left") || t.contains("available"))
            return new Color(74, 222, 128);
        if (t.contains("treating"))
            return new Color(248, 113, 113);
        if (t.contains("arrived") || t.contains("entered") || t.contains("assigned") || t.contains("acquired"))
            return new Color(96, 165, 250);
        return new Color(203, 213, 225);
    }

    /**
     * Parses every console line so the dashboard mirrors the terminal step-by-step.
     */
    private void handleTerminalLine(String line) {
        appendLog(line);
        if (line.isEmpty()) {
            return;
        }
        Matcher m;

        if ((m = PAT_ARRIVED.matcher(line)).matches()) {
            int pId = Integer.parseInt(m.group(1));
            if (!outsideQueue.contains(pId))
                outsideQueue.add(pId);
        } else if (line.equals("Waiting room is full.")) {
            fullWarning.setText("\u26A0  Waiting room is FULL \u2014 patients are waiting outside.");
        } else if ((m = PAT_ENTERED.matcher(line)).matches()) {
            int pId = Integer.parseInt(m.group(1));
            outsideQueue.remove(Integer.valueOf(pId));
            if (!waitingQueue.contains(pId))
                waitingQueue.add(pId);
            if (waitingQueue.size() < totalCapacity)
                fullWarning.setText(" ");
        } else if ((m = PAT_RETURNED.matcher(line)).matches()) {
            int pId = Integer.parseInt(m.group(1));
            if (!waitingQueue.contains(pId))
                waitingQueue.add(pId);
        } else if ((m = PAT_LEFT.matcher(line)).matches()) {
            int pId = Integer.parseInt(m.group(1));
            if (!treatedList.contains(pId))
                treatedList.add(pId);
        } else if ((m = PAT_DOC_AVAILABLE.matcher(line)).matches()) {
            int dId = Integer.parseInt(m.group(1));
            if (simulationCompleted) {
                doctorStates.put(dId, "Shift Ended");
                doctorPatients.put(dId, "-");
            } else {
                String cur = doctorStates.getOrDefault(dId, "");
                if (!cur.startsWith("Treating") && !cur.startsWith("Assigned")) {
                    doctorStates.put(dId, "Available (Took patient)");
                }
            }
        } else if ((m = PAT_DOC_ASSIGNED.matcher(line)).matches()) {
            int dId = Integer.parseInt(m.group(1));
            int pId = Integer.parseInt(m.group(2));
            waitingQueue.remove(Integer.valueOf(pId));
            if (waitingQueue.size() < totalCapacity)
                fullWarning.setText(" ");
            doctorPatients.put(dId, "P" + pId);
            doctorStates.put(dId, "Assigned to P" + pId);
        } else if ((m = PAT_DOC_TRYING.matcher(line)).matches()) {
            doctorStates.put(Integer.parseInt(m.group(1)), "Acquiring room (3s timeout)...");
        } else if ((m = PAT_DOC_ACQUIRED.matcher(line)).matches()) {
            doctorStates.put(Integer.parseInt(m.group(1)), "Acquired treatment room");
        } else if ((m = PAT_DOC_TREATING.matcher(line)).matches()) {
            int dId = Integer.parseInt(m.group(1));
            int pId = Integer.parseInt(m.group(2));
            int ms = Integer.parseInt(m.group(3));
            doctorPatients.put(dId, "P" + pId);
            doctorStates.put(dId, "Treating P" + pId + " (" + ms + " ms)");
            treatInfo.put(dId, new long[] { System.currentTimeMillis(), ms });
        } else if ((m = PAT_DOC_FINISHED.matcher(line)).matches()) {
            int dId = Integer.parseInt(m.group(1));
            doctorStates.put(dId, "Finished treating P" + m.group(2));
            treatInfo.remove(dId);
        } else if ((m = PAT_DOC_RELEASED.matcher(line)).matches()) {
            int dId = Integer.parseInt(m.group(1));
            doctorPatients.put(dId, "-");
            doctorStates.put(dId, "Released room (Idle)");
            treatInfo.remove(dId);
        } else if ((m = PAT_DOC_FAILED.matcher(line)).matches()) {
            doctorStates.put(Integer.parseInt(m.group(1)), "Timeout! No room free");
            timeouts++;
        } else if ((m = PAT_DOC_RETURNING.matcher(line)).matches()) {
            int dId = Integer.parseInt(m.group(1));
            doctorPatients.put(dId, "-");
            doctorStates.put(dId, "Returned P" + m.group(2) + " to waiting room");
        } else if (line.equals("Simulation Completed.")) {
            simulationCompleted = true;
            for (Integer dId : doctorStates.keySet()) {
                if (!doctorStates.get(dId).startsWith("Treating")) {
                    doctorStates.put(dId, "Shift Ended");
                    doctorPatients.put(dId, "-");
                }
            }
        }

        refreshLiveObjects();
        refreshAllVisualPanels();
    }

    // =====================================================================
    // LIVE REFRESH
    // =====================================================================
    private void onTick() {
        pulsePhase += 0.22;
        tickCount++;

        if (isRunning && startMillis > 0) {
            updateClock(System.currentTimeMillis());
        }
        boolean anim = false;
        anim |= kpiOutside.tick();
        anim |= kpiWaiting.tick();
        anim |= kpiTreating.tick();
        anim |= kpiTreated.tick();
        anim |= kpiTimeouts.tick();
        anim |= gaugeEmpty.tick();
        anim |= gaugeFull.tick();
        anim |= gaugeRoom.tick();

        if (isRunning || anim) {
            for (RoomCard rc : roomCards) {
                rc.repaint();
            }
        }
        if (isRunning) {
            refreshLiveObjects();
            updateDoctorProgress();
            if (tickCount % 5 == 0) {
                trendChart.add(lastWait, lastOcc, outsideQueue.size());
            }
        }
    }

    private void updateClock(long now) {
        long end = (isRunning || endMillis == 0) ? now : endMillis;
        long ms = Math.max(0, end - startMillis);
        long min = ms / 60000;
        double sec = (ms % 60000) / 1000.0;
        clockLabel.setText(String.format("%02d:%04.1f", min, sec));
    }

    private void refreshLiveObjects() {
        int occupiedRooms = 0;
        if (activeTreatmentRoomPool != null) {
            TreatmentRoom[] rooms = activeTreatmentRoomPool.getRooms();
            for (int i = 0; i < rooms.length && i < roomCards.size(); i++) {
                TreatmentRoom r = rooms[i];
                boolean occ = r.isOccupied();
                if (occ)
                    occupiedRooms++;
                roomCards.get(i).updateState(r.getRoomId(), occ, r.getCurrentDoctorId(), r.getCurrentPatientId());
            }
        }
        int waitCount = (activeWaitingRoom != null) ? activeWaitingRoom.getCurrentPatientCount() : waitingQueue.size();
        lastWait = waitCount;
        lastOcc = occupiedRooms;

        int tp = Math.max(1, totalPatients);
        kpiOutside.set(String.valueOf(outsideQueue.size()), outsideQueue.size() / (double) tp);
        kpiWaiting.set(waitCount + " / " + totalCapacity, totalCapacity == 0 ? 0 : waitCount / (double) totalCapacity);
        kpiTreating.set(occupiedRooms + " / " + roomCards.size(),
                roomCards.isEmpty() ? 0 : occupiedRooms / (double) roomCards.size());
        kpiTreated.set(treatedList.size() + " / " + totalPatients, treatedList.size() / (double) tp);
        kpiTimeouts.set(String.valueOf(timeouts), Math.min(1.0, timeouts / (double) tp));

        gaugeEmpty.set(activeEmptySlots != null ? activeEmptySlots.getValue() : -1, totalCapacity);
        gaugeFull.set(activeFullSlots != null ? activeFullSlots.getValue() : -1, totalCapacity);
        gaugeRoom.set(activeRoomSemaphore != null ? activeRoomSemaphore.getValue() : -1, roomCards.size());
    }

    private void refreshAllVisualPanels() {
        if (shownOutside == null || !outsideQueue.equals(shownOutside)) {
            outsidePanel.removeAll();
            if (outsideQueue.isEmpty()) {
                outsidePanel.add(hint("No patients waiting outside."));
            } else {
                for (Integer p : outsideQueue) {
                    outsidePanel.add(new Chip("P" + p, AMBER_BG, AMBER));
                    outsidePanel.add(Box.createHorizontalStrut(6));
                }
            }
            outsidePanel.revalidate();
            outsidePanel.repaint();
            shownOutside = new ArrayList<>(outsideQueue);
        }

        if (shownWaiting == null || shownCapacity != totalCapacity || !waitingQueue.equals(shownWaiting)) {
            waitingPanel.removeAll();
            if (totalCapacity <= 0) {
                waitingPanel.add(hint("Set parameters above and click Start Simulation."));
            } else {
                for (int i = 0; i < totalCapacity; i++) {
                    boolean occ = i < waitingQueue.size();
                    waitingPanel.add(new SeatBox("Seat " + (i + 1), occ ? "P" + waitingQueue.get(i) : "Empty", occ));
                    waitingPanel.add(Box.createHorizontalStrut(8));
                }
            }
            waitingPanel.revalidate();
            waitingPanel.repaint();
            shownWaiting = new ArrayList<>(waitingQueue);
            shownCapacity = totalCapacity;
        }

        if (shownTreated == null || !treatedList.equals(shownTreated)) {
            treatedPanel.removeAll();
            if (treatedList.isEmpty()) {
                treatedPanel.add(hint("No patients discharged yet."));
            } else {
                for (Integer p : treatedList) {
                    treatedPanel.add(new Chip("P" + p + " \u2713", GREEN_BG, GREEN));
                    treatedPanel.add(Box.createHorizontalStrut(6));
                }
            }
            treatedPanel.revalidate();
            treatedPanel.repaint();
            shownTreated = new ArrayList<>(treatedList);
        }

        syncDoctorTable();
    }

    private JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setBorder(new EmptyBorder(4, 6, 4, 6));
        return l;
    }

    /** Updates rows in place (no clearing) so selection/scroll never flicker. */
    private void syncDoctorTable() {
        if (doctorModel.getRowCount() != doctorStates.size()) {
            doctorModel.setRowCount(0);
            for (Map.Entry<Integer, String> e : doctorStates.entrySet()) {
                doctorModel.addRow(new Object[] { "Doctor " + e.getKey(),
                        doctorPatients.getOrDefault(e.getKey(), "-"), e.getValue(), -1 });
            }
            return;
        }
        int row = 0;
        for (Map.Entry<Integer, String> e : doctorStates.entrySet()) {
            String pat = doctorPatients.getOrDefault(e.getKey(), "-");
            if (!pat.equals(doctorModel.getValueAt(row, 1)))
                doctorModel.setValueAt(pat, row, 1);
            if (!e.getValue().equals(doctorModel.getValueAt(row, 2)))
                doctorModel.setValueAt(e.getValue(), row, 2);
            row++;
        }
    }

    private void updateDoctorProgress() {
        long now = System.currentTimeMillis();
        int row = 0;
        for (Integer dId : doctorStates.keySet()) {
            if (row >= doctorModel.getRowCount())
                break;
            long[] info = treatInfo.get(dId);
            int pct = -1;
            if (info != null && info[1] > 0) {
                pct = (int) Math.min(100, (now - info[0]) * 100 / info[1]);
            }
            Object cur = doctorModel.getValueAt(row, 3);
            if (cur == null || ((Integer) cur) != pct) {
                doctorModel.setValueAt(pct, row, 3);
            }
            row++;
        }
    }

    private static Color stateColor(String s) {
        if (s.startsWith("Treating"))
            return RED;
        if (s.startsWith("Timeout") || s.startsWith("Acquiring") || s.startsWith("Assigned"))
            return AMBER;
        if (s.startsWith("Returned"))
            return new Color(234, 88, 12);
        if (s.startsWith("Acquired"))
            return PRIMARY;
        if (s.startsWith("Available") || s.startsWith("Released") || s.startsWith("Shift")
                || s.startsWith("Finished"))
            return GREEN;
        return MUTED;
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // =====================================================================
    // CUSTOM COMPONENTS
    // =====================================================================
    private static class RoundedPanel extends JPanel {
        private final int arc;

        RoundedPanel(LayoutManager lm, int arc) {
            super(lm);
            this.arc = arc;
            setOpaque(false);
            setBackground(CARD);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(new Color(15, 23, 42, 10));
            g2.fillRoundRect(1, 2, getWidth() - 2, getHeight() - 2, arc, arc);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 2, arc, arc);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 2, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class FlatButton extends JButton {
        private final Color base;
        private boolean over;

        FlatButton(String text, Color base, Color fg) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(fg);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 18, 8, 18));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            Color c = !isEnabled() ? new Color(203, 213, 225) : (over ? base.darker() : base);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class StatusPill extends JComponent {
        private String text = "READY";
        private Color bg = PRIMARY_BG, fg = PRIMARY;

        StatusPill() {
            setPreferredSize(new Dimension(120, 32));
        }

        void set(String t, Color bg, Color fg) {
            this.text = t;
            this.bg = bg;
            this.fg = fg;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(fg);
            int dotA = (int) (140 + 115 * Math.sin(pulsePhase));
            g2.setColor(alpha(fg, text.equals("RUNNING") ? dotA : 255));
            g2.fillOval(14, getHeight() / 2 - 4, 8, 8);
            g2.setColor(fg);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 28, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    private static class KpiCard extends RoundedPanel {
        private final JLabel title = new JLabel();
        private final JLabel value = new JLabel("0");
        private final Color accent;
        private double target = 0, shown = 0;

        KpiCard(String t, Color accent) {
            super(new BorderLayout(0, 2), 14);
            this.accent = accent;
            setBorder(new EmptyBorder(10, 14, 16, 14));
            title.setText(t);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
            title.setForeground(MUTED);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 26f));
            value.setForeground(accent);
            add(title, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            setPreferredSize(new Dimension(150, 78));
        }

        void set(String v, double frac) {
            if (!v.equals(value.getText()))
                value.setText(v);
            target = Math.max(0, Math.min(1, frac));
        }

        boolean tick() {
            if (Math.abs(target - shown) < 0.004) {
                if (shown != target) {
                    shown = target;
                    repaint();
                }
                return false;
            }
            shown += (target - shown) * 0.25;
            repaint();
            return true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int x = 14, w = getWidth() - 28, y = getHeight() - 14;
            g2.setColor(TRACK);
            g2.fillRoundRect(x, y, w, 5, 5, 5);
            g2.setColor(accent);
            g2.fillRoundRect(x, y, (int) (w * shown), 5, 5, 5);
            g2.dispose();
        }
    }

    private static class Gauge extends JComponent {
        private final String name;
        private final Color color;
        private int value = -1, max = 1;
        private double shown = 0;

        Gauge(String name, Color color) {
            this.name = name;
            this.color = color;
            setPreferredSize(new Dimension(120, 56));
        }

        void set(int v, int m) {
            if (v != value || m != max) {
                value = v;
                max = Math.max(1, m);
                repaint();
            }
        }

        boolean tick() {
            double t = value < 0 ? 0 : Math.min(1.0, value / (double) max);
            if (Math.abs(t - shown) < 0.004)
                return false;
            shown += (t - shown) * 0.25;
            repaint();
            return true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(248, 250, 252));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            g2.setColor(MUTED);
            g2.drawString(name, 10, 17);
            g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
            g2.setColor(color);
            g2.drawString(value < 0 ? "-" : String.valueOf(value) + " / " + max, 10, 40);
            g2.setColor(TRACK);
            g2.fillRoundRect(10, h - 12, w - 20, 5, 5, 5);
            g2.setColor(color);
            g2.fillRoundRect(10, h - 12, (int) ((w - 20) * shown), 5, 5, 5);
            g2.dispose();
        }
    }

    private static class Chip extends JComponent {
        private final String text;
        private final Color bg, fg;

        Chip(String text, Color bg, Color fg) {
            this.text = text;
            this.bg = bg;
            this.fg = fg;
            Font f = getFont().deriveFont(Font.BOLD, 12f);
            setFont(f);
            FontMetrics fm = getFontMetrics(f);
            Dimension d = new Dimension(fm.stringWidth(text) + 24, 28);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.setColor(alpha(fg, 120));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.setColor(fg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    private static class SeatBox extends JComponent {
        private final String seat, occupant;
        private final boolean occupied;

        SeatBox(String seat, String occupant, boolean occupied) {
            this.seat = seat;
            this.occupant = occupant;
            this.occupied = occupied;
            Dimension d = new Dimension(76, 58);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(occupied ? PRIMARY_BG : new Color(248, 250, 252));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            g2.setColor(occupied ? PRIMARY : BORDER);
            g2.setStroke(new BasicStroke(occupied ? 2f : 1f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(seat, (w - fm.stringWidth(seat)) / 2, 17);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g2.setColor(occupied ? PRIMARY : new Color(148, 163, 184));
            fm = g2.getFontMetrics();
            g2.drawString(occupant, (w - fm.stringWidth(occupant)) / 2, 40);
            g2.dispose();
        }
    }

    private class RoomCard extends JComponent {
        private int roomId;
        private boolean occupied;
        private int doctorId = -1, patientId = -1;

        RoomCard(int roomId) {
            this.roomId = roomId;
        }

        void updateState(int roomId, boolean occ, int doc, int pat) {
            if (this.roomId != roomId || occupied != occ || doctorId != doc || patientId != pat) {
                this.roomId = roomId;
                this.occupied = occ;
                this.doctorId = doc;
                this.patientId = pat;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            Color accent = occupied ? RED : GREEN;
            g2.setColor(occupied ? RED_BG : GREEN_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
            int a = occupied ? (int) (150 + 105 * Math.sin(pulsePhase)) : 255;
            g2.setColor(alpha(accent, a));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 16, 16);

            g2.setColor(TEXT);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString("Treatment Room " + roomId, 14, 26);
            g2.setColor(accent);
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g2.drawString(occupied ? "\u25CF OCCUPIED" : "\u25CF AVAILABLE", 14, 46);
            g2.setColor(MUTED);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g2.drawString(occupied ? "Doctor " + doctorId + "  |  Patient P" + patientId
                    : "Waiting for doctor & patient...", 14, 66);

            if (occupied) {
                long[] info = treatInfo.get(doctorId);
                double frac = 0;
                if (info != null && info[1] > 0) {
                    frac = Math.min(1.0, (System.currentTimeMillis() - info[0]) / (double) info[1]);
                }
                g2.setColor(alpha(RED, 50));
                g2.fillRoundRect(14, h - 22, w - 28, 7, 7, 7);
                g2.setColor(RED);
                g2.fillRoundRect(14, h - 22, (int) ((w - 28) * frac), 7, 7, 7);
            }
            g2.dispose();
        }
    }

    private static class TrendChart extends JComponent {
        private final LinkedList<int[]> data = new LinkedList<>();

        void add(int waiting, int treating, int outside) {
            data.add(new int[] { waiting, treating, outside });
            while (data.size() > 240)
                data.removeFirst();
            repaint();
        }

        void clear() {
            data.clear();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            int l = 28, r = 8, t = 22, b = 8;
            int pw = w - l - r, ph = h - t - b;

            int ymax = 1;
            for (int[] d : data)
                ymax = Math.max(ymax, Math.max(d[0], Math.max(d[1], d[2])));
            ymax = Math.max(ymax, 3);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            for (int i = 0; i <= 3; i++) {
                int y = t + ph - ph * i / 3;
                g2.setColor(new Color(241, 245, 249));
                g2.drawLine(l, y, l + pw, y);
                g2.setColor(MUTED);
                g2.drawString(String.valueOf(Math.round(ymax * i / 3.0)), 4, y + 4);
            }
            String[] names = { "Waiting", "Treating", "Outside" };
            Color[] cols = { PRIMARY, RED, AMBER };
            int lx = l;
            for (int s = 0; s < 3; s++) {
                g2.setColor(cols[s]);
                g2.fillRoundRect(lx, 7, 10, 6, 3, 3);
                g2.setColor(MUTED);
                g2.drawString(names[s], lx + 14, 14);
                lx += 70;
            }
            if (data.size() < 2) {
                g2.dispose();
                return;
            }
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int s = 0; s < 3; s++) {
                int[] prev = null;
                int i = 0;
                int n = data.size();
                Path2DHelper path = new Path2DHelper();
                for (int[] d : data) {
                    int x = l + (int) (pw * i / (double) Math.max(1, n - 1));
                    int y = t + ph - (int) (ph * d[s] / (double) ymax);
                    if (prev == null)
                        path.start(x, y);
                    else
                        path.step(x, y);
                    prev = d;
                    i++;
                }
                g2.setColor(cols[s]);
                path.draw(g2);
            }
            g2.dispose();
        }
    }

    /** Minimal step-line helper (horizontal then vertical). */
    private static class Path2DHelper {
        private final java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
        private double lastY;

        void start(double x, double y) {
            p.moveTo(x, y);
            lastY = y;
        }

        void step(double x, double y) {
            p.lineTo(x, lastY);
            p.lineTo(x, y);
            lastY = y;
        }

        void draw(Graphics2D g) {
            g.draw(p);
        }
    }

    private static class DotIcon implements Icon {
        private final Color c;

        DotIcon(Color c) {
            this.c = c;
        }

        public void paintIcon(Component comp, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(c);
            g2.fillOval(x, y, 10, 10);
            g2.dispose();
        }

        public int getIconWidth() {
            return 10;
        }

        public int getIconHeight() {
            return 10;
        }
    }

    private class DoctorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setBorder(new EmptyBorder(0, 10, 0, 6));
            setIcon(null);
            String state = String.valueOf(table.getValueAt(row, 2));
            if (!sel) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
            }
            setForeground(sel ? table.getSelectionForeground() : TEXT);
            setFont(table.getFont().deriveFont(col == 0 ? Font.BOLD : Font.PLAIN));
            if (col == 2) {
                Color c = stateColor(state);
                setIcon(new DotIcon(c));
                setIconTextGap(8);
                if (!sel)
                    setForeground(c.darker());
            }
            return this;
        }
    }

    private static class BarRenderer implements TableCellRenderer {
        private final BarCell cell = new BarCell();

        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int c) {
            cell.pct = (v instanceof Integer) ? (Integer) v : -1;
            cell.stripe = r % 2 == 0;
            return cell;
        }
    }

    private static class BarCell extends JComponent {
        int pct = -1;
        boolean stripe;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(stripe ? Color.WHITE : new Color(248, 250, 252));
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (pct >= 0) {
                int x = 8, w = getWidth() - 16, y = getHeight() / 2 - 4;
                g2.setColor(TRACK);
                g2.fillRoundRect(x, y, w, 8, 8, 8);
                g2.setColor(RED);
                g2.fillRoundRect(x, y, w * pct / 100, 8, 8, 8);
            }
            g2.dispose();
        }
    }

    // Tee OutputStream: forwards to real terminal AND sends full lines to the GUI
    // on the EDT
    private class TeeLineOutputStream extends OutputStream {
        private final PrintStream consoleOut;
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        TeeLineOutputStream(PrintStream consoleOut) {
            this.consoleOut = consoleOut;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            consoleOut.write(b);
            processByte((byte) b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            consoleOut.write(b, off, len);
            for (int i = off; i < off + len; i++) {
                processByte(b[i]);
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            consoleOut.flush();
        }

        private void processByte(byte b) {
            if (b == '\n') {
                String line = lineBuffer.toString(StandardCharsets.UTF_8);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lineBuffer.reset();
                final String finalLine = line;
                SwingUtilities.invokeLater(() -> handleTerminalLine(finalLine));
            } else {
                lineBuffer.write(b);
            }
        }
    }

    // =====================================================================
    // ENTRY POINT
    // =====================================================================
    public static void main(String[] args) {
        PrintStream originalOut = System.out;

        boolean terminalInputMode = false;
        Integer cliCap = null, cliDocs = null, cliPats = null;

        if (args.length == 1 && (args[0].equalsIgnoreCase("--terminal") || args[0].equalsIgnoreCase("-t"))) {
            terminalInputMode = true;
        } else if (args.length == 3) {
            try {
                cliCap = Integer.parseInt(args[0]);
                cliDocs = Integer.parseInt(args[1]);
                cliPats = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }

        if (terminalInputMode) {
            Scanner scanner = new Scanner(System.in);
            originalOut.print("Waiting room capacity: ");
            originalOut.flush();
            int cap = scanner.nextInt();
            originalOut.print("Number of doctors: ");
            originalOut.flush();
            int docs = scanner.nextInt();
            originalOut.print("Number of patients: ");
            originalOut.flush();
            int pats = scanner.nextInt();
            originalOut.println();
            originalOut.println();

            if (cap <= 0 || docs <= 0 || pats <= 0) {
                originalOut.println("Invalid input: capacity, doctors, and patients must be >= 1.");
                return;
            }
            SwingUtilities.invokeLater(() -> {
                HospitalGUI gui = new HospitalGUI(originalOut);
                gui.setVisible(true);
                gui.startSimulationRun(cap, docs, pats, false);
            });
            return;
        }

        final Integer initCap = cliCap, initDocs = cliDocs, initPats = cliPats;
        SwingUtilities.invokeLater(() -> {
            HospitalGUI gui = new HospitalGUI(originalOut);
            gui.setVisible(true);
            if (initCap != null && initDocs != null && initPats != null
                    && initCap > 0 && initDocs > 0 && initPats > 0) {
                gui.startSimulationRun(initCap, initDocs, initPats, true);
            } else {
                originalOut.println("[HospitalGUI] Window opened. Click 'Start Simulation' in the GUI,");
                originalOut.println("[HospitalGUI] or run with '--terminal' to type inputs directly in the terminal.");
            }
        });
    }
}