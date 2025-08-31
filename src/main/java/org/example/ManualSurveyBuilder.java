package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class ManualSurveyBuilder extends JFrame {
    public interface SurveySender {
        SurveyLaunchResult send(List<Question> questions, long delayMillis);
    }

    private final SurveySender sender;

    private static final int MAX_QUESTIONS = 3;
    private static final int MAX_OPTIONS   = 4;

    private final JTextArea[] qText = new JTextArea[MAX_QUESTIONS];
    private final JTextField[][] optText = new JTextField[MAX_QUESTIONS][MAX_OPTIONS];

    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 240, 1)); // דקות
    private final JButton sendBtn = new JButton("שלח סקר לקהילה");
    private final JLabel statusLabel = new JLabel("מוכן");

    public ManualSurveyBuilder(SurveySender sender) {
        super("בניית סקר ידני");
        this.sender = sender;
        buildUI();
        attachValidation();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    public static void open(SurveySender sender) {
        SwingUtilities.invokeLater(() -> new ManualSurveyBuilder(sender).setVisible(true));
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        for (int i = 0; i < MAX_QUESTIONS; i++) {
            center.add(buildQuestionBlock(i));
            if (i < MAX_QUESTIONS - 1) center.add(Box.createVerticalStrut(14));
        }

        JPanel south = new JPanel(new BorderLayout());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("דיליי לשליחה (בדקות):"));
        controls.add(delaySpinner);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("נקה הכל");
        actions.add(clearBtn);
        actions.add(sendBtn);
        south.add(controls, BorderLayout.WEST);
        south.add(actions, BorderLayout.EAST);
        south.add(statusLabel, BorderLayout.SOUTH);

        root.add(new JScrollPane(center), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        clearBtn.addActionListener(e -> clearAll());
        sendBtn.addActionListener(e -> onSend());
        sendBtn.setEnabled(false);
    }

    private JPanel buildQuestionBlock(int index) {
        JPanel block = new JPanel(new BorderLayout(8, 8));
        block.setBorder(new TitledBorder("שאלה " + (index + 1)));

        qText[index] = new JTextArea(2, 50);
        qText[index].setLineWrap(true);
        qText[index].setWrapStyleWord(true);
        qText[index].addKeyListener(nextFieldOnEnter());
        JScrollPane qScroll = new JScrollPane(qText[index],
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        block.add(qScroll, BorderLayout.NORTH);

        JPanel opts = new JPanel(new GridLayout(MAX_OPTIONS, 1, 0, 6));
        for (int i = 0; i < MAX_OPTIONS; i++) {
            optText[index][i] = new JTextField();
            optText[index][i].addKeyListener(nextFieldOnEnter());
            JPanel row = new JPanel(new BorderLayout());
            row.add(new JLabel("אופציה " + (i + 1) + ": "), BorderLayout.WEST);
            row.add(optText[index][i], BorderLayout.CENTER);
            opts.add(row);
        }
        block.add(opts, BorderLayout.CENTER);
        return block;
    }

    private KeyAdapter nextFieldOnEnter() {
        return new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    ((Component) e.getSource()).transferFocus();
                }
            }
        };
    }

    private void attachValidation() {
        for (int i = 0; i < MAX_QUESTIONS; i++) {
            qText[i].getDocument().addDocumentListener(SimpleDocListener.onChange(this::refreshValidity));
            for (int j = 0; j < MAX_OPTIONS; j++) {
                optText[i][j].getDocument().addDocumentListener(SimpleDocListener.onChange(this::refreshValidity));
            }
        }
        delaySpinner.addChangeListener(e -> refreshValidity());
    }

    private void refreshValidity() {
        ValidationResult vr = validateForm();
        sendBtn.setEnabled(vr.ok);
        statusLabel.setText(vr.message);
    }

    private ValidationResult validateForm() {
        int validQuestions = 0;
        for (int i = 0; i < MAX_QUESTIONS; i++) {
            String q = qText[i].getText().trim();
            int nonEmpty = 0;
            for (int j = 0; j < MAX_OPTIONS; j++) {
                if (!optText[i][j].getText().trim().isEmpty()) nonEmpty++;
            }
            if (q.isEmpty() && nonEmpty == 0) continue;
            if (q.isEmpty()) return ValidationResult.fail("שאלה #" + (i + 1) + ": הטקסט ריק.");
            if (nonEmpty < 2) return ValidationResult.fail("שאלה #" + (i + 1) + ": נדרשות לפחות 2 אופציות.");
            validQuestions++;
        }
        if (validQuestions == 0) return ValidationResult.fail("צריך לפחות שאלה אחת עם 2+ אופציות.");
        return ValidationResult.ok("שאלות תקינות: " + validQuestions + "/" + MAX_QUESTIONS);
    }

    private void onSend() {
        ValidationResult vr = validateForm();
        if (!vr.ok) {
            JOptionPane.showMessageDialog(this, vr.message, "שגיאה", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < MAX_QUESTIONS; i++) {
            String q = qText[i].getText().trim();
            if (q.isEmpty()) continue;
            List<String> opts = new ArrayList<>();
            for (int j = 0; j < MAX_OPTIONS; j++) {
                String o = optText[i][j].getText().trim();
                if (!o.isEmpty()) opts.add(o);
            }
            if (opts.size() >= 2 && opts.size() <= 4) {
                try { questions.add(new Question(q, opts)); }
                catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "שגיאה", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        long delayMillis = ((Integer) delaySpinner.getValue()) * 60_000L;
        SurveyLaunchResult result = sender.send(questions, delayMillis);

        switch (result) {
            case SUCCESS -> {
                JOptionPane.showMessageDialog(this, "✅ הסקר נשלח בהצלחה לקהילה.", "נשלח", JOptionPane.INFORMATION_MESSAGE);
                ResultsWindow.show(Main.botInstance);
                dispose();
            }
            case SCHEDULED -> {
                JOptionPane.showMessageDialog(this, "⏳ הסקר תוזמן. יישלח בזמן שנקבע; אם עסוק/אין 3 — יישלח כשיתפנה.", "תוזמן", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            }
            case ACTIVE_SURVEY ->
                    JOptionPane.showMessageDialog(this, "❌ יש כבר סקר פעיל כרגע. המתן עד שיסתיים.", "חסום", JOptionPane.WARNING_MESSAGE);
            case NOT_ENOUGH_MEMBERS ->
                    JOptionPane.showMessageDialog(this, "❌ אי אפשר לשלוח סקר: צריך לפחות 3 חברים בקהילה.", "אין מספיק חברים", JOptionPane.WARNING_MESSAGE);
            case NO_QUESTIONS ->
                    JOptionPane.showMessageDialog(this, "❌ לא הוזנו שאלות תקינות.", "שגיאה", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void clearAll() {
        for (int i = 0; i < MAX_QUESTIONS; i++) {
            qText[i].setText("");
            for (int j = 0; j < MAX_OPTIONS; j++) optText[i][j].setText("");
        }
        delaySpinner.setValue(0);
        refreshValidity();
    }

    private record ValidationResult(boolean ok, String message) {
        static ValidationResult ok(String m) { return new ValidationResult(true, m); }
        static ValidationResult fail(String m) { return new ValidationResult(false, m); }
    }

    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;
        private SimpleDocListener(Runnable r) { this.onChange = r; }
        public static SimpleDocListener onChange(Runnable r) { return new SimpleDocListener(r); }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }
}
