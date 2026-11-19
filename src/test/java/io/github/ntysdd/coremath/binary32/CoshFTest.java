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
 * Tests of {@link CrMath#cosh(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/cosh.txt}, which the C entry point
 * {@code cr_coshf} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class CoshFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/cosh.txt");

  private static String hex(long input) {
    return "coshf(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3f800000L, CrMath.cosh(0.0f), "coshf(+0.0)");
    assertBits(0x3f800000L, CrMath.cosh(-0.0f), "coshf(-0.0)");
    assertBits(0x7f800000L, CrMath.cosh(Float.POSITIVE_INFINITY), "coshf(+Infinity)");
    assertBits(0x7f800000L, CrMath.cosh(Float.NEGATIVE_INFINITY), "coshf(-Infinity)");
    assertBits(0x7f800000L, CrMath.cosh(Float.MAX_VALUE), "coshf(Float.MAX_VALUE)");
    assertTrue(Float.isNaN(CrMath.cosh(Float.intBitsToFloat(0x7fc00000))),
        "coshf(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(CrMath.cosh(Float.intBitsToFloat(0x7f800001))),
        "coshf(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.cosh(Float.intBitsToFloat(0xffc00001))),
        "coshf(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.cosh(Float.intBitsToFloat(0x7fffffff))),
        "coshf(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.cosh(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.cosh(Float.intBitsToFloat((int) input)), hex(input));
  }
}
