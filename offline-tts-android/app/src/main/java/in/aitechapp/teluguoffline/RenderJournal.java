package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.List;

/**
 * Completed chunks are reused only for the exact engine/model/text/controls and matching PCM
 * checksums.
 */
public final class RenderJournal {
  public final File directory;

  public RenderJournal(File parent, ModelCatalog.Voice voice, List<NarrationDirector.Segment> plan)
      throws IOException {
    this(parent, voice, plan, "");
  }

  public RenderJournal(
      File parent,
      ModelCatalog.Voice voice,
      List<NarrationDirector.Segment> plan,
      String speechIdentity)
      throws IOException {
    StringBuilder identity =
        new StringBuilder(voice.tiny() ? "tiny-java-v1-ort1.31/" : "sherpa-1.12.26/pcm-v1/")
            .append(voice.installedSha);
    if (!speechIdentity.isEmpty()) identity.append("/speech-v2/").append(speechIdentity);
    for (NarrationDirector.Segment s : plan)
      identity
          .append('\n')
          .append(s.text.length())
          .append(':')
          .append(s.text)
          .append('|')
          .append(s.rate)
          .append('|')
          .append(s.pauseAfterMs);
    directory = new File(parent, FileIntegrity.sha256(identity.toString()));
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Cannot create render storage.");
  }

  public File chunk(int i) {
    return new File(directory, String.format(java.util.Locale.ROOT, "%05d.wav", i));
  }

  private File receipt(int i) {
    return new File(directory, i + ".sha256");
  }

  public boolean reusable(int i) throws IOException {
    if (!receipt(i).isFile() || !chunk(i).isFile()) return false;
    try {
      PcmWave.validate(chunk(i));
      return FileIntegrity.matches(chunk(i), FileIntegrity.read(receipt(i)));
    } catch (IOException e) {
      return false;
    }
  }

  public void complete(int i) throws IOException {
    PcmWave.validate(chunk(i));
    FileIntegrity.write(receipt(i), FileIntegrity.sha256(chunk(i)));
  }
}
