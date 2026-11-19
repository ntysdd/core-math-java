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
 * Tests of {@link CrMath#exp(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/exp.txt}, which the C entry point
 * {@code cr_exp} produced, and against a sample of the upstream worst cases
 * {@code exp.wc}.
 */
class ExpTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/exp.txt");

  private static String hex(long bits) {
    return "exp(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0x3ff0000000000000L, CrMath.exp(0.0), "exp(+0.0)");
    assertBits(0x3ff0000000000000L, CrMath.exp(-0.0), "exp(-0.0)");
    assertBits(0x7ff0000000000000L, CrMath.exp(Double.POSITIVE_INFINITY),
        "exp(+Infinity)");
    assertBits(0x0000000000000000L, CrMath.exp(Double.NEGATIVE_INFINITY),
        "exp(-Infinity)");
    assertTrue(Double.isNaN(CrMath.exp(Double.longBitsToDouble(0x7ff8000000000000L))),
        "exp(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.exp(Double.longBitsToDouble(0x7ff0000000000001L))),
        "exp(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.exp(Double.longBitsToDouble(0xfff8000000000001L))),
        "exp(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.exp(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "exp(NaN with every payload bit set)");
    assertBits(0x3ff0000000000000L, CrMath.exp(Double.MIN_VALUE),
        "exp(smallest subnormal)");
    assertBits(0x3ff0000000000000L, CrMath.exp(-Double.MIN_VALUE),
        "exp(-smallest subnormal)");
    assertBits(0x7ff0000000000000L, CrMath.exp(Double.MAX_VALUE),
        "exp(Double.MAX_VALUE)");
    assertBits(0x0000000000000000L, CrMath.exp(-Double.MAX_VALUE),
        "exp(-Double.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.exp(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.exp(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.exp(Double.longBitsToDouble(input)), hex(input));
  }
}
