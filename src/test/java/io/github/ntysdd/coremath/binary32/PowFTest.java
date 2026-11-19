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
 * Tests of {@link CrMath#pow(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/pow.txt}, which the C entry point
 * {@code cr_powf} produced, and against a sample of the upstream worst
 * cases {@code pow.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class PowFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/pow.txt");

  private static String hex(long y, long x) {
    return "powf(0x" + Vectors.hex(y, 32) + ", 0x"
        + Vectors.hex(x, 32) + ")";
  }


  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> worstCases() {
    return Vectors.stream(V.worstCases);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3f800000L, CrMath.pow(0.0f, 0.0f), "powf(0.0f, 0.0f)");
    assertBits(0x7f800000L, CrMath.pow(0.0f, -1.0f), "powf(0.0f, -1.0f)");
    assertBits(0xff800000L, CrMath.pow(-0.0f, -1.0f), "powf(-0.0f, -1.0f)");
    assertBits(0x7f800000L, CrMath.pow(-0.0f, -2.0f), "powf(-0.0f, -2.0f)");
    assertBits(0x3f800000L, CrMath.pow(1.0f, Float.intBitsToFloat(0x7fc00000)), "powf(1.0f, NaN)");
    assertBits(0x3f800000L, CrMath.pow(Float.intBitsToFloat(0x7fc00000), 0.0f), "powf(NaN, 0.0f)");
    assertBits(0x3f800000L, CrMath.pow(-1.0f, Float.POSITIVE_INFINITY), "powf(-1.0f, +Infinity)");
    assertBits(0x3f800000L, CrMath.pow(Float.POSITIVE_INFINITY, 0.0f), "powf(+Infinity, 0.0f)");
    assertBits(0x44800000L, CrMath.pow(2.0f, 10.0f), "powf(2.0f, 10.0f)");
    assertBits(0x3f000000L, CrMath.pow(2.0f, -1.0f), "powf(2.0f, -1.0f)");
    assertBits(0xc1000000L, CrMath.pow(-2.0f, 3.0f), "powf(-2.0f, 3.0f)");
    assertTrue(Float.isNaN(CrMath.pow(-2.0f, 0.5f)), "powf(-2.0f, 0.5f)");
    assertTrue(Float.isNaN(CrMath.pow(Float.intBitsToFloat(0x7fc00000), 1.0f)),
        "powf(0x7fc00000, 1.0): quiet NaN");
    assertTrue(Float.isNaN(CrMath.pow(Float.intBitsToFloat(0x7f800001), 1.0f)),
        "powf(0x7f800001, 1.0): signalling NaN, smallest payload");
    assertTrue(Float.isNaN(CrMath.pow(Float.intBitsToFloat(0xffc00001), 1.0f)),
        "powf(0xffc00001, 1.0): negative NaN with a payload");
    assertTrue(Float.isNaN(CrMath.pow(Float.intBitsToFloat(0x7fffffff), 1.0f)),
        "powf(0x7fffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected, CrMath.pow(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected, CrMath.pow(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected, CrMath.pow(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }
}
