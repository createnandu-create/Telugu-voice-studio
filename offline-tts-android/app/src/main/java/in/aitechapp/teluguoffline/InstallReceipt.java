package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.Properties;

/** No hash or network on normal reuse; explicit Verify detects same-size/same-date corruption. */
public final class InstallReceipt {
  public static boolean ready(File model, File receipt, String sha, String revision)
      throws IOException {
    if (!model.isFile() || !receipt.isFile()) return false;
    Properties p = load(receipt);
    return "verified".equals(p.getProperty("state"))
        && sha.equals(p.getProperty("sha256"))
        && revision.equals(p.getProperty("revision"))
        && model.getCanonicalPath().equals(p.getProperty("path"))
        && Long.toString(model.length()).equals(p.getProperty("size"))
        && Long.toString(model.lastModified()).equals(p.getProperty("modified"));
  }

  public static boolean verify(File model, File receipt, String sha, String revision)
      throws IOException {
    if (!FileIntegrity.matches(model, sha)) {
      Properties bad = new Properties();
      bad.setProperty("state", "damaged");
      bad.setProperty("revision", revision);
      save(receipt, bad);
      return false;
    }
    Properties p = new Properties();
    p.setProperty("state", "verified");
    p.setProperty("sha256", sha);
    p.setProperty("revision", revision);
    p.setProperty("path", model.getCanonicalPath());
    p.setProperty("size", Long.toString(model.length()));
    p.setProperty("modified", Long.toString(model.lastModified()));
    p.setProperty("verifiedAt", Long.toString(System.currentTimeMillis()));
    save(receipt, p);
    return true;
  }

  public static Properties load(File file) throws IOException {
    Properties p = new Properties();
    if (file.isFile())
      try (InputStream in = new FileInputStream(file)) {
        p.load(in);
      }
    return p;
  }

  public static void save(File file, Properties values) throws IOException {
    StringWriter text = new StringWriter();
    values.store(text, "Local model/download state");
    FileIntegrity.write(file, text.toString());
  }

  private InstallReceipt() {}
}
