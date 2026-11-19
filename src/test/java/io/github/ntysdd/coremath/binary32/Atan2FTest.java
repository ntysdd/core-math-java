package io.github.ntysdd.coremath.binary32;

import static io.github.ntysdd.coremath.test.Vectors.assertBits;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ntysdd.coremath.CrMath;
import io.github.ntysdd.coremath.test.Vectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {@link CrMath#atan2(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/atan2.txt}, which the C entry point
 * {@code cr_atan2f} produced, and against a sample of the upstream worst
 * cases {@code atan2.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class Atan2FTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/atan2.txt");

  private static String hex(long y, long x) {
    return "atan2f(0x" + Vectors.hex(y, 32) + ", 0x"
        + Vectors.hex(x, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> worstCases() {
    return Vectors.stream(V.worstCases);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x00000000L, CrMath.atan2(0.0f, 0.0f), "atan2f(0.0f, 0.0f)");
    assertBits(0x80000000L, CrMath.atan2(-0.0f, 0.0f), "atan2f(-0.0f, 0.0f)");
    assertBits(0x40490fdbL, CrMath.atan2(0.0f, -0.0f), "atan2f(0.0f, -0.0f)");
    assertBits(0x3f490fdbL, CrMath.atan2(1.0f, 1.0f), "atan2f(1.0f, 1.0f)");
    assertBits(0xbf490fdbL, CrMath.atan2(-1.0f, 1.0f), "atan2f(-1.0f, 1.0f)");
    assertBits(0x4016cbe4L, CrMath.atan2(1.0f, -1.0f), "atan2f(1.0f, -1.0f)");
    assertBits(0x3fc90fdbL, CrMath.atan2(1.0f, 0.0f), "atan2f(1.0f, 0.0f)");
    assertBits(0xbfc90fdbL, CrMath.atan2(-1.0f, 0.0f), "atan2f(-1.0f, 0.0f)");
    assertBits(0x3f490fdbL,
        CrMath.atan2(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        "atan2f(+Infinity, +Infinity)");
    assertBits(0x00000000L,
        CrMath.atan2(1.0f, Float.POSITIVE_INFINITY),
        "atan2f(1.0f, +Infinity)");
    assertTrue(Float.isNaN(
        CrMath.atan2(Float.intBitsToFloat(0x7fc00000), 1.0f)),
        "atan2f(NaN, 1.0f)");
    assertTrue(Float.isNaN(
        CrMath.atan2(Float.intBitsToFloat(0x7fc00000), 1.0f)),
        "atan2f(0x7fc00000, 1.0): quiet NaN");
    assertTrue(Float.isNaN(
        CrMath.atan2(Float.intBitsToFloat(0x7f800001), 1.0f)),
        "atan2f(0x7f800001, 1.0): signalling NaN, smallest payload");
    assertTrue(Float.isNaN(
        CrMath.atan2(Float.intBitsToFloat(0xffc00001), 1.0f)),
        "atan2f(0xffc00001, 1.0): negative NaN with a payload");
    assertTrue(Float.isNaN(
        CrMath.atan2(Float.intBitsToFloat(0x7fffffff), 1.0f)),
        "atan2f(0x7fffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected,
        CrMath.atan2(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)),
        hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected,
        CrMath.atan2(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)),
        hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected,
        CrMath.atan2(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)),
        hex(y, x));
  }
}
