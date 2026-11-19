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
 * Tests of {@link CrMath#exp2(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/exp2.txt}, which the C entry point
 * {@code cr_exp2f} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class Exp2FTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/exp2.txt");

  private static String hex(long input) {
    return "exp2f(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3f800000L, CrMath.exp2(0.0f), "exp2f(+0.0)");
    assertBits(0x3f800000L, CrMath.exp2(-0.0f), "exp2f(-0.0)");
    assertBits(0x43800000L, CrMath.exp2(8.0f), "exp2f(8.0)");
    assertBits(0x3b800000L, CrMath.exp2(-8.0f), "exp2f(-8.0)");
    assertBits(0x7f800000L, CrMath.exp2(Float.POSITIVE_INFINITY), "exp2f(+Infinity)");
    assertBits(0x00000000L, CrMath.exp2(Float.NEGATIVE_INFINITY), "exp2f(-Infinity)");
    assertBits(0x7f800000L, CrMath.exp2(Float.MAX_VALUE), "exp2f(Float.MAX_VALUE)");
    assertBits(0x00000000L, CrMath.exp2(-Float.MAX_VALUE), "exp2f(-Float.MAX_VALUE)");
    assertTrue(Float.isNaN(CrMath.exp2(Float.intBitsToFloat(0x7fc00000))),
        "exp2f(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(CrMath.exp2(Float.intBitsToFloat(0x7f800001))),
        "exp2f(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.exp2(Float.intBitsToFloat(0xffc00001))),
        "exp2f(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.exp2(Float.intBitsToFloat(0x7fffffff))),
        "exp2f(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.exp2(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.exp2(Float.intBitsToFloat((int) input)), hex(input));
  }
}
