package io.github.ntysdd.coremath.binary64;

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
 * Tests of {@link CrMath#atan2(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/atan2.txt}, which the C entry point
 * {@code cr_atan2} produced, and against a sample of the upstream worst
 * cases {@code atan2.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class Atan2Test {

  private static final Vectors V = Vectors.load("/vectors/binary64/atan2.txt");

  private static String hex(long y, long x) {
    return "atan2(0x" + Vectors.hex(y, 64) + ", 0x"
        + Vectors.hex(x, 64) + ")";
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
    assertBits(0x0000000000000000L, CrMath.atan2(0.0, 0.0), "atan2(0.0, 0.0)");
    assertBits(0x8000000000000000L, CrMath.atan2(-0.0, 0.0), "atan2(-0.0, 0.0)");
    assertBits(0x400921fb54442d18L, CrMath.atan2(0.0, -0.0), "atan2(0.0, -0.0)");
    assertBits(0x3fe921fb54442d18L, CrMath.atan2(1.0, 1.0), "atan2(1.0, 1.0)");
    assertBits(0xbfe921fb54442d18L, CrMath.atan2(-1.0, 1.0), "atan2(-1.0, 1.0)");
    assertBits(0x4002d97c7f3321d2L, CrMath.atan2(1.0, -1.0), "atan2(1.0, -1.0)");
    assertBits(0x3ff921fb54442d18L, CrMath.atan2(1.0, 0.0), "atan2(1.0, 0.0)");
    assertBits(0xbff921fb54442d18L, CrMath.atan2(-1.0, 0.0), "atan2(-1.0, 0.0)");
    assertBits(0x3fe921fb54442d18L,
        CrMath.atan2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
        "atan2(+Infinity, +Infinity)");
    assertBits(0x0000000000000000L,
        CrMath.atan2(1.0, Double.POSITIVE_INFINITY),
        "atan2(1.0, +Infinity)");
    assertTrue(Double.isNaN(
        CrMath.atan2(Double.longBitsToDouble(0x7ff8000000000000L), 1.0)),
        "atan2(NaN, 1.0)");
    assertTrue(Double.isNaN(
        CrMath.atan2(Double.longBitsToDouble(0x7ff8000000000000L), 1.0)),
        "atan2(0x7ff8000000000000, 1.0): quiet NaN");
    assertTrue(Double.isNaN(
        CrMath.atan2(Double.longBitsToDouble(0x7ff0000000000001L), 1.0)),
        "atan2(0x7ff0000000000001, 1.0): signalling NaN, smallest payload");
    assertTrue(Double.isNaN(
        CrMath.atan2(Double.longBitsToDouble(0xfff8000000000001L), 1.0)),
        "atan2(0xfff8000000000001, 1.0): negative NaN with a payload");
    assertTrue(Double.isNaN(
        CrMath.atan2(Double.longBitsToDouble(0x7fffffffffffffffL), 1.0)),
        "atan2(0x7fffffffffffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected,
        CrMath.atan2(Double.longBitsToDouble(y), Double.longBitsToDouble(x)),
        hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected,
        CrMath.atan2(Double.longBitsToDouble(y), Double.longBitsToDouble(x)),
        hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected,
        CrMath.atan2(Double.longBitsToDouble(y), Double.longBitsToDouble(x)),
        hex(y, x));
  }
}
