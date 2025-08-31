package org.example;

import java.util.List;
import java.util.Set;

public class Survey {
    public static final long DURATION_MILLIS = 5 * 60 * 1000;

    private final long ownerChatId;
    private final List<Question> questions;

    private long startTime;
    private long deadline;
    private boolean active;

    public Survey(long ownerChatId, List<Question> questions) {
        if (questions == null || questions.size() < 1 || questions.size() > 3)
            throw new IllegalArgumentException("Survey must have 1–3 questions.");
        this.ownerChatId = ownerChatId;
        this.questions = List.copyOf(questions);
    }

    public synchronized void start() {
        if (active) return;
        startTime = System.currentTimeMillis();
        deadline  = startTime + DURATION_MILLIS;
        active = true;
    }

    public synchronized void close()        { active = false; }
    public synchronized boolean isExpired() { return active && System.currentTimeMillis() >= deadline; }
    public synchronized boolean isOpen()    { return active && !isExpired(); }

    public synchronized boolean recordVote(long userId, int qIndex, int optionIndex) {
        if (!isOpen()) return false;
        if (qIndex < 0 || qIndex >= questions.size()) return false;
        return questions.get(qIndex).vote(userId, optionIndex);
    }

    public synchronized boolean allMembersAnswered(int totalMembers) {
        if (totalMembers <= 0) return false;
        Set<Long> intersection = null;
        for (Question q : questions) {
            Set<Long> voters = q.getVoterIds();
            if (intersection == null) {
                intersection = voters;
            } else {
                intersection.retainAll(voters);
            }
            if (intersection.isEmpty()) return false;
        }
        return intersection != null && intersection.size() >= totalMembers;
    }

    public long getOwnerChatId() { return ownerChatId; }
    public synchronized List<Question> getQuestions() { return questions; }
    public synchronized boolean isActive() { return active; }
    public synchronized long getDeadline() { return deadline; }
}
