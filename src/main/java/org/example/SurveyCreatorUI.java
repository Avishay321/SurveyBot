package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SurveyCreatorUI {
    public static void launchUI() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Survey Creator");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setSize(520, 620);
            frame.setLayout(new BorderLayout(12, 12));
            frame.getRootPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            List<JTextField> questionFields = new ArrayList<>();
            List<List<JTextField>> optionFields = new ArrayList<>();

            for (int qi = 0; qi < 3; qi++) {
                JTextField qField = new JTextField();
                qField.setToolTipText("שאלה " + (qi + 1));
                qField.putClientProperty("JComponent.sizeVariant", "large");
                mainPanel.add(new JLabel("שאלה " + (qi + 1) + ":"));
                mainPanel.add(qField);
                mainPanel.add(Box.createVerticalStrut(6));
                questionFields.add(qField);

                List<JTextField> opts = new ArrayList<>();
                for (int oi = 0; oi < 4; oi++) {
                    JTextField optField = new JTextField();
                    optField.setToolTipText("אפשרות " + (oi + 1));
                    mainPanel.add(new JLabel("אפשרות " + (oi + 1) + ":"));
                    mainPanel.add(optField);
                    mainPanel.add(Box.createVerticalStrut(6));
                    opts.add(optField);
                }
                optionFields.add(opts);

                mainPanel.add(Box.createRigidArea(new Dimension(0, 12)));
                mainPanel.add(new JSeparator());
                mainPanel.add(Box.createRigidArea(new Dimension(0, 12)));
            }

            JButton sendBtn = new JButton("שלח סקר");
            sendBtn.addActionListener(e -> {
                if (Main.botInstance == null) {
                    JOptionPane.showMessageDialog(frame, "הבוט לא פעיל.");
                    return;
                }

                List<Question> questions = new ArrayList<>();
                for (int qi = 0; qi < questionFields.size(); qi++) {
                    String qText = questionFields.get(qi).getText().trim();
                    if (qText.isEmpty()) continue;

                    List<String> opts = new ArrayList<>();
                    for (JTextField optField : optionFields.get(qi)) {
                        String txt = optField.getText().trim();
                        if (!txt.isEmpty()) opts.add(txt);
                    }

                    try {
                        questions.add(new Question(qText, opts));
                    } catch (IllegalArgumentException ex) {
                        JOptionPane.showMessageDialog(frame, "שאלה #" + (qi + 1) + ": " + ex.getMessage(),
                                "שגיאה בשאלה", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }

                if (questions.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "חייב לפחות שאלה אחת עם 2–4 אופציות.",
                            "טופס לא תקין", JOptionPane.WARNING_MESSAGE);
                    return;
                }


                SurveyLaunchResult res = Main.botInstance.startSurveyForCommunity(questions, 0L);

                switch (res) {
                    case SUCCESS -> {
                        JOptionPane.showMessageDialog(frame, "הסקר נשלח בהצלחה לקהילה.",
                                "נשלח", JOptionPane.INFORMATION_MESSAGE);
                        ResultsWindow.show(Main.botInstance);
                        frame.dispose();
                    }
                    case ACTIVE_SURVEY -> JOptionPane.showMessageDialog(frame,
                            "יש כבר סקר פעיל כרגע. המתן עד שיסתיים.",
                            "חסום", JOptionPane.WARNING_MESSAGE);
                    case NOT_ENOUGH_MEMBERS -> JOptionPane.showMessageDialog(frame,
                            "אי אפשר לשלוח סקר: צריך לפחות 3 חברים בקהילה.",
                            "אין מספיק חברים", JOptionPane.WARNING_MESSAGE);
                    case NO_QUESTIONS -> JOptionPane.showMessageDialog(frame,
                            "לא הוזנו שאלות תקינות.",
                            "שגיאה", JOptionPane.WARNING_MESSAGE);
                    case SCHEDULED -> {
                        JOptionPane.showMessageDialog(frame,
                                "הסקר תוזמן לשליחה מאוחרת.",
                                "תוזמן", JOptionPane.INFORMATION_MESSAGE);
                        frame.dispose();
                    }
                }
            });

            frame.add(new JScrollPane(mainPanel), BorderLayout.CENTER);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(sendBtn);
            frame.add(south, BorderLayout.SOUTH);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
