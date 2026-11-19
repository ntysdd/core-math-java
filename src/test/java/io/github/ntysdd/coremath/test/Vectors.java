package io.github.ntysdd.coremath.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.provider.Arguments;

/**
 * A vector file of {@code src/test/resources/vectors}, and the deterministic
 * pseudo-random sweep of AGENTS.md section 5.5.
 *
 * <p>A file holds three sections, introduced by {@code #---} markers: the
 * directed cases (the special values, both sides of every comparison threshold
 * of the translation and the exceptional entries the entry point can reach),
 * a sample of the upstream worst cases where there is one, and the sweep. Each
 * data line is one case, its input arguments followed by its expected results,
 * all as hexadecimal bit patterns.
 *
 * <p>The sweep is a 64-bit xorshift seeded with the value in its marker, the
 * same generator and the same seed that {@code tools/mkvectors.py} used. The
 * loader recomputes the inputs and fails when a row does not match them, so a
 * hand-edited or truncated sweep cannot pass unnoticed.
 */
public final class Vectors {

  /** The directed cases. */
  public final long[][] directed;

  /** The sample of the upstream worst cases, empty where there is none. */
  public final long[][] worstCases;

  /** The deterministic pseudo-random sweep. */
  public final long[][] random;

  private Vectors(long[][] directed, long[][] worstCases, long[][] random) {
    this.directed = directed;
    this.worstCases = worstCases;
    this.random = random;
  }

  /**
   * Reads a vector file from the classpath.
   *
   * @param resource a name such as {@code /vectors/binary32/sin.txt}
   * @return the three sections
   */
  public static Vectors load(String resource) {
    List<String> lines = read(resource);
    int width = widthOf(lines, resource);
    List<long[]> directed = new ArrayList<>();
    List<long[]> worst = new ArrayList<>();
    List<long[]> random = new ArrayList<>();
    List<long[]> section = null;
    long seed = 0;
    int draws = 0;
    for (String line : lines) {
      if (line.isEmpty()) {
        continue;
      }
      if (line.startsWith("#--- ")) {
        String[] marker = line.substring(5).trim().split(" +");
        if (marker[0].equals("directed")) {
          section = directed;
        } else if (marker[0].equals("worst")) {
          section = worst;
        } else if (marker[0].equals("random")) {
          section = random;
          for (int i = 1; i < marker.length; i++) {
            if (marker[i].startsWith("seed=0x")) {
              seed = Long.parseUnsignedLong(marker[i].substring(7), 16);
            } else if (marker[i].startsWith("draws=")) {
              draws = Integer.parseInt(marker[i].substring(6));
            }
          }
        } else {
          throw new IllegalStateException(resource + ": section " + marker[0]);
        }
        continue;
      }
      if (line.startsWith("#")) {
        continue;
      }
      if (section == null) {
        throw new IllegalStateException(resource + ": data before a section");
      }
      section.add(parse(line));
    }
    if (draws > 0) {
      checkSweep(resource, random, seed, draws, width);
    }
    return new Vectors(directed.toArray(new long[0][]),
        worst.toArray(new long[0][]), random.toArray(new long[0][]));
  }

  /** The rows of a section as parameterized test arguments. */
  public static Stream<Arguments> stream(long[][] rows) {
    return Arrays.stream(rows).map(row -> {
      Object[] args = new Object[row.length];
      for (int i = 0; i < row.length; i++) {
        args[i] = row[i];
      }
      return Arguments.of(args);
    });
  }

  /** Fails unless {@code actual} has the bits {@code expected}. */
  public static void assertBits(long expected, float actual, String where) {
    if (Float.isNaN(Float.intBitsToFloat((int) expected))) {
      Assertions.assertTrue(Float.isNaN(actual), where + ": expected a NaN");
    } else {
      Assertions.assertEquals(expected, Float.floatToRawIntBits(actual) & 0xffffffffL,
          where + ": expected 0x" + hex(expected, 32) + ", was 0x"
              + hex(Float.floatToRawIntBits(actual) & 0xffffffffL, 32));
    }
  }

  /** Fails unless {@code actual} has the bits {@code expected}. */
  public static void assertBits(long expected, double actual, String where) {
    if (Double.isNaN(Double.longBitsToDouble(expected))) {
      Assertions.assertTrue(Double.isNaN(actual), where + ": expected a NaN");
    } else {
      Assertions.assertEquals(expected, Double.doubleToRawLongBits(actual),
          where + ": expected 0x" + hex(expected, 64) + ", was 0x"
              + hex(Double.doubleToRawLongBits(actual), 64));
    }
  }

  /** The bits as the hexadecimal literal a vector file uses. */
  public static String hex(long bits, int width) {
    return String.format(width == 64 ? "%016x" : "%08x", bits);
  }

  private static void checkSweep(String resource, List<long[]> rows, long seed,
      int draws, int width) {
    XorShift rng = new XorShift(seed);
    for (int i = 0; i < rows.size(); i++) {
      long[] row = rows.get(i);
      for (int j = 0; j < draws; j++) {
        long expected = rng.next();
        if (width == 32) {
          expected &= 0xffffffffL;
        }
        if (row[j] != expected) {
          throw new IllegalStateException(resource + ": sweep case " + i
              + " has the input " + hex(row[j], width) + ", the generator gives "
              + hex(expected, width));
        }
      }
    }
  }

  private static int widthOf(List<String> lines, String resource) {
    for (String line : lines) {
      if (line.startsWith("# core-math-java test vectors:")) {
        return line.contains("binary32") ? 32 : 64;
      }
    }
    throw new IllegalStateException(resource + ": no binary32 or binary64 header");
  }

  private static long[] parse(String line) {
    String[] fields = line.trim().split(" +");
    long[] values = new long[fields.length];
    for (int i = 0; i < fields.length; i++) {
      values[i] = Long.parseUnsignedLong(fields[i], 16);
    }
    return values;
  }

  private static List<String> read(String resource) {
    try (InputStream in = Vectors.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("no vector file " + resource);
      }
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
      return lines;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final class XorShift {
    private long state;

    XorShift(long seed) {
      state = seed;
    }

    long next() {
      state ^= state << 13;
      state ^= state >>> 7;
      state ^= state << 17;
      return state;
    }
  }
}
