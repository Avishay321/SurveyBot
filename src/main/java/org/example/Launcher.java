package org.example;

import javax.swing.*;
import java.awt.*;

public class Launcher extends JFrame {
    public Launcher(myBot bot) {
        super("Survey Launcher");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JButton manualBtn = new JButton("סקר ידני ");
        JButton apiBtn    = new JButton("סקר ע\"י API (כולל תזמון)");
        apiBtn.setToolTipText("יצירת סקר מהצ'אט + אפשרות תזמון לשליחה בשעה מסוימת");

        // ידני
        manualBtn.addActionListener(e ->
                ManualSurveyBuilder.open((questions, delayMillis) ->
                        bot.startSurveyForCommunity(questions, delayMillis)
                )
        );


        apiBtn.addActionListener(e -> {
            ManualSurveyBuilder.SurveySender sender =
                    (questions, delayMillis) -> bot.startSurveyForCommunity(questions, delayMillis);

            LegacyChatApiClient apiClient = new LegacyChatApiClient(
                    "https://app.seker.live/fm1",
                    216161729L
            );
            ChatSurveyGenerator generator = new ChatLegacyGenerator(apiClient);

            ApiSurveyBuilder.open(sender, generator);
        });

        JPanel p = new JPanel(new GridLayout(2, 1, 12, 12));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.add(manualBtn);
        p.add(apiBtn);

        setContentPane(p);
        pack();
        setLocationRelativeTo(null);
    }

    public static void show(myBot bot) {
        SwingUtilities.invokeLater(() -> new Launcher(bot).setVisible(true));
    }
}
