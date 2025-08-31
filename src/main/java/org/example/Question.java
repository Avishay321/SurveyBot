package org.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Question {
    private final String text;
    private final List<String> options;
    private final Map<Long, Integer> votes;

    public Question(String text, List<String> options) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Question text cannot be empty.");
        if (options == null || options.size() < 2 || options.size() > 4)
            throw new IllegalArgumentException("Question must have 2–4 options.");
        this.text = text.strip();
        this.options = List.copyOf(options);
        this.votes = new HashMap<>();
    }


    public synchronized boolean vote(long userId, int optionIndex) {
        if (optionIndex < 0 || optionIndex >= options.size()) return false;
        if (votes.containsKey(userId)) return false;
        votes.put(userId, optionIndex);
        return true;
    }

    public String getText() { return text; }
    public List<String> getOptions() { return options; }

    public synchronized int[] getCounts() {
        int[] c = new int[options.size()];
        for (int idx : votes.values()) c[idx]++;
        return c;
    }

    public synchronized double[] getPercentages() {
        int total = votes.size();
        double[] p = new double[options.size()];
        if (total == 0) return p;
        int[] c = getCounts();
        for (int i = 0; i < c.length; i++) p[i] = (100.0 * c[i]) / total;
        return p;
    }


    public synchronized Set<Long> getVoterIds() {
        return new java.util.HashSet<>(votes.keySet());
    }
}
