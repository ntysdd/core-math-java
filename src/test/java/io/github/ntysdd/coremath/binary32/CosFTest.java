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
 * Tests of {@link CrMath#cos(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/cos.txt}, which the C entry point
 * {@code cr_cosf} produced.
 */
class CosFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/cos.txt");

  private static String hex(long bits) {
    return "cosf(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3f800000L, CrMath.cos(0.0f), "cosf(+0.0)");
    assertBits(0x3f800000L, CrMath.cos(-0.0f), "cosf(-0.0)");
    assertTrue(Float.isNaN(CrMath.cos(Float.POSITIVE_INFINITY)), "cosf(+Infinity)");
    assertTrue(Float.isNaN(CrMath.cos(Float.NEGATIVE_INFINITY)), "cosf(-Infinity)");
    assertTrue(Float.isNaN(CrMath.cos(Float.intBitsToFloat(0x7fc00000))),
        "cosf(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.cos(Float.intBitsToFloat(0x7f800001))),
        "cosf(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.cos(Float.intBitsToFloat(0xffc00001))),
        "cosf(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.cos(Float.intBitsToFloat(0x7fffffff))),
        "cosf(NaN with every payload bit set)");
    assertBits(0x3f800000L, CrMath.cos(Float.MIN_VALUE), "cosf(smallest subnormal)");
    assertBits(0x3f800000L, CrMath.cos(-Float.MIN_VALUE),
        "cosf(-smallest subnormal)");
    assertBits(0x3f5a5f96L, CrMath.cos(Float.MAX_VALUE), "cosf(Float.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.cos(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.cos(Float.intBitsToFloat((int) input)), hex(input));
  }
}
