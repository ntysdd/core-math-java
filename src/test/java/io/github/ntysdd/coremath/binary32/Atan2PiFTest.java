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
 * Tests of {@link CrMath#atan2pi(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/atan2pi.txt}, which the C entry point
 * {@code cr_atan2pif} produced, and against a sample of the upstream worst
 * cases {@code atan2pi.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class Atan2PiFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/atan2pi.txt");

  private static String hex(long y, long x) {
    return "atan2pif(0x" + Vectors.hex(y, 32) + ", 0x"
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
    assertBits(0x00000000L, CrMath.atan2pi(0.0f, 0.0f), "atan2pif(0.0f, 0.0f)");
    assertBits(0x80000000L, CrMath.atan2pi(-0.0f, 0.0f), "atan2pif(-0.0f, 0.0f)");
    assertBits(0x3f800000L, CrMath.atan2pi(0.0f, -0.0f), "atan2pif(0.0f, -0.0f)");
    assertBits(0x3e800000L, CrMath.atan2pi(1.0f, 1.0f), "atan2pif(1.0f, 1.0f)");
    assertBits(0xbe800000L, CrMath.atan2pi(-1.0f, 1.0f), "atan2pif(-1.0f, 1.0f)");
    assertBits(0x3f000000L, CrMath.atan2pi(1.0f, 0.0f), "atan2pif(1.0f, 0.0f)");
    assertBits(0x3e800000L, CrMath.atan2pi(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY), "atan2pif(+Infinity, +Infinity)");
    assertTrue(Float.isNaN(CrMath.atan2pi(Float.intBitsToFloat(0x7fc00000), 1.0f)), "atan2pif(NaN, 1.0f)");
    assertTrue(Float.isNaN(CrMath.atan2pi(Float.intBitsToFloat(0x7fc00000), 1.0f)),
        "atan2pif(0x7fc00000, 1.0): quiet NaN");
    assertTrue(Float.isNaN(CrMath.atan2pi(Float.intBitsToFloat(0x7f800001), 1.0f)),
        "atan2pif(0x7f800001, 1.0): signalling NaN, smallest payload");
    assertTrue(Float.isNaN(CrMath.atan2pi(Float.intBitsToFloat(0xffc00001), 1.0f)),
        "atan2pif(0xffc00001, 1.0): negative NaN with a payload");
    assertTrue(Float.isNaN(CrMath.atan2pi(Float.intBitsToFloat(0x7fffffff), 1.0f)),
        "atan2pif(0x7fffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected, CrMath.atan2pi(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected, CrMath.atan2pi(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected, CrMath.atan2pi(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }
}
