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
 * Tests of {@link CrMath#sin(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/sin.txt}, which the C entry point
 * {@code cr_sinf} produced.
 */
class SinFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/sin.txt");

  private static String hex(long bits) {
    return "sinf(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x00000000L, CrMath.sin(0.0f), "sinf(+0.0)");
    assertBits(0x80000000L, CrMath.sin(-0.0f), "sinf(-0.0)");
    assertTrue(Float.isNaN(CrMath.sin(Float.POSITIVE_INFINITY)), "sinf(+Infinity)");
    assertTrue(Float.isNaN(CrMath.sin(Float.NEGATIVE_INFINITY)), "sinf(-Infinity)");
    assertTrue(Float.isNaN(CrMath.sin(Float.intBitsToFloat(0x7fc00000))),
        "sinf(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.sin(Float.intBitsToFloat(0x7f800001))),
        "sinf(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.sin(Float.intBitsToFloat(0xffc00001))),
        "sinf(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.sin(Float.intBitsToFloat(0x7fffffff))),
        "sinf(NaN with every payload bit set)");
    assertBits(0x00000001L, CrMath.sin(Float.MIN_VALUE), "sinf(smallest subnormal)");
    assertBits(0x80000001L, CrMath.sin(-Float.MIN_VALUE),
        "sinf(-smallest subnormal)");
    assertBits(0xbf0599b3L, CrMath.sin(Float.MAX_VALUE), "sinf(Float.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.sin(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.sin(Float.intBitsToFloat((int) input)), hex(input));
  }
}
