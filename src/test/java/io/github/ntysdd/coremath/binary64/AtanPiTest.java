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
 * Tests of {@link CrMath#atanpi(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/atanpi.txt}, which the C entry point
 * {@code cr_atanpi} produced, and against a sample of the upstream worst
 * cases {@code atanpi.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AtanPiTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/atanpi.txt");

  private static String hex(long input) {
    return "atanpi(0x" + Vectors.hex(input, 64) + ")";
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
    assertBits(0x0000000000000000L, CrMath.atanpi(0.0), "atanpi(+0.0)");
    assertBits(0x8000000000000000L, CrMath.atanpi(-0.0), "atanpi(-0.0)");
    assertBits(0x3fd0000000000000L, CrMath.atanpi(1.0), "atanpi(1.0)");
    assertBits(0xbfd0000000000000L, CrMath.atanpi(-1.0), "atanpi(-1.0)");
    assertBits(0x3fe0000000000000L,
        CrMath.atanpi(Double.POSITIVE_INFINITY),
        "atanpi(+Infinity)");
    assertBits(0xbfe0000000000000L,
        CrMath.atanpi(Double.NEGATIVE_INFINITY),
        "atanpi(-Infinity)");
    assertTrue(Double.isNaN(
        CrMath.atanpi(Double.longBitsToDouble(0x7ff8000000000000L))),
        "atanpi(0x7ff8000000000000, quiet NaN)");
    assertTrue(Double.isNaN(
        CrMath.atanpi(Double.longBitsToDouble(0x7ff0000000000001L))),
        "atanpi(0x7ff0000000000001, signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(
        CrMath.atanpi(Double.longBitsToDouble(0xfff8000000000001L))),
        "atanpi(0xfff8000000000001, negative NaN with a payload)");
    assertTrue(Double.isNaN(
        CrMath.atanpi(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "atanpi(0x7fffffffffffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.atanpi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.atanpi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.atanpi(Double.longBitsToDouble(input)), hex(input));
  }
}
