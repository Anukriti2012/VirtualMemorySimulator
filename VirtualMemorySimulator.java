import javax.swing.*;
import java.awt.*;
import java.util.*;
import javax.swing.border.*;

public class VirtualMemorySimulator extends JFrame {
    private JPanel controlPanel, inputPanel, visualizationPanel, explanationPanel;
    private JComboBox<String> algorithmCombo;
    private JTextField frameInput, sequenceInput;
    private JTextArea resultArea, explanationArea;
    private JButton setupButton, visualizeButton;
    private int numFrames;
    private String selectedAlgorithm;
    private int[] pageSequence;
    private javax.swing.Timer animationTimer;
    private int currentStep = 0;
    private int[] faults = {0};
    private Set<Integer> frames = new LinkedHashSet<>();
    private Queue<Integer> fifoQueue = new LinkedList<>();
    private Map<Integer,Integer> recentUse = new HashMap<>();
    private Map<Integer,String> segmentMapping = new HashMap<>();

    public VirtualMemorySimulator() {
        setTitle("Virtual Memory Management Simulator");
        setSize(1300, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(34, 40, 49));

        setupControlPanel();
        setupInputPanel();

        add(controlPanel, BorderLayout.WEST);
        add(inputPanel, BorderLayout.CENTER);
    }

    private void setupControlPanel() {
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setPreferredSize(new Dimension(300, getHeight()));
        controlPanel.setBackground(new Color(45, 52, 54));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JLabel title = new JLabel("Configure Simulator");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(new Color(0xFD9644));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlPanel.add(title);
        controlPanel.add(Box.createVerticalStrut(15));

        controlPanel.add(createLabel("Number of Frames:"));
        frameInput = createTextField();
        controlPanel.add(frameInput);
        controlPanel.add(Box.createVerticalStrut(10));

        controlPanel.add(createLabel("Select Algorithm:"));
        algorithmCombo = new JComboBox<>(new String[]{"FIFO", "LRU", "MRU", "Optimal"});
        algorithmCombo.setMaximumSize(new Dimension(200, 30));
        algorithmCombo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        algorithmCombo.setBackground(new Color(99, 110, 114));
        algorithmCombo.setForeground(Color.WHITE);
        controlPanel.add(algorithmCombo);
        controlPanel.add(Box.createVerticalStrut(20));

        setupButton = new JButton("Setup");
        styleButton(setupButton, new Color(0x58B19F));
        controlPanel.add(setupButton);
        controlPanel.add(Box.createVerticalStrut(20));

        controlPanel.add(createLabel("Results:"));
        resultArea = new JTextArea(6, 20);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setBackground(new Color(78, 81, 84));
        resultArea.setForeground(Color.WHITE);
        resultArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        resultArea.setBorder(BorderFactory.createLineBorder(new Color(99, 110, 114)));
        controlPanel.add(new JScrollPane(resultArea));

        setupButton.addActionListener(e -> {
            try {
                numFrames = Integer.parseInt(frameInput.getText().trim());
                selectedAlgorithm = (String) algorithmCombo.getSelectedItem();
                inputPanel.setVisible(true);
                inputPanel.revalidate();
                inputPanel.repaint();

                // Display algorithm description on setup
                resultArea.setText(getAlgorithmDescription(selectedAlgorithm));

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number of frames.");
            }
        });
    }

