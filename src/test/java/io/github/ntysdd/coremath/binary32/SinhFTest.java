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
 * Tests of {@link CrMath#sinh(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/sinh.txt}, which the C entry point
 * {@code cr_sinhf} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class SinhFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/sinh.txt");

  private static String hex(long input) {
    return "sinhf(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x00000000L, CrMath.sinh(0.0f), "sinhf(+0.0)");
    assertBits(0x80000000L, CrMath.sinh(-0.0f), "sinhf(-0.0)");
    assertBits(0x7f800000L, CrMath.sinh(Float.POSITIVE_INFINITY), "sinhf(+Infinity)");
    assertBits(0xff800000L, CrMath.sinh(Float.NEGATIVE_INFINITY), "sinhf(-Infinity)");
    assertBits(0x7f800000L, CrMath.sinh(Float.MAX_VALUE), "sinhf(Float.MAX_VALUE)");
    assertBits(0xff800000L, CrMath.sinh(-Float.MAX_VALUE), "sinhf(-Float.MAX_VALUE)");
    assertTrue(Float.isNaN(
        CrMath.sinh(Float.intBitsToFloat(0x7fc00000))),
        "sinhf(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(
        CrMath.sinh(Float.intBitsToFloat(0x7f800001))),
        "sinhf(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(
        CrMath.sinh(Float.intBitsToFloat(0xffc00001))),
        "sinhf(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(
        CrMath.sinh(Float.intBitsToFloat(0x7fffffff))),
        "sinhf(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.sinh(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.sinh(Float.intBitsToFloat((int) input)), hex(input));
  }
}
