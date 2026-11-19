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
 * Tests of {@link CrMath#acospi(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/acospi.txt}, which the C entry point
 * {@code cr_acospi} produced, and against a sample of the upstream worst
 * cases {@code acospi.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AcosPiTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/acospi.txt");

  private static String hex(long input) {
    return "acospi(0x" + Vectors.hex(input, 64) + ")";
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
    assertBits(0x3fe0000000000000L, CrMath.acospi(0.0), "acospi(+0.0)");
    assertBits(0x3fe0000000000000L, CrMath.acospi(-0.0), "acospi(-0.0)");
    assertBits(0x3fd5555555555555L, CrMath.acospi(0.5), "acospi(0.5)");
    assertBits(0x0000000000000000L, CrMath.acospi(1.0), "acospi(1.0)");
    assertBits(0x3ff0000000000000L, CrMath.acospi(-1.0), "acospi(-1.0)");
    assertTrue(Double.isNaN(CrMath.acospi(2.0)), "acospi(2.0)");
    assertTrue(Double.isNaN(CrMath.acospi(Double.POSITIVE_INFINITY)), "acospi(+Infinity)");
    assertTrue(Double.isNaN(CrMath.acospi(Double.NEGATIVE_INFINITY)), "acospi(-Infinity)");
    assertTrue(Double.isNaN(
        CrMath.acospi(Double.longBitsToDouble(0x7ff8000000000000L))),
        "acospi(0x7ff8000000000000, quiet NaN)");
    assertTrue(Double.isNaN(
        CrMath.acospi(Double.longBitsToDouble(0x7ff0000000000001L))),
        "acospi(0x7ff0000000000001, signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(
        CrMath.acospi(Double.longBitsToDouble(0xfff8000000000001L))),
        "acospi(0xfff8000000000001, negative NaN with a payload)");
    assertTrue(Double.isNaN(
        CrMath.acospi(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "acospi(0x7fffffffffffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.acospi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.acospi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.acospi(Double.longBitsToDouble(input)), hex(input));
  }
}
