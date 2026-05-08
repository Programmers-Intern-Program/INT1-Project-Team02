package com.flodiback.domain.speech.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

public final class AssistantCallExtractor {

    private static final List<String> EXACT_WAKE_WORDS =
            List.of("AI야", "ai야", "에이아이야", "봇아", "클로드야", "플로디야", "플로디아", "플로드야", "flodiya", "plodiya");
    private static final List<String> FUZZY_FLODI_WAKE_WORDS = List.of("플로디야", "플로디아");
    private static final Pattern LEADING_PUNCTUATION = Pattern.compile("^[\\s,，.。?？!！:：;；-]+");
    private static final Pattern WORD_TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private AssistantCallExtractor() {}

    public static String extractQuestion(String speechText) {
        if (!StringUtils.hasText(speechText)) {
            return null;
        }

        Match match = findEarliestMatch(speechText);
        if (match == null) {
            return null;
        }

        String rawQuestion = speechText.substring(match.endIndex());
        String question =
                LEADING_PUNCTUATION.matcher(rawQuestion).replaceFirst("").strip();

        return StringUtils.hasText(question) ? question : null;
    }

    public static boolean containsWakeWord(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return findEarliestMatch(text) != null;
    }

    private static Match findEarliestMatch(String text) {
        Match exactMatch = findExactMatch(text);
        Match fuzzyMatch = findFuzzyFlodiMatch(text);

        if (exactMatch == null) {
            return fuzzyMatch;
        }
        if (fuzzyMatch == null) {
            return exactMatch;
        }
        return exactMatch.startIndex() <= fuzzyMatch.startIndex() ? exactMatch : fuzzyMatch;
    }

    private static Match findExactMatch(String text) {
        int wakeWordIndex = Integer.MAX_VALUE;
        String matchedWakeWord = null;

        for (String wakeWord : EXACT_WAKE_WORDS) {
            int index = text.indexOf(wakeWord);
            if (index >= 0 && index < wakeWordIndex) {
                wakeWordIndex = index;
                matchedWakeWord = wakeWord;
            }
        }

        if (matchedWakeWord == null) {
            return null;
        }
        return new Match(wakeWordIndex, wakeWordIndex + matchedWakeWord.length());
    }

    private static Match findFuzzyFlodiMatch(String text) {
        Matcher matcher = WORD_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (isLikelyFlodiWakeWord(token)) {
                return new Match(matcher.start(), matcher.end());
            }
        }
        return null;
    }

    private static boolean isLikelyFlodiWakeWord(String token) {
        if (token.length() < 3 || token.length() > 5) {
            return false;
        }
        if (!(token.endsWith("야") || token.endsWith("아"))) {
            return false;
        }
        if (!(token.startsWith("플") || token.startsWith("필"))) {
            return false;
        }

        for (String wakeWord : FUZZY_FLODI_WAKE_WORDS) {
            if (levenshteinDistance(token, wakeWord) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static int levenshteinDistance(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + substitutionCost);
            }
        }
        return distance[left.length()][right.length()];
    }

    private record Match(int startIndex, int endIndex) {}
}
