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
 * Tests of {@link CrMath#lgamma(double, int[])} against the vectors of
 * {@code src/test/resources/vectors/binary64/lgamma.txt}, which the C entry
 * point {@code cr_lgamma} produced, and against a sample of the upstream worst
 * cases {@code lgamma.wc}.
 *
 * <p>The third column is the {@code signgamp} value as a 32-bit pattern, so
 * {@code 00000001} is 1 and {@code ffffffff} is -1.
 */
class LgammaTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/lgamma.txt");

  private static String hex(long bits) {
    return "lgamma(0x" + Vectors.hex(bits, 64) + ")";
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
    int[] sign = new int[1];
    assertBits(0x7ff0000000000000L, CrMath.lgamma(0.0, sign), "lgamma(+0.0)");
    assertEquals(1, sign[0], "lgamma(+0.0): signgamp");
    assertBits(0x7ff0000000000000L, CrMath.lgamma(-0.0, sign), "lgamma(-0.0)");
    assertEquals(-1, sign[0], "lgamma(-0.0): signgamp");
    assertBits(0x0000000000000000L, CrMath.lgamma(1.0, sign), "lgamma(1.0)");
    assertEquals(1, sign[0], "lgamma(1.0): signgamp");
    assertBits(0x0000000000000000L, CrMath.lgamma(2.0, sign), "lgamma(2.0)");
    assertEquals(1, sign[0], "lgamma(2.0): signgamp");
    assertBits(0x7ff0000000000000L, CrMath.lgamma(-1.0, sign), "lgamma(-1.0)");
    assertEquals(-1, sign[0], "lgamma(-1.0): signgamp");
    assertBits(0x7ff0000000000000L, CrMath.lgamma(Double.POSITIVE_INFINITY, sign),
        "lgamma(+Infinity)");
    assertEquals(1, sign[0], "lgamma(+Infinity): signgamp");
    assertBits(0x7ff0000000000000L, CrMath.lgamma(Double.NEGATIVE_INFINITY, sign),
        "lgamma(-Infinity)");
    assertEquals(1, sign[0], "lgamma(-Infinity): signgamp");
    assertTrue(Double.isNaN(CrMath.lgamma(Double.longBitsToDouble(0x7ff8000000000000L),
        sign)), "lgamma(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.lgamma(Double.longBitsToDouble(0x7ff0000000000001L),
        sign)), "lgamma(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.lgamma(Double.longBitsToDouble(0xfff8000000000001L),
        sign)), "lgamma(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.lgamma(Double.longBitsToDouble(0x7fffffffffffffffL),
        sign)), "lgamma(NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected, long sign) {
    int[] signgamp = new int[1];
    assertBits(expected, CrMath.lgamma(Double.longBitsToDouble(input), signgamp),
        hex(input));
    assertEquals((int) sign, signgamp[0], hex(input) + ": signgamp");
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected, long sign) {
    int[] signgamp = new int[1];
    assertBits(expected, CrMath.lgamma(Double.longBitsToDouble(input), signgamp),
        hex(input));
    assertEquals((int) sign, signgamp[0], hex(input) + ": signgamp");
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected, long sign) {
    int[] signgamp = new int[1];
    assertBits(expected, CrMath.lgamma(Double.longBitsToDouble(input), signgamp),
        hex(input));
    assertEquals((int) sign, signgamp[0], hex(input) + ": signgamp");
  }
}
