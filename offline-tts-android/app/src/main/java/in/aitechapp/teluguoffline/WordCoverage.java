package in.aitechapp.teluguoffline;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Align the expected spoken words against a local transcript. ASR spaces are unreliable in Telugu. */
final class WordCoverage {
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");
  final double characterError;
  final int missing;
  final int firstMissingOffset;

  private WordCoverage(double characterError, int missing, int firstMissingOffset) {
    this.characterError = characterError;
    this.missing = missing;
    this.firstMissingOffset = firstMissingOffset;
  }

  static WordCoverage compare(String expected, String heard) {
    String source = Normalizer.normalize(expected, Normalizer.Form.NFC);
    // Include shaping marks in the matched span to keep offsets in the source text.
    Matcher words = SOURCE_WORD.matcher(source);
    StringBuilder letters = new StringBuilder();
    int[] ends = new int[source.length()], starts = new int[source.length()];
    int count = 0;
    while (words.find()) {
      String word = words.group().replace("\u200c", "").replace("\u200d", "");
      if (word.isEmpty()) continue;
      letters.append(word);
      starts[count] = words.start();
      ends[count++] = letters.length();
    }
    String transcript = Normalizer.normalize(heard, Normalizer.Form.NFC);
    StringBuilder target = new StringBuilder();
    Matcher parts = WORD.matcher(transcript);
    while (parts.find()) target.append(parts.group());

    int n = letters.length(), m = target.length();
    int[] row = new int[m + 1];
    for (int j = 0; j <= m; j++) row[j] = j;
    byte[][] path = new byte[n + 1][m + 1];
    for (int i = 1; i <= n; i++) {
      int diagonal = row[0];
      row[0] = i;
      for (int j = 1; j <= m; j++) {
        int above = row[j];
        int paired = diagonal + (letters.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1);
        int deletion = above + 1, insertion = row[j - 1] + 1;
        row[j] = Math.min(paired, Math.min(deletion, insertion));
        path[i][j] = (byte)(row[j] == paired ? 0 : row[j] == deletion ? 1 : 2);
        diagonal = above;
      }
    }
    boolean[] deleted = new boolean[n];
    for (int i = n, j = m; i > 0;) {
      if (j == 0 || path[i][j] == 1) deleted[--i] = true;
      else if (path[i][j] == 2) j--;
      else { i--; j--; }
    }
    int start = 0, missing = 0, first = -1;
    for (int w = 0; w < count; w++) {
      int drops = 0, length = ends[w] - start;
      for (int i = start; i < ends[w]; i++) if (deleted[i]) drops++;
      if (drops > 0 && drops * 4 >= length * 3) {
        if (missing++ == 0) first = starts[w];
      }
      start = ends[w];
    }
    return new WordCoverage(row[m] / (double)Math.max(1, n), missing, first);
  }

  private static final Pattern SOURCE_WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}\u200c\u200d]+");
}
