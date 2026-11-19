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
 * Tests of {@link CrMath#cbrt(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/cbrt.txt}, which the C entry point
 * {@code cr_cbrtf} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class CbrtFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/cbrt.txt");

  private static String hex(long input) {
    return "cbrtf(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x00000000L, CrMath.cbrt(0.0f), "cbrtf(+0.0)");
    assertBits(0x80000000L, CrMath.cbrt(-0.0f), "cbrtf(-0.0)");
    assertBits(0x40000000L, CrMath.cbrt(8.0f), "cbrtf(8.0)");
    assertBits(0xc0000000L, CrMath.cbrt(-8.0f), "cbrtf(-8.0)");
    assertBits(0x7f800000L, CrMath.cbrt(Float.POSITIVE_INFINITY), "cbrtf(+Infinity)");
    assertBits(0xff800000L, CrMath.cbrt(Float.NEGATIVE_INFINITY), "cbrtf(-Infinity)");
    assertTrue(Float.isNaN(
        CrMath.cbrt(Float.intBitsToFloat(0x7fc00000))),
        "cbrtf(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(
        CrMath.cbrt(Float.intBitsToFloat(0x7f800001))),
        "cbrtf(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(
        CrMath.cbrt(Float.intBitsToFloat(0xffc00001))),
        "cbrtf(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(
        CrMath.cbrt(Float.intBitsToFloat(0x7fffffff))),
        "cbrtf(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.cbrt(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.cbrt(Float.intBitsToFloat((int) input)), hex(input));
  }
}
