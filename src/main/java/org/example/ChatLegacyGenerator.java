package org.example;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class ChatLegacyGenerator implements ChatSurveyGenerator {
    private final LegacyChatApiClient api;

    public ChatLegacyGenerator(LegacyChatApiClient api) {
        this.api = api;
    }

    @Override
    public List<Question> generate(String topic) throws Exception {

        api.clearHistory();


        String systemPrompt =
                "שלום צ'אט, התפקיד שלך הוא ליצור סקר. אני אשלח לך נושא.\n" +
                        "תחזיר טקסט בפורמט מדויק, בלי הסברים נוספים:\n" +
                        "לפני כל שאלה כתוב Q:: ואז את השאלה עצמה.\n" +
                        "לפני כל תשובה כתוב O:: ואז את התשובה עצמה.\n" +
                        "צור 1 עד 3 שאלות, ולכל שאלה 2 עד 4 תשובות קצרות וברורות.\n" +
                        "דוגמה: Q::מה הצבע האהוב עליך?O::צהובO::כחולO::ירוק\n" +
                        "אם הנושא לא מובן החזר בדיוק: Error";
        api.sendMessage(systemPrompt);


        JSONObject resp = api.sendMessage(topic);
        String extra = resp.optString("extra", "").trim();

        if (extra.equalsIgnoreCase("Error") || extra.isBlank()) {
            throw new RuntimeException("No valid survey text returned.");
        }


        List<Question> out = parseQOP(extra);

        if (out.isEmpty()) throw new RuntimeException("Parsed 0 questions from survey text.");
        if (out.size() > 3) out = out.subList(0, 3);
        return out;
    }


    private List<Question> parseQOP(String text) {
        List<Question> result = new ArrayList<>();
        String[] blocks = text.split("Q::");
        for (String blk : blocks) {
            String b = blk.trim();
            if (b.isEmpty()) continue;

            String[] parts = b.split("O::");
            String qText = parts[0].trim();
            List<String> opts = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String opt = parts[i].trim();
                if (!opt.isEmpty()) opts.add(opt);
            }

            if (!qText.isEmpty()) {
                try { result.add(new Question(qText, opts)); } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }
}
