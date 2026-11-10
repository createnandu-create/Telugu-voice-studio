package in.aitechapp.teluguoffline;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class LifecycleTest {
  static int checks;

  static void check(boolean b, String s) {
    if (!b) throw new AssertionError(s);
    checks++;
  }

  public static void main(String[] args) throws Exception {
    String[] phrases = {
      "నమస్కారం",
      "నమస్కారం ఫ్రెండ్స్!",
      "ఇప్పుడు ఇక్కడ క్లిక్ చేయండి.",
      "ఇది చాలా ముఖ్యమైన విషయం. జాగ్రత్తగా వినండి."
    };
    for (String s : phrases)
      check(
          PronunciationLexicon.apply(s, Collections.emptyMap(), false).equals(s),
          "original Telugu preserved");
    Map<String, String> words = new LinkedHashMap<>();
    words.put("friends", "ఫ్రెండ్స్");
    words.put("click", "క్లిక్");
    check(
        PronunciationLexicon.apply("friends click myfriends", words, false)
            .equals("ఫ్రెండ్స్ క్లిక్ myfriends"),
        "word boundaries");
    check(
        PronunciationLexicon.apply("నమస్కారం 15", words, true).equals("నమస్కారం ఒకటి ఐదు"),
        "explicit digit reading");
    check(
        PronunciationLexicon.apply("నమస్కారం 15", words, false).equals("నమస్కారం 15"),
        "numbers not silently rewritten");
    RecordingClock clock = new RecordingClock();
    clock.start(100);
    check(clock.elapsed(10100) == 10000, "record ten seconds");
    clock.pause(10100);
    check(clock.elapsed(50100) == 10000, "pause excludes wall time");
    clock.resume(50100);
    check(clock.elapsed(90100) == 50000, "navigation does not reset clock");
    check(clock.limit(100100), "60 second cap");
    clock.stop(100100);
    check(clock.elapsed(999999) == 60000, "stop stable across reopen");
    File root = Files.createTempDirectory("model-lifecycle").toFile();
    byte[] data = new byte[300000];
    new Random(1).nextBytes(data);
    File remote = new File(root, "fixture");
    Files.write(remote.toPath(), data);
    String sha = FileIntegrity.sha256(remote);
    AtomicInteger requests = new AtomicInteger();
    AtomicLong resumed = new AtomicLong();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/model",
        exchange -> {
          requests.incrementAndGet();
          String range = exchange.getRequestHeaders().getFirst("Range");
          int offset = range == null ? 0 : Integer.parseInt(range.substring(6, range.length() - 1));
          resumed.set(offset);
          exchange.getResponseHeaders().set("ETag", "\"pinned\"");
          if (offset > 0)
            exchange
                .getResponseHeaders()
                .set(
                    "Content-Range",
                    "bytes " + offset + "-" + (data.length - 1) + "/" + data.length);
          exchange.sendResponseHeaders(offset > 0 ? 206 : 200, data.length - offset);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(data, offset, data.length - offset);
          }
        });
    server.createContext(
        "/no-range",
        e -> {
          e.sendResponseHeaders(200, data.length);
          try (OutputStream out = e.getResponseBody()) {
            out.write(data);
          }
        });
    server.createContext(
        "/unavailable",
        e -> {
          e.sendResponseHeaders(503, -1);
          e.close();
        });
    server.createContext(
        "/changed",
        e -> {
          e.getResponseHeaders().set("ETag", "\"changed\"");
          e.getResponseHeaders().set("Content-Range", "bytes 100000-299999/300000");
          e.sendResponseHeaders(206, 200000);
          try (OutputStream out = e.getResponseBody()) {
            out.write(data, 100000, 200000);
          }
        });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/model";
      File part = new File(root, "model.part");
      Files.write(part.toPath(), Arrays.copyOf(data, 100000));
      Properties state = new Properties();
      state.setProperty("url", url);
      state.setProperty("sha256", sha);
      state.setProperty("etag", "\"pinned\"");
      InstallReceipt.save(new File(part.getPath() + ".properties"), state);
      ResumableDownload download = new ResumableDownload(true);
      download.fetch(url, part, data.length, sha, new AtomicBoolean(), (b, t) -> {});
      check(
          resumed.get() == 100000 && FileIntegrity.matches(part, sha),
          "HTTP Range resumes exact bytes");
      int before = requests.get();
      download.fetch(url, part, data.length, sha, new AtomicBoolean(), (b, t) -> {});
      check(requests.get() == before, "verified download not repeated");
      File receipt = new File(root, "installed.properties");
      check(InstallReceipt.verify(part, receipt, sha, "v1"), "verify and atomic receipt");
      check(InstallReceipt.ready(part, receipt, sha, "v1"), "offline metadata reuse");
      check(!InstallReceipt.ready(part, receipt, sha, "v2"), "version mismatch rejected");
      long modified = part.lastModified();
      try (RandomAccessFile f = new RandomAccessFile(part, "rw")) {
        f.seek(10);
        f.write(3);
      }
      part.setLastModified(modified);
      check(
          !InstallReceipt.verify(part, receipt, sha, "v1"),
          "explicit Verify detects same-length corruption");
      check(!InstallReceipt.ready(part, receipt, sha, "v1"), "damaged manifest not ready");
      Files.write(part.toPath(), Arrays.copyOf(data, 100000));
      boolean paused = false;
      try {
        download.fetch(url, part, data.length, sha, new AtomicBoolean(true), (b, t) -> {});
      } catch (java.util.concurrent.CancellationException e) {
        paused = true;
      }
      check(paused && part.length() == 100000, "cancellation retains partial bytes");
      for (String endpoint : new String[] {"no-range", "unavailable", "changed"}) {
        String address = url.replace("/model", "/" + endpoint);
        Files.write(part.toPath(), Arrays.copyOf(data, 100000));
        state.setProperty("url", address);
        InstallReceipt.save(new File(part.getPath() + ".properties"), state);
        boolean failed = false;
        try {
          download.fetch(address, part, data.length, sha, new AtomicBoolean(), (b, t) -> {});
        } catch (IOException e) {
          failed = true;
        }
        if (endpoint.equals("no-range"))
          check(
              !failed && FileIntegrity.matches(part, sha),
              "HTTP 200 safely restarts unsupported Range");
        else
          check(
              failed && part.length() == 100000,
              endpoint + " retains partial and rejects invalid content");
      }
      clock.start(120000);
      clock.stop(125000);
      check(clock.elapsed(180000) == 5000, "interruption records actual duration, not 60s");
    } finally {
      server.stop(0);
      try (java.util.stream.Stream<Path> paths = Files.walk(root.toPath())) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.delete(p);
                  } catch (IOException ignored) {
                  }
                });
      }
    }
    System.out.println("PASS: " + checks + " pronunciation/recording/download lifecycle checks");
  }
}
