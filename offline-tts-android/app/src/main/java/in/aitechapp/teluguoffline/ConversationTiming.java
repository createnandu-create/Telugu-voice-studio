package in.aitechapp.teluguoffline;

import java.util.Arrays;

/** Selects intact neural takes using Telugu conversational utterance durations. */
final class ConversationTiming {
  static final class UnusableTake extends java.io.IOException {
    UnusableTake(String message) { super(message); }
  }

  interface Synthesizer {
    float[] generate(float rate) throws Exception;
  }

  // Fit and dataset attribution in tools/fit-conversation-timing.py.
  static double targetSeconds(String spoken, float rate) {
    int letters = 0, clusters = 0, words = 0;
    boolean hasLetter = false;
    for (int i = 0; i < spoken.length(); i++) {
      char c = spoken.charAt(i);
      if ((c >= 0x0c05 && c <= 0x0c14) || (c >= 0x0c15 && c <= 0x0c39)) {
        letters++;
        if (!hasLetter) words++;
        hasLetter = true;
      } else if (c == '్') clusters++;
      else if (Character.isWhitespace(c)) hasLetter = false;
      else if (Character.isLetterOrDigit(c) && Character.UnicodeBlock.of(c) != Character.UnicodeBlock.TELUGU)
        return 0; // Unknown spoken expansion; keep the narrator's timing.
    }
    int nuclei = letters - clusters;
    if (nuclei < 4 || words < 2) return 0;
    return (.01259 + .11805 * nuclei + .15111 * words + .06349 * clusters) / rate;
  }

  // Only remove excess near-silence outside the utterance. Keep a margin for quiet consonants.
  static float[] trimPadding(float[] audio, int hz) {
    int first = 0, end = audio.length;
    while (first < end && Math.abs(audio[first]) < .001f) first++;
    if (first == end) return audio;
    while (end > first && Math.abs(audio[end - 1]) < .001f) end--;
    first = Math.max(0, first - hz / 40);
    end = Math.min(audio.length, end + hz / 25);
    return first == 0 && end == audio.length ? audio : Arrays.copyOfRange(audio, first, end);
  }

  static void shapeDelivery(float[] pcm, NarrationDirector.Emotion emotion) {
    double accent;
    switch (emotion) {
      case EXCITED: case SURPRISE: accent = 1.8; break;
      case WELCOME: case CALL_TO_ACTION: accent = 1.2; break;
      case GUIDE: accent = 1.0; break;
      case QUESTION: accent = .8; break;
      case EMPATHY: case SERIOUS: accent = -1.1; break;
      default: return;
    }
    // A broad, smooth energy arc follows the opening stressed part of the phrase.
    // A single headroom adjustment preserves the arc without clipping or changing pitch.
    double peak = 0;
    for (int i = 0; i < pcm.length; i++) {
      double t = i / (double)Math.max(1, pcm.length - 1);
      double envelope = Math.sin(Math.PI * t) * (1 - t) * 1.6;
      pcm[i] *= (float)Math.pow(10, accent * envelope / 20);
      peak = Math.max(peak, Math.abs(pcm[i]));
    }
    if (peak > .98) for (int i = 0; i < pcm.length; i++) pcm[i] *= (float)(.98 / peak);
  }

  static float candidateRate(double target, double seconds, float rate) {
    if (target <= 0 || seconds < .45 || seconds > 18) return rate;
    double ratio = seconds / target;
    if (Math.abs(Math.log(ratio)) < .07 || ratio < .48 || ratio > 2.1) return rate;
    return (float) Math.max(.5, Math.min(2, rate * Math.min(1.20, Math.max(.85, ratio))));
  }

  static float[] render(String text, float rate, int hz, boolean enabled, Synthesizer synth)
      throws Exception {
    float[] first = synth.generate(rate);
    if (!enabled) return first;
    first = trimPadding(first, hz);
    double target = targetSeconds(text, rate);
    double seconds = first.length / (double) hz;
    float next = candidateRate(target, seconds, rate);
    if (Math.abs(next - rate) < .001f) return first;
    float[] second;
    try {
      second = trimPadding(synth.generate(next), hz);
    } catch (UnusableTake rejected) {
      return first;
    }
    double revised = second.length / (double) hz;
    // Reject truncated/runaway candidates even when their duration is nominally nearer.
    if (!audible(second)) return first;
    if (revised < .72 * seconds || revised > 1.35 * seconds) return first;
    return Math.abs(Math.log(revised / target)) < Math.abs(Math.log(seconds / target)) ? second : first;
  }

  private static boolean audible(float[] samples) {
    int active = 0;
    for (float x : samples) {
      if (!Float.isFinite(x)) return false;
      if (Math.abs(x) >= .001f) active++;
    }
    return active >= 64;
  }

  private ConversationTiming() {}
}
