package com.ashutosh.dotnetinterviewhub;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owns text-to-speech state so document screens contain only UI logic. */
public final class SpeechController {
    public interface Listener {
        void onReady(boolean ready);
        void onState(String status, boolean speaking, boolean paused);
    }

    private final Listener listener;
    private TextToSpeech engine;
    private volatile boolean ready;
    private volatile boolean speaking;
    private volatile boolean paused;
    private int session;
    private volatile int current;
    private volatile int resumeOffset;
    private int utteranceBaseOffset;
    private int utteranceSequence;
    private volatile String activeUtteranceId = "";
    private float rate;
    private final List<String> chunks = new ArrayList<>();

    public SpeechController(Context context, float rate, Listener listener) {
        this.listener = listener; this.rate = rate;
        engine = new TextToSpeech(context.getApplicationContext(), this::onInitialized);
    }

    private void onInitialized(int status) {
        if (status != TextToSpeech.SUCCESS || engine == null) {
            notifyState("Text-to-speech could not be started on this phone."); listener.onReady(false); return;
        }
        int language = engine.setLanguage(new Locale("en", "IN"));
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED)
            language = engine.setLanguage(Locale.US);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            notifyState("An English text-to-speech voice is not installed on this phone."); listener.onReady(false); return;
        }
        engine.setSpeechRate(rate); engine.setPitch(1.0f);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                if (isCurrent(id)) notifyState("Reading section " + (current + 1) + " of " + chunks.size());
            }
            @Override public void onDone(String id) {
                if (!isCurrent(id) || paused || !speaking) return;
                resumeOffset = 0;
                current++;
                if (current < chunks.size()) speakCurrent();
                else { speaking = false; paused = false; notifyState("Finished reading this document."); }
            }
            @Override public void onError(String id) {
                // Some speech engines report a stopped utterance as an error. A user pause is not an error.
                if (isCurrent(id) && !paused) {
                    speaking = false; notifyState("Reading stopped because the speech service reported an error.");
                }
            }
            @Override public void onRangeStart(String id, int start, int end, int frame) {
                if (isCurrent(id)) resumeOffset = Math.max(0, utteranceBaseOffset + start);
            }
        });
        ready = true; listener.onReady(true); notifyState("Ready to read this document aloud.");
    }

    public void playOrResume(String content) {
        if (!ready) { notifyState("The phone's text-to-speech service is not ready."); return; }
        if (paused && !chunks.isEmpty()) { paused = false; speaking = true; speakCurrent(); return; }
        stop(true); chunks.addAll(split(clean(content))); current = 0; resumeOffset = 0;
        if (chunks.isEmpty()) { notifyState("There is no readable content in this document."); return; }
        speaking = true; speakCurrent();
    }

    public void pause() {
        if (!speaking || engine == null) return;
        paused = true; speaking = false; engine.stop();
        notifyState("Paused. Resume will continue from this position.");
    }

    public void stop(boolean silent) {
        session++; speaking = false; paused = false; current = 0; resumeOffset = 0;
        activeUtteranceId = ""; chunks.clear();
        if (engine != null) engine.stop(); if (!silent) notifyState("Reading stopped.");
    }

    public void setRate(float rate) {
        this.rate = rate; if (engine != null) engine.setSpeechRate(rate);
        if (ready && !speaking && !paused) notifyState("Reading speed set to " + rate + "x.");
    }

    public boolean isReady() { return ready; }
    public boolean isSpeaking() { return speaking; }
    public boolean isPaused() { return paused; }

    public void shutdown() {
        if (engine != null) { engine.stop(); engine.shutdown(); engine = null; }
    }

    private void speakCurrent() {
        if (!ready || engine == null || current >= chunks.size()) return;
        String fullChunk = chunks.get(current);
        if (resumeOffset >= fullChunk.length()) {
            resumeOffset = 0; current++;
            if (current < chunks.size()) speakCurrent();
            else { speaking = false; paused = false; notifyState("Finished reading this document."); }
            return;
        }
        speaking = true; paused = false;
        utteranceBaseOffset = resumeOffset;
        activeUtteranceId = "knowledge-" + session + "-section-" + current + "-take-" + (++utteranceSequence);
        int result = engine.speak(fullChunk.substring(resumeOffset), TextToSpeech.QUEUE_FLUSH,
                new Bundle(), activeUtteranceId);
        if (result == TextToSpeech.ERROR) { speaking = false; notifyState("The phone could not start reading this section."); }
        else notifyState("Reading section " + (current + 1) + " of " + chunks.size());
    }

    private boolean isCurrent(String id) { return id != null && id.equals(activeUtteranceId); }
    private void notifyState(String status) { listener.onState(status, speaking, paused); }

    private static String clean(String content) {
        return content.replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("(?m)^[-*•]\\s*", "")
                .replace("`", "").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static List<String> split(String text) {
        // Short chunks provide a close fallback on Android 7, where word-range callbacks are unavailable.
        int limit = Math.min(500, TextToSpeech.getMaxSpeechInputLength() - 200);
        List<String> result = new ArrayList<>(); StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            for (String sentence : paragraph.trim().split("(?<=[.!?])\\s+")) {
                String remaining = sentence.trim();
                while (remaining.length() > limit) {
                    int at = remaining.lastIndexOf(' ', limit); if (at < limit / 2) at = limit;
                    append(result, current, remaining.substring(0, at), limit); remaining = remaining.substring(at).trim();
                }
                if (!remaining.isEmpty()) append(result, current, remaining, limit);
            }
            if (current.length() > 0) { result.add(current.toString()); current.setLength(0); }
        }
        if (current.length() > 0) result.add(current.toString()); return result;
    }

    private static void append(List<String> result, StringBuilder current, String value, int limit) {
        int extra = current.length() == 0 ? value.length() : value.length() + 2;
        if (current.length() > 0 && current.length() + extra > limit) { result.add(current.toString()); current.setLength(0); }
        if (current.length() > 0) current.append("\n\n"); current.append(value);
    }
}
