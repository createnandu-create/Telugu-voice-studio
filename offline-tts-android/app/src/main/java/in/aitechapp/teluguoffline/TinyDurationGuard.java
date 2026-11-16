package in.aitechapp.teluguoffline;

import java.util.*;

/** Character-span duration guards for the pinned Telugu Tiny encoder. Never changes token IDs. */
public final class TinyDurationGuard {
  public static final int MIN_CV_FRAMES = 7; // ceil(65 ms * 24000 / 256)

  public static float[] apply(String text, float[] durations) {
    if (durations.length != text.length() * 2 + 1)
      throw new IllegalArgumentException("Duration/token alignment mismatch");
    float[] out = durations.clone();
    for (float d : out)
      if (!Float.isFinite(d) || d < 0) throw new IllegalArgumentException("Invalid duration");
    int begin = 0;
    while (begin < text.length()) {
      if (!telugu(text.charAt(begin))) {
        begin++;
        continue;
      }
      int end = begin + 1;
      while (end < text.length() && telugu(text.charAt(end))) end++;
      List<int[]> spans = new ArrayList<>();
      for (int i = begin; i < end; ) {
        int start = i++;
        while (i < end && mark(text.charAt(i))) {
          char m = text.charAt(i++);
          if (m == '్' && i < end && consonant(text.charAt(i))) i++;
        }
        spans.add(new int[] {start, i});
      }
      if (spans.size() > 4) for (int i = 2 * begin + 1; i <= 2 * end; i++) out[i] *= 1.10f;
      for (int k = 1; k < spans.size() - 1; k++) {
        int[] span = spans.get(k);
        char base = text.charAt(span[0]);
        String syllable = text.substring(span[0], span[1]);
        if ((base == 'మ' || base == 'వ' || base == 'గ') && syllable.indexOf('్') < 0) {
          float sum = 0;
          for (int i = span[0] * 2 + 1; i < span[1] * 2; i++) sum += out[i];
          // Add to the last character's acoustic span, not a silence/blank token.
          if (sum < MIN_CV_FRAMES) out[(span[1] - 1) * 2 + 1] += MIN_CV_FRAMES - sum;
        }
      }
      begin = end;
    }
    return out;
  }

  private static boolean telugu(char c) {
    return c >= '\u0c00' && c <= '\u0c7f';
  }

  private static boolean consonant(char c) {
    return c >= 'క' && c <= 'హ';
  }

  private static boolean mark(char c) {
    int type = Character.getType(c);
    return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
  }

  private TinyDurationGuard() {}
}
