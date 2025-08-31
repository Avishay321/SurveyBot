package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.*;

public class myBot extends TelegramLongPollingBot {
    private final List<Long> membersChatId = new ArrayList<>();
    public volatile Survey currentSurvey;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final Map<Long, Map<Integer, Integer>> userQuestionMsgIds = new ConcurrentHashMap<>();

    private static class SurveyTask {
        final Survey survey;
        final long earliestStartMillis;
        SurveyTask(Survey survey, long earliestStartMillis) {
            this.survey = survey; this.earliestStartMillis = earliestStartMillis;
        }
    }
    private final ConcurrentLinkedQueue<SurveyTask> pendingQueue = new ConcurrentLinkedQueue<>();

    private volatile Snapshot lastClosedSnapshot;

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) { handleCallback(update); return; }

            if (update.hasMessage() && update.getMessage().hasText()) {
                String userInput = update.getMessage().getText().trim();
                String lower = userInput.toLowerCase(Locale.ROOT);
                long chatId = update.getMessage().getChatId();

                boolean isJoinCmd = userInput.equals("/start")
                        || userInput.equals("היי")
                        || lower.equals("hi")
                        || lower.equals("hello");

                if (isJoinCmd) {
                    if (!membersChatId.contains(chatId)) {
                        membersChatId.add(chatId);

                        String first = (update.getMessage().getFrom()!=null && update.getMessage().getFrom().getFirstName()!=null)
                                ? update.getMessage().getFrom().getFirstName() : "חבר חדש";


                        sendMessage("ברוך/ה הבא/ה, " + first + "! הצטרפת לקהילה. גודל הקהילה כעת: " + membersChatId.size(), chatId);

                        for (Long m : new ArrayList<>(membersChatId)) {
                            if (!m.equals(chatId)) {
                                sendMessage(first + " הצטרף/ה לקהילה. גודל חדש: " + membersChatId.size(), m);
                            }
                        }
                    } else {
                        sendMessage("את/ה כבר חבר/ה בקהילה. כרגע ניתן רק לענות על סקרים שנשלחים בבוט.", chatId);
                    }
                    return;
                }


                if (userInput.equals("/survey")) {
                    sendMessage("יצירת סקר מתבצעת רק דרך הממשק (Launcher → \"סקר ידני (UI)\" או \"סקר ע\"י API\").", chatId);
                    return;
                }

                if (userInput.equals("/close")) {
                    Survey s = currentSurvey;
                    if (s != null && s.isActive()) {
                        Snapshot finalSnap = buildSnapshot(s);
                        finalSnap.active = false;
                        lastClosedSnapshot = finalSnap;

                        s.close(); currentSurvey = null;
                        broadcast("הסקר נסגר ידנית.");
                        synchronized (this) { tryLaunchNextPendingLocked(); }
                    } else sendMessage(" אין סקר פעיל לסגירה.", chatId);
                    return;
                }


                if (!membersChatId.contains(chatId)) {
                    sendMessage("כדי להצטרף לקהילה, שלח/י: /start או \"היי\" או \"Hi\" או \"hello\".", chatId);
                } else {
                    sendMessage("הבוט מיועד למענה על סקרים בלבד. המתן/י לסקר חדש והצביע/י דרך הכפתורים.", chatId);
                }
                return;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean hasMinMembers() { return membersChatId.size() >= 3; }

    public synchronized SurveyLaunchResult startSurveyForCommunity(List<Question> questions, long delayMillis) {
        if (questions == null || questions.isEmpty())
            return SurveyLaunchResult.NO_QUESTIONS;

        Survey survey = new Survey(0L, questions);

        if (delayMillis <= 0) {
            if (currentSurvey != null && currentSurvey.isActive())
                return SurveyLaunchResult.ACTIVE_SURVEY;
            if (!hasMinMembers())
                return SurveyLaunchResult.NOT_ENOUGH_MEMBERS;

            launchSurvey(survey);
            return SurveyLaunchResult.SUCCESS;
        }

        long fireAt = System.currentTimeMillis() + delayMillis;
        scheduler.schedule(() -> {
            synchronized (this) {
                if ((currentSurvey == null || !currentSurvey.isActive()) && hasMinMembers()) {
                    launchSurvey(survey);
                } else {
                    pendingQueue.add(new SurveyTask(survey, fireAt));
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);

        return SurveyLaunchResult.SCHEDULED;
    }


    private void launchSurvey(Survey survey) {
        currentSurvey = survey;
        currentSurvey.start();
        sendSurveyToAllMembers(currentSurvey);
        scheduleAutoClose(currentSurvey);
        broadcast("📨 נשלח סקר חדש לקהילה. ייסגר אוטומטית בעוד 5 דק׳ או מוקדם אם כולם ענו.");
    }

    private void scheduleAutoClose(Survey survey) {
        scheduler.schedule(() -> {
            synchronized (this) {
                Survey s = currentSurvey;
                if (s != null && s == survey && s.isActive()) {
                    Snapshot finalSnap = buildSnapshot(s);
                    finalSnap.active = false;
                    lastClosedSnapshot = finalSnap;

                    s.close(); currentSurvey = null;
                    broadcast("הסקר נסגר אוטומטית. תודה שהצבעתם!");
                }
                tryLaunchNextPendingLocked();
            }
        }, Survey.DURATION_MILLIS, TimeUnit.MILLISECONDS);
    }

    private synchronized void tryLaunchNextPendingLocked() {
        while (true) {
            SurveyTask head = pendingQueue.peek();
            if (head == null) return;

            long now = System.currentTimeMillis();
            if (now < head.earliestStartMillis) {
                scheduler.schedule(this::tryLaunchNextPending, head.earliestStartMillis - now, TimeUnit.MILLISECONDS);
                return;
            }
            if (currentSurvey != null && currentSurvey.isActive()) return;
            if (!hasMinMembers()) {
                scheduler.schedule(this::tryLaunchNextPending, 30_000, TimeUnit.MILLISECONDS);
                return;
            }

            pendingQueue.poll();
            launchSurvey(head.survey);
            return;
        }
    }
    private void tryLaunchNextPending() { synchronized (this) { tryLaunchNextPendingLocked(); } }



    private void handleCallback(Update update) {
        long userId = update.getCallbackQuery().getFrom().getId();
        String data = update.getCallbackQuery().getData();

        try { execute(new AnswerCallbackQuery(update.getCallbackQuery().getId())); }
        catch (TelegramApiException ignored) {}

        Survey s = currentSurvey;
        if (s != null && s.isExpired()) {
            Snapshot finalSnap = buildSnapshot(s);
            finalSnap.active = false;
            lastClosedSnapshot = finalSnap;

            s.close(); currentSurvey = null;
            sendMessage("הסקר נסגר.", userId);
            broadcast("הסקר נסגר (עבר הזמן).");
            synchronized (this) { tryLaunchNextPendingLocked(); }
            return;
        }
        if (s == null || !s.isOpen()) {
            sendMessage("אין סקר פעיל או שהוא נסגר.", userId);
            return;
        }

        int[] parsed = parseCallback(data);
        if (parsed == null) { sendMessage("בחירה לא חוקית.", userId); return; }

        int qIndex = parsed[0], optionIndex = parsed[1];
        boolean ok = s.recordVote(userId, qIndex, optionIndex);
        if (ok) {
            Integer msgId = null;
            Map<Integer,Integer> perUser = userQuestionMsgIds.get(userId);
            if (perUser != null) msgId = perUser.get(qIndex);

            if (msgId != null) {
                try {
                    EditMessageReplyMarkup clear = new EditMessageReplyMarkup();
                    clear.setChatId(String.valueOf(userId));
                    clear.setMessageId(msgId);
                    clear.setReplyMarkup(new InlineKeyboardMarkup(new ArrayList<>()));
                    execute(clear);

                    String voted = s.getQuestions().get(qIndex).getOptions().get(optionIndex);
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(String.valueOf(userId));
                    edit.setMessageId(msgId);
                    edit.setText("שאלה " + (qIndex+1) + ":\n" + s.getQuestions().get(qIndex).getText()
                            + "\n\nהצבעת: " + voted + " ✅");
                    execute(edit);
                } catch (TelegramApiException e) { e.printStackTrace(); }
            }
            sendMessage("✅ ההצבעה שלך נקלטה!", userId);
        } else {
            sendMessage("❌ כבר הצבעת על השאלה הזו / בחירה לא חוקית / הסקר סגור.", userId);
        }

        if (s.allMembersAnswered(membersChatId.size())) {
            // סגירה מוקדמת — עם סנאפשוט
            Snapshot finalSnap = buildSnapshot(s);
            finalSnap.active = false;
            lastClosedSnapshot = finalSnap;

            s.close(); currentSurvey = null;
            broadcast("כולם ענו! הסקר נסגר מוקדם.");
            synchronized (this) { tryLaunchNextPendingLocked(); }
        }
    }


    public void sendSurveyToAllMembers(Survey survey) {
        List<Question> qs = survey.getQuestions();
        List<Long> targets = new ArrayList<>(membersChatId);
        for (Long memberId : targets) {
            Map<Integer,Integer> perUser = userQuestionMsgIds.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>());
            for (int qi = 0; qi < qs.size(); qi++) {
                Question q = qs.get(qi);
                SendMessage msg = new SendMessage(String.valueOf(memberId),
                        "שאלה " + (qi+1) + ":\n" + q.getText());
                msg.setReplyMarkup(buildKeyboardForQuestion(qi, q));
                try {
                    var sent = execute(msg);
                    if (sent != null && sent.getMessageId() != null) perUser.put(qi, sent.getMessageId());
                } catch (TelegramApiException e) { e.printStackTrace(); }
            }
        }
    }

    private InlineKeyboardMarkup buildKeyboardForQuestion(int qIndex, Question q) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < q.getOptions().size(); i++) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(q.getOptions().get(i));
            btn.setCallbackData("SV|q:" + qIndex + "|o:" + i);
            rows.add(List.of(btn));
        }
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(rows);
        return kb;
    }

    private int[] parseCallback(String data) {
        if (data == null || !data.startsWith("SV|")) return null;
        int q=-1,o=-1;
        for (String p : data.split("\\|")) {
            if (p.startsWith("q:")) q = safeParseInt(p.substring(2));
            else if (p.startsWith("o:")) o = safeParseInt(p.substring(2));
        }
        return (q>=0 && o>=0) ? new int[]{q,o} : null;
    }
    private int safeParseInt(String s){ try { return Integer.parseInt(s);} catch(Exception e){ return -1; } }

    private void sendMessage(String text, long chatId){
        try { execute(new SendMessage(String.valueOf(chatId), text)); }
        catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void broadcast(String text){ for(Long m: new ArrayList<>(membersChatId)) sendMessage(text, m); }


    public static class QSnap { public String text; public List<String> options; public int[] counts; public double[] percent; }
    public static class Snapshot { public boolean active; public List<QSnap> questions = new ArrayList<>(); }

    private Snapshot buildSnapshot(Survey s) {
        Snapshot snap = new Snapshot();
        snap.active = s.isActive();
        for (Question q: s.getQuestions()){
            QSnap qs = new QSnap();
            qs.text = q.getText(); qs.options = q.getOptions();
            qs.counts = q.getCounts(); qs.percent = q.getPercentages();
            snap.questions.add(qs);
        }
        return snap;
    }

    public synchronized Snapshot getResultsSnapshot() {
        if (currentSurvey != null) {
            return buildSnapshot(currentSurvey);
        }
        return lastClosedSnapshot;
    }

    @Override public String getBotUsername() { return "survey321bot"; }
    @Override public String getBotToken() { return "8448669024:AAGe3CcjmdPCqWP0ujI_8JyOyXSnJziOe3Q"; }
}
