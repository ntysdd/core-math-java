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
 * Tests of {@link CrMath#hypot(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/hypot.txt}, which the C entry point
 * {@code cr_hypotf} produced, and against a sample of the upstream worst
 * cases {@code hypot.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class HypotFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/hypot.txt");

  private static String hex(long y, long x) {
    return "hypotf(0x" + Vectors.hex(y, 32) + ", 0x"
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
    assertBits(0x00000000L, CrMath.hypot(0.0f, 0.0f), "hypotf(0.0f, 0.0f)");
    assertBits(0x00000000L, CrMath.hypot(-0.0f, -0.0f), "hypotf(-0.0f, -0.0f)");
    assertBits(0x40a00000L, CrMath.hypot(3.0f, 4.0f), "hypotf(3.0f, 4.0f)");
    assertBits(0x3fb504f3L, CrMath.hypot(1.0f, 1.0f), "hypotf(1.0f, 1.0f)");
    assertBits(0x7f800000L, CrMath.hypot(Float.POSITIVE_INFINITY, Float.intBitsToFloat(0x7fc00000)), "hypotf(+Infinity, NaN)");
    assertBits(0x7f800000L, CrMath.hypot(Float.intBitsToFloat(0x7fc00000), Float.POSITIVE_INFINITY), "hypotf(NaN, +Infinity)");
    assertTrue(Float.isNaN(CrMath.hypot(1.0f, Float.intBitsToFloat(0x7fc00000))), "hypotf(1.0f, NaN)");
    assertBits(0x7f800000L, CrMath.hypot(Float.MAX_VALUE, Float.MAX_VALUE), "hypotf(Float.MAX_VALUE, Float.MAX_VALUE)");
    assertTrue(Float.isNaN(CrMath.hypot(Float.intBitsToFloat(0x7fc00000), 1.0f)),
        "hypotf(0x7fc00000, 1.0): quiet NaN");
    assertTrue(Float.isNaN(CrMath.hypot(Float.intBitsToFloat(0x7f800001), 1.0f)),
        "hypotf(0x7f800001, 1.0): signalling NaN, smallest payload");
    assertTrue(Float.isNaN(CrMath.hypot(Float.intBitsToFloat(0xffc00001), 1.0f)),
        "hypotf(0xffc00001, 1.0): negative NaN with a payload");
    assertTrue(Float.isNaN(CrMath.hypot(Float.intBitsToFloat(0x7fffffff), 1.0f)),
        "hypotf(0x7fffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected, CrMath.hypot(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected, CrMath.hypot(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected, CrMath.hypot(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }
}
