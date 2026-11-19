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
 * Tests of {@link CrMath#asin(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/asin.txt}, which the C entry point
 * {@code cr_asinf} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AsinFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/asin.txt");

  private static String hex(long input) {
    return "asinf(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x00000000L, CrMath.asin(0.0f), "asinf(+0.0)");
    assertBits(0x80000000L, CrMath.asin(-0.0f), "asinf(-0.0)");
    assertBits(0x3fc90fdbL, CrMath.asin(1.0f), "asinf(1.0)");
    assertBits(0xbfc90fdbL, CrMath.asin(-1.0f), "asinf(-1.0)");
    assertTrue(Float.isNaN(CrMath.asin(2.0f)), "asinf(2.0)");
    assertTrue(Float.isNaN(CrMath.asin(-2.0f)), "asinf(-2.0)");
    assertTrue(Float.isNaN(CrMath.asin(Float.POSITIVE_INFINITY)), "asinf(+Infinity)");
    assertTrue(Float.isNaN(CrMath.asin(Float.NEGATIVE_INFINITY)), "asinf(-Infinity)");
    assertTrue(Float.isNaN(CrMath.asin(Float.intBitsToFloat(0x7fc00000))),
        "asinf(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(CrMath.asin(Float.intBitsToFloat(0x7f800001))),
        "asinf(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.asin(Float.intBitsToFloat(0xffc00001))),
        "asinf(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.asin(Float.intBitsToFloat(0x7fffffff))),
        "asinf(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.asin(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.asin(Float.intBitsToFloat((int) input)), hex(input));
  }
}
