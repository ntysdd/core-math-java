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
 * Tests of {@link CrMath#tan(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/tan.txt}, which the C entry point
 * {@code cr_tan} produced, and against a sample of the upstream worst cases
 * {@code tan.wc}.
 */
class TanTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/tan.txt");

  private static String hex(long bits) {
    return "tan(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0x0000000000000000L, CrMath.tan(0.0), "tan(+0.0)");
    assertBits(0x8000000000000000L, CrMath.tan(-0.0), "tan(-0.0)");
    assertTrue(Double.isNaN(CrMath.tan(Double.POSITIVE_INFINITY)), "tan(+Infinity)");
    assertTrue(Double.isNaN(CrMath.tan(Double.NEGATIVE_INFINITY)), "tan(-Infinity)");
    assertTrue(Double.isNaN(CrMath.tan(Double.longBitsToDouble(0x7ff8000000000000L))),
        "tan(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.tan(Double.longBitsToDouble(0x7ff0000000000001L))),
        "tan(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.tan(Double.longBitsToDouble(0xfff8000000000001L))),
        "tan(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.tan(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "tan(NaN with every payload bit set)");
    assertBits(0x0000000000000001L, CrMath.tan(Double.MIN_VALUE),
        "tan(smallest subnormal)");
    assertBits(0x8000000000000001L, CrMath.tan(-Double.MIN_VALUE),
        "tan(-smallest subnormal)");
    assertBits(0xbf74530cfe729484L, CrMath.tan(Double.MAX_VALUE),
        "tan(Double.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.tan(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.tan(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.tan(Double.longBitsToDouble(input)), hex(input));
  }
}
