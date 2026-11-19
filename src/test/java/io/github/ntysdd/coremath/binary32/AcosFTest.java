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
 * Tests of {@link CrMath#acos(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/acos.txt}, which the C entry point
 * {@code cr_acosf} produced.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AcosFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/acos.txt");

  private static String hex(long input) {
    return "acosf(0x" + Vectors.hex(input, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3fc90fdbL, CrMath.acos(0.0f), "acosf(+0.0)");
    assertBits(0x3fc90fdbL, CrMath.acos(-0.0f), "acosf(-0.0)");
    assertBits(0x00000000L, CrMath.acos(1.0f), "acosf(1.0)");
    assertBits(0x40490fdbL, CrMath.acos(-1.0f), "acosf(-1.0)");
    assertTrue(Float.isNaN(CrMath.acos(2.0f)), "acosf(2.0)");
    assertTrue(Float.isNaN(CrMath.acos(-2.0f)), "acosf(-2.0)");
    assertTrue(Float.isNaN(CrMath.acos(Float.POSITIVE_INFINITY)), "acosf(+Infinity)");
    assertTrue(Float.isNaN(CrMath.acos(Float.NEGATIVE_INFINITY)), "acosf(-Infinity)");
    assertTrue(Float.isNaN(
        CrMath.acos(Float.intBitsToFloat(0x7fc00000))),
        "acosf(0x7fc00000, quiet NaN)");
    assertTrue(Float.isNaN(
        CrMath.acos(Float.intBitsToFloat(0x7f800001))),
        "acosf(0x7f800001, signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(
        CrMath.acos(Float.intBitsToFloat(0xffc00001))),
        "acosf(0xffc00001, negative NaN with a payload)");
    assertTrue(Float.isNaN(
        CrMath.acos(Float.intBitsToFloat(0x7fffffff))),
        "acosf(0x7fffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.acos(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.acos(Float.intBitsToFloat((int) input)), hex(input));
  }
}
