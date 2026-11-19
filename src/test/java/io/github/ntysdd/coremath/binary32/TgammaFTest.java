package io.github.ntysdd.coremath.binary32;

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
 * Tests of {@link CrMath#tgamma(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/tgamma.txt}, which the C entry point
 * {@code cr_tgammaf} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class TgammaFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/tgamma.txt");

  private static String hex(long input) {
    return "tgammaf(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x7f800000L, CrMath.tgamma(0.0f), "tgammaf(+0.0)");
    assertBits(0xff800000L, CrMath.tgamma(-0.0f), "tgammaf(-0.0)");
    assertBits(0x3f800000L, CrMath.tgamma(1.0f), "tgammaf(1.0)");
    assertBits(0x3f800000L, CrMath.tgamma(2.0f), "tgammaf(2.0)");
    assertBits(0x41c00000L, CrMath.tgamma(5.0f), "tgammaf(5.0)");
    assertTrue(Float.isNaN(CrMath.tgamma(-1.0f)), "tgammaf(-1.0)");
    assertBits(0x7f800000L, CrMath.tgamma(Float.POSITIVE_INFINITY), "tgammaf(+Infinity)");
    assertTrue(Float.isNaN(CrMath.tgamma(Float.NEGATIVE_INFINITY)), "tgammaf(-Infinity)");
    assertBits(0x7f800000L, CrMath.tgamma(Float.MAX_VALUE), "tgammaf(Float.MAX_VALUE)");
    assertTrue(Float.isNaN(CrMath.tgamma(-Float.MAX_VALUE)), "tgammaf(-Float.MAX_VALUE)");
    assertTrue(Float.isNaN(CrMath.tgamma(Float.intBitsToFloat(0x7fc00000))),
        "tgammaf(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(CrMath.tgamma(Float.intBitsToFloat(0x7f800001))),
        "tgammaf(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.tgamma(Float.intBitsToFloat(0xffc00001))),
        "tgammaf(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.tgamma(Float.intBitsToFloat(0x7fffffff))),
        "tgammaf(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.tgamma(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.tgamma(Float.intBitsToFloat((int) input)), hex(input));
  }
}
