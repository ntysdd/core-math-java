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
 * Tests of {@link CrMath#log10p1(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/log10p1.txt}, which the C entry point
 * {@code cr_log10p1f} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class Log10P1FTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/log10p1.txt");

  private static String hex(long input) {
    return "log10p1f(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x00000000L, CrMath.log10p1(0.0f), "log10p1f(+0.0)");
    assertBits(0x80000000L, CrMath.log10p1(-0.0f), "log10p1f(-0.0)");
    assertBits(0x3e9a209bL, CrMath.log10p1(1.0f), "log10p1f(1.0)");
    assertBits(0xff800000L, CrMath.log10p1(-1.0f), "log10p1f(-1.0)");
    assertTrue(Float.isNaN(CrMath.log10p1(-2.0f)), "log10p1f(-2.0)");
    assertBits(0x7f800000L, CrMath.log10p1(Float.POSITIVE_INFINITY), "log10p1f(+Infinity)");
    assertTrue(Float.isNaN(CrMath.log10p1(Float.NEGATIVE_INFINITY)), "log10p1f(-Infinity)");
    assertTrue(Float.isNaN(
        CrMath.log10p1(Float.intBitsToFloat(0x7fc00000))),
        "log10p1f(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(
        CrMath.log10p1(Float.intBitsToFloat(0x7f800001))),
        "log10p1f(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(
        CrMath.log10p1(Float.intBitsToFloat(0xffc00001))),
        "log10p1f(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(
        CrMath.log10p1(Float.intBitsToFloat(0x7fffffff))),
        "log10p1f(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log10p1(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log10p1(Float.intBitsToFloat((int) input)), hex(input));
  }
}
