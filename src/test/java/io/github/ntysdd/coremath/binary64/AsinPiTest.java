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
 * Tests of {@link CrMath#asinpi(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/asinpi.txt}, which the C entry point
 * {@code cr_asinpi} produced, and against a sample of the upstream worst
 * cases {@code asinpi.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AsinPiTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/asinpi.txt");

  private static String hex(long input) {
    return "asinpi(0x" + Vectors.hex(input, 64) + ")";
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
    assertBits(0x0000000000000000L, CrMath.asinpi(0.0), "asinpi(+0.0)");
    assertBits(0x8000000000000000L, CrMath.asinpi(-0.0), "asinpi(-0.0)");
    assertBits(0x3fc5555555555555L, CrMath.asinpi(0.5), "asinpi(0.5)");
    assertBits(0x3fe0000000000000L, CrMath.asinpi(1.0), "asinpi(1.0)");
    assertBits(0xbfe0000000000000L, CrMath.asinpi(-1.0), "asinpi(-1.0)");
    assertTrue(Double.isNaN(CrMath.asinpi(2.0)), "asinpi(2.0)");
    assertTrue(Double.isNaN(CrMath.asinpi(Double.POSITIVE_INFINITY)), "asinpi(+Infinity)");
    assertTrue(Double.isNaN(CrMath.asinpi(Double.NEGATIVE_INFINITY)), "asinpi(-Infinity)");
    assertTrue(Double.isNaN(CrMath.asinpi(Double.longBitsToDouble(0x7ff8000000000000L))),
        "asinpi(0x7ff8000000000000, quiet NaN)");
    assertTrue(Double.isNaN(CrMath.asinpi(Double.longBitsToDouble(0x7ff0000000000001L))),
        "asinpi(0x7ff0000000000001, signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.asinpi(Double.longBitsToDouble(0xfff8000000000001L))),
        "asinpi(0xfff8000000000001, negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.asinpi(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "asinpi(0x7fffffffffffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.asinpi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.asinpi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.asinpi(Double.longBitsToDouble(input)), hex(input));
  }
}
