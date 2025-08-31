package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class ApiSurveyBuilder extends JFrame {
    private final ManualSurveyBuilder.SurveySender sender;
    private final ChatSurveyGenerator generator;

    private final JTextField topicField = new JTextField();
    private final JButton createNowBtn = new JButton("יצירת הסקר (מיידי)");
    private final JButton scheduleBtn  = new JButton("תזמן לשעה הנבחרת");
    private final JLabel status = new JLabel(" ");
    private boolean busy = false;

    private final JSpinner hourSpinner   = new JSpinner(new SpinnerNumberModel(12, 0, 23, 1));
    private final JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));

    public ApiSurveyBuilder(ManualSurveyBuilder.SurveySender sender, ChatSurveyGenerator generator) {
        super("יצירת סקר ע\"י הצ'אט");
        this.sender = sender;
        this.generator = generator;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildUI());
        setSize(640, 200);
        setLocationRelativeTo(null);
        getRootPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);


        topicField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshValidity(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshValidity(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshValidity(); }
        });


        lockSpinner(hourSpinner);
        lockSpinner(minuteSpinner);

        createNowBtn.addActionListener(e -> createSurvey(false));
        scheduleBtn.addActionListener(e -> createSurvey(true));

        refreshValidity();
    }

    public static void open(ManualSurveyBuilder.SurveySender sender, ChatSurveyGenerator generator) {
        SwingUtilities.invokeLater(() -> new ApiSurveyBuilder(sender, generator).setVisible(true));
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel row = new JPanel(new BorderLayout(8, 8));
        row.add(new JLabel("נושא כללי:"), BorderLayout.WEST);
        row.add(topicField, BorderLayout.CENTER);

        JPanel scheduleRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        scheduleRow.add(new JLabel("שעה:"));
        scheduleRow.add(hourSpinner);
        scheduleRow.add(new JLabel(":"));
        scheduleRow.add(minuteSpinner);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        JPanel btns  = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.add(createNowBtn);
        btns.add(scheduleBtn);
        south.add(btns, BorderLayout.EAST);
        south.add(status, BorderLayout.WEST);

        root.add(row, BorderLayout.NORTH);
        root.add(scheduleRow, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private void refreshValidity() {
        boolean ok = !topicField.getText().trim().isEmpty() && !busy;
        createNowBtn.setEnabled(ok);
        scheduleBtn.setEnabled(ok);
        status.setText(ok ? " " : "יש להזין נושא לסקר");
    }

    private void setBusy(boolean b, String msg) {
        busy = b;
        status.setText(msg);
        topicField.setEnabled(!b);
        hourSpinner.setEnabled(!b);
        minuteSpinner.setEnabled(!b);
        refreshValidity();
    }

    private void createSurvey(boolean schedule) {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "כתוב נושא לסקר.", "חסר נושא", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setBusy(true, schedule ? "מייצר סקר ומזמן לשליחה..." : "מייצר סקר ושולח...");

        new SwingWorker<List<Question>, Void>() {
            @Override protected List<Question> doInBackground() throws Exception {
                return generator.generate(topic);
            }
            @Override protected void done() {
                try {
                    List<Question> qs = get();
                    long delayMillis = 0;
                    if (schedule) delayMillis = computeDelayMillis((int) hourSpinner.getValue(), (int) minuteSpinner.getValue());

                    SurveyLaunchResult res = sender.send(qs, delayMillis);
                    switch (res) {
                        case SUCCESS -> {
                            JOptionPane.showMessageDialog(ApiSurveyBuilder.this,
                                    " הסקר נשלח בהצלחה לקהילה.", "נשלח", JOptionPane.INFORMATION_MESSAGE);
                            ResultsWindow.show(Main.botInstance);
                            dispose();
                        }
                        case SCHEDULED -> {
                            String when = schedule
                                    ? String.format("לשעה %02d:%02d", hourSpinner.getValue(), minuteSpinner.getValue())
                                    : "לזמן מאוחר יותר";
                            JOptionPane.showMessageDialog(ApiSurveyBuilder.this,
                                    "הסקר תוזמן " + when + ". אם יהיה סקר פעיל/אין 3 – יישלח כשיתפנה.",
                                    "תוזמן", JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                        }
                        case ACTIVE_SURVEY -> JOptionPane.showMessageDialog(ApiSurveyBuilder.this,
                                " יש כבר סקר פעיל כרגע. המתן עד שיסתיים.", "חסום", JOptionPane.WARNING_MESSAGE);
                        case NOT_ENOUGH_MEMBERS -> JOptionPane.showMessageDialog(ApiSurveyBuilder.this,
                                " אי אפשר לשלוח סקר: צריך לפחות 3 חברים בקהילה.", "אין מספיק חברים", JOptionPane.WARNING_MESSAGE);
                        case NO_QUESTIONS -> JOptionPane.showMessageDialog(ApiSurveyBuilder.this,
                                " ה־API לא החזיר שאלות תקינות.", "שגיאה", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ApiSurveyBuilder.this,
                            "שגיאת API: " + ex.getMessage(), "שגיאה", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false, " ");
                }
            }
        }.execute();
    }


    private long computeDelayMillis(int targetHour, int targetMinute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (!target.isAfter(now)) target = target.plusDays(1);
        long nowMs = new Date().getTime();
        long targetMs = Date.from(target.atZone(ZoneId.systemDefault()).toInstant()).getTime();
        return Math.max(0, targetMs - nowMs);
    }


    private static void lockSpinner(JSpinner spinner) {
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "00");
        spinner.setEditor(editor);

        JFormattedTextField tf = editor.getTextField();
        tf.setEditable(false);
        tf.setHorizontalAlignment(SwingConstants.CENTER);
    }
}
