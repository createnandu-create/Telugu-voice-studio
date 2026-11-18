package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;

/** Reads the pinned SentencePiece unigram model (normal pieces plus UTF-8 fallback). */
final class HimaTokenizer {
  private final Map<String,Piece> dictionary = new HashMap<>();
  private final int[] bytes = new int[256];
  private int maxLength;
  private float unknownScore = Float.POSITIVE_INFINITY;

  private static final class Piece {
    int id, type = 1;
    String text;
    float score;
  }

  HimaTokenizer(File model) throws IOException {
    ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(model.toPath())).order(ByteOrder.LITTLE_ENDIAN);
    int id = 0;
    while (data.hasRemaining()) {
      int tag = varint(data);
      if (tag != 10) { skip(data,tag); continue; }
      ByteBuffer entry = block(data);
      Piece piece = new Piece();
      piece.id = id++;
      while (entry.hasRemaining()) {
        int key = varint(entry);
        switch (key) {
          case 10: piece.text = StandardCharsets.UTF_8.decode(block(entry)).toString(); break;
          case 21: piece.score = entry.getFloat(); break;
          case 24: piece.type = varint(entry); break;
          default: skip(entry,key);
        }
      }
      if (piece.type == 1) {
        dictionary.put(piece.text,piece);
        maxLength = Math.max(maxLength,piece.text.length());
        unknownScore = Math.min(unknownScore,piece.score);
      } else if (piece.type == 6) bytes[Integer.parseInt(piece.text.substring(3,5),16)] = piece.id;
    }
    if (id != 16000 || bytes[255] == 0) throw new IOException("Invalid Telugu tokenizer");
  }

  private static int varint(ByteBuffer b) throws IOException {
    int result = 0;
    for (int shift = 0; shift < 32 && b.hasRemaining(); shift += 7) {
      int v = b.get() & 255;
      result |= (v & 127) << shift;
      if (v < 128) return result;
    }
    throw new IOException("Invalid tokenizer field");
  }

  private static ByteBuffer block(ByteBuffer b) throws IOException {
    int size = varint(b);
    if (size < 0 || size > b.remaining()) throw new IOException("Invalid tokenizer length");
    ByteBuffer part = b.slice().order(ByteOrder.LITTLE_ENDIAN);
    part.limit(size); b.position(b.position()+size);
    return part;
  }

  private static void skip(ByteBuffer b, int tag) throws IOException {
    switch (tag & 7) {
      case 0: varint(b); break;
      case 2: block(b); break;
      case 5: b.position(b.position()+4); break;
      case 1: b.position(b.position()+8); break;
      default: throw new IOException("Unknown tokenizer wire type");
    }
  }

  int[] encode(String input) {
    String text = "▁" + Normalizer.normalize(input, Normalizer.Form.NFKC)
        .trim().replaceAll("[\\s\\p{Z}]+", "▁");
    int length = text.length();
    float[] best = new float[length+1];
    Arrays.fill(best,Float.NEGATIVE_INFINITY);
    best[0] = 0;
    int[] prev = new int[length+1], token = new int[length+1];
    for (int i=0; i<length; i+=Character.charCount(text.codePointAt(i))) {
      int one = i + Character.charCount(text.codePointAt(i));
      boolean single = false;
      for (int end=one; end<=Math.min(length,i+maxLength); end++) {
        Piece p = dictionary.get(text.substring(i,end));
        if (p == null) continue;
        single |= end == one;
        if (best[i]+p.score > best[end]) {
          best[end] = best[i]+p.score; prev[end]=i; token[end]=p.id;
        }
      }
      if (!single && best[i]+unknownScore-10 > best[one]) {
        best[one]=best[i]+unknownScore-10; prev[one]=i; token[one]=-1;
      }
    }
    List<Integer> reversed = new ArrayList<>();
    for (int end=length; end>0; end=prev[end]) {
      if (token[end]>=0) reversed.add(token[end]);
      else {
        byte[] raw=text.substring(prev[end],end).getBytes(StandardCharsets.UTF_8);
        for(int i=raw.length-1;i>=0;i--) reversed.add(bytes[raw[i]&255]);
      }
    }
    Collections.reverse(reversed);
    return reversed.stream().mapToInt(Integer::intValue).toArray();
  }
}
