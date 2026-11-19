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
 * Tests of {@link CrMath#hypot(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/hypot.txt}, which the C entry point
 * {@code cr_hypot} produced, and against a sample of the upstream worst
 * cases {@code hypot.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class HypotTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/hypot.txt");

  private static String hex(long y, long x) {
    return "hypot(0x" + Vectors.hex(y, 64) + ", 0x"
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
    assertBits(0x0000000000000000L, CrMath.hypot(0.0, 0.0), "hypot(0.0, 0.0)");
    assertBits(0x0000000000000000L, CrMath.hypot(-0.0, -0.0), "hypot(-0.0, -0.0)");
    assertBits(0x4014000000000000L, CrMath.hypot(3.0, 4.0), "hypot(3.0, 4.0)");
    assertBits(0x3ff6a09e667f3bcdL, CrMath.hypot(1.0, 1.0), "hypot(1.0, 1.0)");
    assertBits(0x7ff0000000000000L,
        CrMath.hypot(Double.POSITIVE_INFINITY, Double.longBitsToDouble(0x7ff8000000000000L)),
        "hypot(+Infinity, NaN)");
    assertBits(0x7ff0000000000000L,
        CrMath.hypot(Double.longBitsToDouble(0x7ff8000000000000L), Double.POSITIVE_INFINITY),
        "hypot(NaN, +Infinity)");
    assertTrue(Double.isNaN(
        CrMath.hypot(1.0, Double.longBitsToDouble(0x7ff8000000000000L))),
        "hypot(1.0, NaN)");
    assertBits(0x7ff0000000000000L,
        CrMath.hypot(Double.MAX_VALUE, Double.MAX_VALUE),
        "hypot(Double.MAX_VALUE, Double.MAX_VALUE)");
    assertTrue(Double.isNaN(
        CrMath.hypot(Double.longBitsToDouble(0x7ff8000000000000L), 1.0)),
        "hypot(0x7ff8000000000000, 1.0): quiet NaN");
    assertTrue(Double.isNaN(
        CrMath.hypot(Double.longBitsToDouble(0x7ff0000000000001L), 1.0)),
        "hypot(0x7ff0000000000001, 1.0): signalling NaN, smallest payload");
    assertTrue(Double.isNaN(
        CrMath.hypot(Double.longBitsToDouble(0xfff8000000000001L), 1.0)),
        "hypot(0xfff8000000000001, 1.0): negative NaN with a payload");
    assertTrue(Double.isNaN(
        CrMath.hypot(Double.longBitsToDouble(0x7fffffffffffffffL), 1.0)),
        "hypot(0x7fffffffffffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected,
        CrMath.hypot(Double.longBitsToDouble(y), Double.longBitsToDouble(x)),
        hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected,
        CrMath.hypot(Double.longBitsToDouble(y), Double.longBitsToDouble(x)),
        hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected,
        CrMath.hypot(Double.longBitsToDouble(y), Double.longBitsToDouble(x)),
        hex(y, x));
  }
}
