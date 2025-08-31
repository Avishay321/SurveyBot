package org.example;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ResultsWindow extends JFrame {
    private final myBot bot;
    private javax.swing.Timer timer;
    private final JPanel resultsPanel = new JPanel();

    public ResultsWindow(myBot bot) {
        super("תוצאות הסקר (חי)");
        this.bot = bot;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12,12));

        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        add(new JScrollPane(resultsPanel), BorderLayout.CENTER);

        timer = new javax.swing.Timer(1000, e -> refresh());
        timer.start();

        JButton closeBtn = new JButton("סגור חלון");
        closeBtn.addActionListener(e -> {
            if (timer != null) timer.stop();
            dispose();
        });
        add(closeBtn, BorderLayout.SOUTH);

        setSize(640, 520);
        setLocationRelativeTo(null);
    }

    public static void show(myBot bot) {
        SwingUtilities.invokeLater(() -> new ResultsWindow(bot).setVisible(true));
    }

    private void refresh() {
        myBot.Snapshot snap = bot.getResultsSnapshot();

        resultsPanel.removeAll();

        if (snap == null) {
            resultsPanel.add(new JLabel("אין סקר להצגה כרגע."));
        } else {
            for (int qi = 0; qi < snap.questions.size(); qi++) {
                myBot.QSnap q = snap.questions.get(qi);

                JPanel qPanel = new JPanel(new BorderLayout(8,8));
                qPanel.setBorder(new TitledBorder("שאלה " + (qi+1)));
                JTextArea qText = new JTextArea(q.text);
                qText.setEditable(false);
                qText.setLineWrap(true);
                qText.setWrapStyleWord(true);
                qText.setOpaque(false);
                qPanel.add(qText, BorderLayout.NORTH);

                List<Integer> idxs = new ArrayList<>();
                for (int i = 0; i < q.options.size(); i++) idxs.add(i);
                idxs.sort(Comparator.comparingInt((Integer i) -> q.counts[i]).reversed());

                JPanel list = new JPanel(new GridLayout(q.options.size(), 1, 0, 6));
                for (int i : idxs) {
                    String line = String.format("%s — %d קולות (%.1f%%)", q.options.get(i), q.counts[i], q.percent[i]);
                    list.add(new JLabel(line));
                }
                qPanel.add(list, BorderLayout.CENTER);
                resultsPanel.add(qPanel);
                if (qi < snap.questions.size()-1) resultsPanel.add(Box.createVerticalStrut(10));
            }

            String footer = snap.active ? "הסקר פתוח... מתעדכן" : "הסקר נסגר — תוצאות סופיות";
            resultsPanel.add(Box.createVerticalStrut(10));
            resultsPanel.add(new JLabel(footer));

            if (!snap.active && timer != null && timer.isRunning()) {
                timer.stop();
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
    }
}
