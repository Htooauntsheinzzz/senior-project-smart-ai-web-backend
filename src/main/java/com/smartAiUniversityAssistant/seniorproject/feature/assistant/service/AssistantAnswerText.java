package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import java.util.regex.Pattern;

/**
 * Turns a model answer into the plain chat text the apps show. The prompts ask for plain text, but a model still writes
 * Markdown now and then; the mobile app and the admin panel print the answer as it is, so "**March 1**" would reach the
 * reader with its asterisks. Only Markdown syntax is removed: words, numbers, punctuation and line breaks stay.
 */
public final class AssistantAnswerText {
    private static final Pattern FENCE = Pattern.compile("(?m)^[ \\t]*(?:```|~~~)[^\\n]*\\n?");
    private static final Pattern RULE = Pattern.compile("(?m)^[ \\t]*([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$\\n?");
    private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+(.*?)[ \\t#]*$");
    private static final Pattern QUOTE = Pattern.compile("(?m)^[ \\t]{0,3}>[ \\t]?");
    private static final Pattern BULLET = Pattern.compile("(?m)^([ \\t]*)[*+\\u2022][ \\t]+");
    private static final Pattern LINK = Pattern.compile("!?\\[([^\\]\\n]+)]\\(([^)\\s]+)(?:[ \\t]+\"[^\"\\n]*\")?\\)");
    private static final Pattern STRONG = Pattern.compile("(\\*{2,3})(?=\\S)(.+?)(?<=\\S)\\1");
    private static final Pattern STRONG_UNDERSCORE =
            Pattern.compile("(?<![\\p{L}\\p{N}_])(_{2,3})(?=\\S)(.+?)(?<=\\S)\\1(?![\\p{L}\\p{N}_])");
    // "5*3*2" and "a * b" are not emphasis: the opening star is followed by text and neither star touches a digit or star.
    private static final Pattern EMPHASIS = Pattern.compile("(?<![\\p{N}*])\\*(?=[^\\s*])([^*\\n]+?)(?<=[^\\s*])\\*(?![\\p{N}*])");
    // An underscore inside a word (a file name such as academic_calendar_2) is kept.
    private static final Pattern EMPHASIS_UNDERSCORE =
            Pattern.compile("(?<![\\p{L}\\p{N}_])_(?=[^\\s_])([^_\\n]+?)(?<=[^\\s_])_(?![\\p{L}\\p{N}_])");
    private static final Pattern STRIKE = Pattern.compile("~~(?=\\S)(.+?)(?<=\\S)~~");
    private static final Pattern CODE = Pattern.compile("`+([^`\\n]+?)`+");
    // Characters Markdown lets a writer escape ("\\*" is a literal star). They are parked in the private use area while the
    // syntax is removed, so an escaped star is never read as emphasis.
    private static final String ESCAPABLE = "\\`*_{}[]()#+-.!>~|";
    private static final Pattern ESCAPE = Pattern.compile("\\\\([\\\\`*_{}\\[\\]()#+\\-.!>~|])");
    private static final char PARKED = '\uE000';
    private static final Pattern TRAILING_SPACE = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private AssistantAnswerText() {}

    public static String plain(String answer) {
        if (answer == null) return "";
        String text = answer.replace("\r\n", "\n").replace('\r', '\n');
        text = ESCAPE.matcher(text).replaceAll(match -> String.valueOf((char) (PARKED + ESCAPABLE.indexOf(match.group(1)))));
        text = FENCE.matcher(text).replaceAll("");
        text = RULE.matcher(text).replaceAll("");
        text = HEADING.matcher(text).replaceAll("$1");
        text = QUOTE.matcher(text).replaceAll("");
        text = BULLET.matcher(text).replaceAll("$1- ");
        text = LINK.matcher(text).replaceAll("$1 ($2)");
        text = STRONG.matcher(text).replaceAll("$2");
        text = STRONG_UNDERSCORE.matcher(text).replaceAll("$2");
        text = EMPHASIS.matcher(text).replaceAll("$1");
        text = EMPHASIS_UNDERSCORE.matcher(text).replaceAll("$1");
        text = STRIKE.matcher(text).replaceAll("$1");
        text = CODE.matcher(text).replaceAll("$1");
        text = TRAILING_SPACE.matcher(text).replaceAll("");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
        var restored = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            restored.append(c >= PARKED && c < PARKED + ESCAPABLE.length() ? ESCAPABLE.charAt(c - PARKED) : c);
        }
        return restored.toString();
    }
}
