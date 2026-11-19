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
 * Tests of {@link CrMath#tgamma(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/tgamma.txt}, which the C entry point
 * {@code cr_tgamma} produced, and against a sample of the upstream worst
 * cases {@code tgamma.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class TgammaTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/tgamma.txt");

  private static String hex(long input) {
    return "tgamma(0x" + Vectors.hex(input, 64) + ")";
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
    assertBits(0x7ff0000000000000L, CrMath.tgamma(0.0), "tgamma(+0.0)");
    assertBits(0xfff0000000000000L, CrMath.tgamma(-0.0), "tgamma(-0.0)");
    assertBits(0x3ff0000000000000L, CrMath.tgamma(1.0), "tgamma(1.0)");
    assertBits(0x3ff0000000000000L, CrMath.tgamma(2.0), "tgamma(2.0)");
    assertBits(0x4038000000000000L, CrMath.tgamma(5.0), "tgamma(5.0)");
    assertTrue(Double.isNaN(CrMath.tgamma(-1.0)), "tgamma(-1.0)");
    assertBits(0x7ff0000000000000L,
        CrMath.tgamma(Double.POSITIVE_INFINITY),
        "tgamma(+Infinity)");
    assertTrue(Double.isNaN(CrMath.tgamma(Double.NEGATIVE_INFINITY)), "tgamma(-Infinity)");
    assertBits(0x7ff0000000000000L,
        CrMath.tgamma(Double.MAX_VALUE),
        "tgamma(Double.MAX_VALUE)");
    assertTrue(Double.isNaN(CrMath.tgamma(-Double.MAX_VALUE)), "tgamma(-Double.MAX_VALUE)");
    assertTrue(Double.isNaN(
        CrMath.tgamma(Double.longBitsToDouble(0x7ff8000000000000L))),
        "tgamma(0x7ff8000000000000, quiet NaN)");
    assertTrue(Double.isNaN(
        CrMath.tgamma(Double.longBitsToDouble(0x7ff0000000000001L))),
        "tgamma(0x7ff0000000000001, signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(
        CrMath.tgamma(Double.longBitsToDouble(0xfff8000000000001L))),
        "tgamma(0xfff8000000000001, negative NaN with a payload)");
    assertTrue(Double.isNaN(
        CrMath.tgamma(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "tgamma(0x7fffffffffffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.tgamma(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.tgamma(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.tgamma(Double.longBitsToDouble(input)), hex(input));
  }
}