    private void setupInputPanel() {
        inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBackground(new Color(34, 40, 49));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JPanel top = new JPanel();
        top.setBackground(new Color(34, 40, 49));
        top.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));

        JLabel seqLabel = new JLabel("Enter Page Sequence:");
        seqLabel.setForeground(Color.WHITE);
        seqLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        top.add(seqLabel);

        sequenceInput = new JTextField(25);
        sequenceInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sequenceInput.setBackground(new Color(78, 81, 84));
        sequenceInput.setForeground(Color.WHITE);
        top.add(sequenceInput);

        visualizeButton = new JButton("Visualize");
        styleButton(visualizeButton, new Color(0xFC427B));
        top.add(visualizeButton);

        inputPanel.add(top, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        visualizationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        visualizationPanel.setBackground(new Color(34, 40, 49));
        centerPanel.add(new JScrollPane(visualizationPanel), BorderLayout.CENTER);

        explanationArea = new JTextArea(8, 80);
        explanationArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        explanationArea.setEditable(false);
        explanationArea.setLineWrap(true);
        explanationArea.setWrapStyleWord(true);
        explanationArea.setBackground(new Color(20, 20, 20));
        explanationArea.setForeground(Color.WHITE);
        explanationArea.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.GRAY), "Execution Steps", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14), Color.WHITE));

        explanationPanel = new JPanel(new BorderLayout());
        explanationPanel.setBackground(new Color(34, 40, 49));
        explanationPanel.add(new JScrollPane(explanationArea), BorderLayout.CENTER);

        centerPanel.add(explanationPanel, BorderLayout.SOUTH);
        inputPanel.add(centerPanel, BorderLayout.CENTER);

        inputPanel.setVisible(false);

        visualizeButton.addActionListener(e -> {
            try {
                String[] seq = sequenceInput.getText().trim().split("\\s+");
                pageSequence = Arrays.stream(seq).mapToInt(Integer::parseInt).toArray();
                simulateAndVisualize();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid page sequence.");
            }
        });
    }

    private void simulateAndVisualize() {
        visualizationPanel.removeAll();
        resultArea.setText("");
        explanationArea.setText("");
        currentStep = 0;
        faults[0] = 0;
        frames.clear();
        fifoQueue.clear();
        recentUse.clear();
        segmentMapping.clear();

        animationTimer = new javax.swing.Timer(800, e -> {
            if (currentStep >= pageSequence.length) {
                showFinalSummary();
                animationTimer.stop();
                return;
            }

            int page = pageSequence[currentStep];
            boolean fault = false;
            int victim = -1;

            StringBuilder explanation = new StringBuilder("Step " + (currentStep + 1) + ": Page " + page);

            if (!frames.contains(page)) {
                fault = true;
                explanation.append(" caused a miss. A page fault occurred because the page was not found in memory.");
                if (frames.size() == numFrames) {
                    switch (selectedAlgorithm) {
                        case "FIFO":
                            victim = fifoQueue.poll();
                            explanation.append(" Page ").append(victim).append(" was evicted using FIFO policy to make space.");
                            break;
                        case "LRU":
                            victim = Collections.min(recentUse.entrySet(), Map.Entry.comparingByValue()).getKey();
                            explanation.append(" Page ").append(victim).append(" was the least recently used and was evicted.");
                            break;
                        case "MRU":
                            victim = Collections.max(recentUse.entrySet(), Map.Entry.comparingByValue()).getKey();
                            explanation.append(" Page ").append(victim).append(" was the most recently used and was evicted.");
                            break;
                        case "Optimal":
                            victim = selectOptimalVictim(currentStep);
                            explanation.append(" Page ").append(victim).append(" was predicted to be used farthest in the future and was evicted.");
                            break;
                    }
                    frames.remove(victim);
                    fifoQueue.remove(victim);
                    recentUse.remove(victim);
                }
                frames.add(page);
                if ("FIFO".equals(selectedAlgorithm)) fifoQueue.offer(page);
                faults[0]++;
            } else {
                explanation.append(" was a hit. No page fault, the page was already in memory.");
            }

            if ("LRU".equals(selectedAlgorithm) || "MRU".equals(selectedAlgorithm)) {
                recentUse.put(page, currentStep);
            }

            segmentMapping.put(page, "Code");
            updateVisualization(frames, page, fault);
            explanationArea.append(explanation.toString() + "\n");
            currentStep++;
        });
        animationTimer.start();
    }

    private int selectOptimalVictim(int step) {
        int farthest = -1, victim = -1;
        for (int f : frames) {
            int next = Integer.MAX_VALUE;
            for (int j = step + 1; j < pageSequence.length; j++) {
                if (pageSequence[j] == f) { next = j; break; }
            }
            if (next > farthest) { farthest = next; victim = f; }
        }
        return victim;
    }

    private void showFinalSummary() {
        StringBuilder sb = new StringBuilder();
        frames.forEach(p -> sb.append(p).append(" "));
        resultArea.setText(
            "Page Faults: " + faults[0] +
            "\nTotal Pages: " + pageSequence.length +
            "\nHit Ratio: " + String.format("%.2f", 1 - (double)faults[0]/pageSequence.length) +
            "\nFinal Frames: " + sb.toString().trim()
        );
    }

    private void updateVisualization(Set<Integer> frames, int current, boolean fault) {
        JPanel cell = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fault ? new Color(255, 99, 132, 50) : new Color(46, 204, 113, 50));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        cell.setPreferredSize(new Dimension(120, 160));
        cell.setOpaque(false);
        cell.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(new Color(99, 110, 114)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        for (int p : frames) {
            JLabel lbl = new JLabel("Page " + p + " (" + segmentMapping.get(p) + ")");
            lbl.setOpaque(true);
            lbl.setBackground(p == current
                ? (fault ? new Color(255, 99, 132) : new Color(46, 204, 113))
                : new Color(78, 81, 84)
            );
            lbl.setForeground(Color.WHITE);
            lbl.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            lbl.setFont(new Font("Consolas", Font.BOLD, 14));
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            cell.add(lbl);
            cell.add(Box.createVerticalStrut(4));
        }
        for (int i = frames.size(); i < numFrames; i++) {
            JLabel empty = new JLabel("-");
            empty.setFont(new Font("Consolas", Font.PLAIN, 14));
            empty.setForeground(new Color(99, 110, 114));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            cell.add(empty);
            cell.add(Box.createVerticalStrut(4));
        }
        JLabel status = new JLabel(fault ? "MISS" : "HIT");
        status.setFont(new Font("Segoe UI", Font.BOLD, 16));
        status.setForeground(fault ? new Color(255, 99, 132) : new Color(46, 204, 113));
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        cell.add(Box.createVerticalGlue());
        cell.add(status);

        visualizationPanel.add(cell);
        visualizationPanel.revalidate();
        visualizationPanel.repaint();
    }

    private String getAlgorithmDescription(String algo) {
        switch (algo) {
            case "FIFO":
                return "FIFO (First-In-First-Out): Replaces the oldest page in memory, the one that came in first.";
            case "LRU":
                return "LRU (Least Recently Used): Replaces the page that has not been used for the longest time.";
            case "MRU":
                return "MRU (Most Recently Used): Replaces the page that was most recently used.";
            case "Optimal":
                return "Optimal: Replaces the page that will not be used for the longest period in the future.";
            default:
                return "";
        }
    }

    private JLabel createLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        return l;
    }

    private JTextField createTextField() {
        JTextField tf = new JTextField();
        tf.setMaximumSize(new Dimension(200, 30));
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setBackground(new Color(78, 81, 84));
        tf.setForeground(Color.WHITE);
        tf.setBorder(BorderFactory.createLineBorder(new Color(99, 110, 114)));
        return tf;
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setMaximumSize(new Dimension(200, 35));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VirtualMemorySimulator().setVisible(true));
    }
}
