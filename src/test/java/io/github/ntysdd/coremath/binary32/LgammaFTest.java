package io.github.ntysdd.coremath.binary32;

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
 * Tests of {@link CrMath#lgamma(float, int[])} against the vectors of
 * {@code src/test/resources/vectors/binary32/lgamma.txt}, which the C entry
 * point {@code cr_lgammaf} produced.
 *
 * <p>The third column is the {@code signgamp} value as a 32-bit pattern, so
 * {@code 00000001} is 1 and {@code ffffffff} is -1.
 */
class LgammaFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/lgamma.txt");

  private static String hex(long bits) {
    return "lgammaf(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    int[] sign = new int[1];
    assertBits(0x7f800000L, CrMath.lgamma(0.0f, sign), "lgammaf(+0.0)");
    assertEquals(1, sign[0], "lgammaf(+0.0): signgamp");
    assertBits(0x7f800000L, CrMath.lgamma(-0.0f, sign), "lgammaf(-0.0)");
    assertEquals(-1, sign[0], "lgammaf(-0.0): signgamp");
    assertBits(0x00000000L, CrMath.lgamma(1.0f, sign), "lgammaf(1.0)");
    assertEquals(1, sign[0], "lgammaf(1.0): signgamp");
    assertBits(0x00000000L, CrMath.lgamma(2.0f, sign), "lgammaf(2.0)");
    assertEquals(1, sign[0], "lgammaf(2.0): signgamp");
    assertBits(0x7f800000L, CrMath.lgamma(-1.0f, sign), "lgammaf(-1.0)");
    assertEquals(1, sign[0], "lgammaf(-1.0): signgamp");
    assertBits(0x7f800000L, CrMath.lgamma(Float.POSITIVE_INFINITY, sign),
        "lgammaf(+Infinity)");
    assertEquals(1, sign[0], "lgammaf(+Infinity): signgamp");
    assertBits(0x7f800000L, CrMath.lgamma(Float.NEGATIVE_INFINITY, sign),
        "lgammaf(-Infinity)");
    assertEquals(1, sign[0], "lgammaf(-Infinity): signgamp");
    assertTrue(Float.isNaN(CrMath.lgamma(Float.intBitsToFloat(0x7fc00000), sign)),
        "lgammaf(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.lgamma(Float.intBitsToFloat(0x7f800001), sign)),
        "lgammaf(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.lgamma(Float.intBitsToFloat(0xffc00001), sign)),
        "lgammaf(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.lgamma(Float.intBitsToFloat(0x7fffffff), sign)),
        "lgammaf(NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected, long sign) {
    int[] signgamp = new int[1];
    assertBits(expected, CrMath.lgamma(Float.intBitsToFloat((int) input), signgamp),
        hex(input));
    assertEquals((int) sign, signgamp[0], hex(input) + ": signgamp");
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected, long sign) {
    int[] signgamp = new int[1];
    assertBits(expected, CrMath.lgamma(Float.intBitsToFloat((int) input), signgamp),
        hex(input));
    assertEquals((int) sign, signgamp[0], hex(input) + ": signgamp");
  }
}
