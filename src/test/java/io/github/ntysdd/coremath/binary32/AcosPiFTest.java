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
 * Tests of {@link CrMath#acospi(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/acospi.txt}, which the C entry point
 * {@code cr_acospif} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AcosPiFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/acospi.txt");

  private static String hex(long input) {
    return "acospif(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3f000000L, CrMath.acospi(0.0f), "acospif(+0.0)");
    assertBits(0x3f000000L, CrMath.acospi(-0.0f), "acospif(-0.0)");
    assertBits(0x3eaaaaabL, CrMath.acospi(0.5f), "acospif(0.5)");
    assertBits(0x00000000L, CrMath.acospi(1.0f), "acospif(1.0)");
    assertBits(0x3f800000L, CrMath.acospi(-1.0f), "acospif(-1.0)");
    assertTrue(Float.isNaN(CrMath.acospi(2.0f)), "acospif(2.0)");
    assertTrue(Float.isNaN(CrMath.acospi(Float.POSITIVE_INFINITY)), "acospif(+Infinity)");
    assertTrue(Float.isNaN(CrMath.acospi(Float.NEGATIVE_INFINITY)), "acospif(-Infinity)");
    assertTrue(Float.isNaN(CrMath.acospi(Float.intBitsToFloat(0x7fc00000))),
        "acospif(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(CrMath.acospi(Float.intBitsToFloat(0x7f800001))),
        "acospif(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.acospi(Float.intBitsToFloat(0xffc00001))),
        "acospif(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.acospi(Float.intBitsToFloat(0x7fffffff))),
        "acospif(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.acospi(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.acospi(Float.intBitsToFloat((int) input)), hex(input));
  }
}
