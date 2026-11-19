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
 * Tests of {@link CrMath#cos(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/cos.txt}, which the C entry point
 * {@code cr_cos} produced, and against a sample of the upstream worst cases
 * {@code cos.wc}.
 */
class CosTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/cos.txt");

  private static String hex(long bits) {
    return "cos(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0x3ff0000000000000L, CrMath.cos(0.0), "cos(+0.0)");
    assertBits(0x3ff0000000000000L, CrMath.cos(-0.0), "cos(-0.0)");
    assertTrue(Double.isNaN(CrMath.cos(Double.POSITIVE_INFINITY)), "cos(+Infinity)");
    assertTrue(Double.isNaN(CrMath.cos(Double.NEGATIVE_INFINITY)), "cos(-Infinity)");
    assertTrue(Double.isNaN(CrMath.cos(Double.longBitsToDouble(0x7ff8000000000000L))),
        "cos(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.cos(Double.longBitsToDouble(0x7ff0000000000001L))),
        "cos(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.cos(Double.longBitsToDouble(0xfff8000000000001L))),
        "cos(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.cos(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "cos(NaN with every payload bit set)");
    assertBits(0x3ff0000000000000L, CrMath.cos(Double.MIN_VALUE),
        "cos(smallest subnormal)");
    assertBits(0x3ff0000000000000L, CrMath.cos(-Double.MIN_VALUE),
        "cos(-smallest subnormal)");
    assertBits(0xbfefffe62ecfab75L, CrMath.cos(Double.MAX_VALUE),
        "cos(Double.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.cos(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.cos(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.cos(Double.longBitsToDouble(input)), hex(input));
  }
}
