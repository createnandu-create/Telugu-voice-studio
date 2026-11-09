package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;

/** Shared, host-tested integrity and atomic persistence primitives. */
public final class FileIntegrity {
  public static String sha256(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      MessageDigest d = digest();
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) d.update(b, 0, n);
      return hex(d.digest());
    }
  }

  public static String sha256(String value) {
    return hex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return s.toString();
  }

  public static boolean matches(File f, String sha) throws IOException {
    return f.isFile() && sha256(f).equals(sha);
  }

  public static void promote(File part, File destination) throws IOException {
    Files.move(
        part.toPath(),
        destination.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }

  public static void write(File file, String value) throws IOException {
    File part = new File(file.getPath() + ".tmp");
    try (FileOutputStream out = new FileOutputStream(part)) {
      out.write(value.getBytes(StandardCharsets.UTF_8));
      out.getFD().sync();
    }
    promote(part, file);
  }

  public static String read(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }

  private FileIntegrity() {}
}
