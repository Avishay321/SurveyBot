package org.example;

import java.util.List;

public interface ChatSurveyGenerator {
    List<Question> generate(String topic) throws Exception;
}

