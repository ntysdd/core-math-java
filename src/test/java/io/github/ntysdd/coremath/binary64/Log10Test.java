package io.github.ntysdd.coremath.binary64;

import static io.github.ntysdd.coremath.test.Vectors.assertBits;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ntysdd.coremath.CrMath;
import io.github.ntysdd.coremath.test.Vectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {@link CrMath#log10(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/log10.txt}, which the C entry
 * point {@code cr_log10} produced, and against a sample of the upstream worst
 * cases {@code log10.wc}.
 */
class Log10Test {

  private static final Vectors V = Vectors.load("/vectors/binary64/log10.txt");

  private static String hex(long bits) {
    return "log10(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0xfff0000000000000L, CrMath.log10(0.0), "log10(+0.0)");
    assertBits(0xfff0000000000000L, CrMath.log10(-0.0), "log10(-0.0)");
    assertTrue(Double.isNaN(CrMath.log10(-1.0)), "log10(-1.0)");
    assertBits(0x0000000000000000L, CrMath.log10(1.0), "log10(1.0)");
    assertBits(0x7ff0000000000000L, CrMath.log10(Double.POSITIVE_INFINITY),
        "log10(+Infinity)");
    assertTrue(Double.isNaN(CrMath.log10(Double.NEGATIVE_INFINITY)),
        "log10(-Infinity)");
    assertTrue(Double.isNaN(CrMath.log10(Double.longBitsToDouble(0x7ff8000000000000L))),
        "log10(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.log10(Double.longBitsToDouble(0x7ff0000000000001L))),
        "log10(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.log10(Double.longBitsToDouble(0xfff8000000000001L))),
        "log10(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.log10(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "log10(NaN with every payload bit set)");
    assertBits(0xc07434e6420f4374L, CrMath.log10(Double.MIN_VALUE),
        "log10(smallest subnormal)");
    assertTrue(Double.isNaN(CrMath.log10(-Double.MIN_VALUE)),
        "log10(-smallest subnormal)");
    assertBits(0x40734413509f79ffL, CrMath.log10(Double.MAX_VALUE),
        "log10(Double.MAX_VALUE)");
  }

  @Test
  void exactPowersOfTen() {
    double p = 1.0;
    for (int n = 0; n <= 22; n++) {
      assertEquals(n, CrMath.log10(p), "log10(10^" + n + ")");
      p *= 10.0;
    }
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log10(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.log10(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log10(Double.longBitsToDouble(input)), hex(input));
  }
}
