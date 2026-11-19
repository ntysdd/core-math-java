package io.github.ntysdd.coremath.binary32;

import static io.github.ntysdd.coremath.test.Vectors.assertBits;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ntysdd.coremath.CrMath;
import io.github.ntysdd.coremath.test.Vectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {@link CrMath#log10(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/log10.txt}, which the C entry
 * point {@code cr_log10f} produced.
 */
class Log10FTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/log10.txt");

  private static String hex(long bits) {
    return "log10f(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0xff800000L, CrMath.log10(0.0f), "log10f(+0.0)");
    assertBits(0xff800000L, CrMath.log10(-0.0f), "log10f(-0.0)");
    assertTrue(Float.isNaN(CrMath.log10(-1.0f)), "log10f(-1.0)");
    assertBits(0x00000000L, CrMath.log10(1.0f), "log10f(1.0)");
    assertBits(0x7f800000L, CrMath.log10(Float.POSITIVE_INFINITY),
        "log10f(+Infinity)");
    assertTrue(Float.isNaN(CrMath.log10(Float.NEGATIVE_INFINITY)),
        "log10f(-Infinity)");
    assertTrue(Float.isNaN(CrMath.log10(Float.intBitsToFloat(0x7fc00000))),
        "log10f(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.log10(Float.intBitsToFloat(0x7f800001))),
        "log10f(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.log10(Float.intBitsToFloat(0xffc00001))),
        "log10f(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.log10(Float.intBitsToFloat(0x7fffffff))),
        "log10f(NaN with every payload bit set)");
    assertBits(0xc23369f4L, CrMath.log10(Float.MIN_VALUE),
        "log10f(smallest subnormal)");
    assertTrue(Float.isNaN(CrMath.log10(-Float.MIN_VALUE)),
        "log10f(-smallest subnormal)");
    assertBits(0x421a209bL, CrMath.log10(Float.MAX_VALUE),
        "log10f(Float.MAX_VALUE)");
  }

  @Test
  void exactPowersOfTen() {
    float p = 1.0f;
    for (int n = 0; n <= 10; n++) {
      assertEquals(n, CrMath.log10(p), "log10f(10^" + n + ")");
      p *= 10.0f;
    }
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log10(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log10(Float.intBitsToFloat((int) input)), hex(input));
  }
}
