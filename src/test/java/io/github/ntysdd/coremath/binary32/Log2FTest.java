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
 * Tests of {@link CrMath#log2(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/log2.txt}, which the C entry point
 * {@code cr_log2f} produced.
 */
class Log2FTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/log2.txt");

  private static String hex(long bits) {
    return "log2f(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0xff800000L, CrMath.log2(0.0f), "log2f(+0.0)");
    assertBits(0xff800000L, CrMath.log2(-0.0f), "log2f(-0.0)");
    assertTrue(Float.isNaN(CrMath.log2(-1.0f)), "log2f(-1.0)");
    assertBits(0x00000000L, CrMath.log2(1.0f), "log2f(1.0)");
    assertBits(0x7f800000L, CrMath.log2(Float.POSITIVE_INFINITY),
        "log2f(+Infinity)");
    assertTrue(Float.isNaN(CrMath.log2(Float.NEGATIVE_INFINITY)), "log2f(-Infinity)");
    assertTrue(Float.isNaN(CrMath.log2(Float.intBitsToFloat(0x7fc00000))),
        "log2f(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.log2(Float.intBitsToFloat(0x7f800001))),
        "log2f(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.log2(Float.intBitsToFloat(0xffc00001))),
        "log2f(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.log2(Float.intBitsToFloat(0x7fffffff))),
        "log2f(NaN with every payload bit set)");
    assertBits(0xc3150000L, CrMath.log2(Float.MIN_VALUE),
        "log2f(smallest subnormal) is -149");
    assertTrue(Float.isNaN(CrMath.log2(-Float.MIN_VALUE)),
        "log2f(-smallest subnormal)");
    assertBits(0x43000000L, CrMath.log2(Float.MAX_VALUE),
        "log2f(Float.MAX_VALUE) is 128");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log2(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log2(Float.intBitsToFloat((int) input)), hex(input));
  }
}
