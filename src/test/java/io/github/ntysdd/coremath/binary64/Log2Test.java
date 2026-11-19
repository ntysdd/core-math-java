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
 * Tests of {@link CrMath#log2(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/log2.txt}, which the C entry point
 * {@code cr_log2} produced, and against a sample of the upstream worst cases
 * {@code log2.wc}.
 */
class Log2Test {

  private static final Vectors V = Vectors.load("/vectors/binary64/log2.txt");

  private static String hex(long bits) {
    return "log2(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0xfff0000000000000L, CrMath.log2(0.0), "log2(+0.0)");
    assertBits(0xfff0000000000000L, CrMath.log2(-0.0), "log2(-0.0)");
    assertTrue(Double.isNaN(CrMath.log2(-1.0)), "log2(-1.0)");
    assertBits(0x0000000000000000L, CrMath.log2(1.0), "log2(1.0)");
    assertBits(0x7ff0000000000000L, CrMath.log2(Double.POSITIVE_INFINITY),
        "log2(+Infinity)");
    assertTrue(Double.isNaN(CrMath.log2(Double.NEGATIVE_INFINITY)), "log2(-Infinity)");
    assertTrue(Double.isNaN(CrMath.log2(Double.longBitsToDouble(0x7ff8000000000000L))),
        "log2(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.log2(Double.longBitsToDouble(0x7ff0000000000001L))),
        "log2(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.log2(Double.longBitsToDouble(0xfff8000000000001L))),
        "log2(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.log2(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "log2(NaN with every payload bit set)");
    assertBits(0xc090c80000000000L, CrMath.log2(Double.MIN_VALUE),
        "log2(smallest subnormal) is -1074");
    assertTrue(Double.isNaN(CrMath.log2(-Double.MIN_VALUE)),
        "log2(-smallest subnormal)");
    assertBits(0x4090000000000000L, CrMath.log2(Double.MAX_VALUE),
        "log2(Double.MAX_VALUE) is 1024");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log2(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.log2(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log2(Double.longBitsToDouble(input)), hex(input));
  }
}
