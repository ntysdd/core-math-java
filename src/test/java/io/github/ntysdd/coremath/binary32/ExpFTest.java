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
 * Tests of {@link CrMath#exp(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/exp.txt}, which the C entry point
 * {@code cr_expf} produced.
 */
class ExpFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/exp.txt");

  private static String hex(long bits) {
    return "expf(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0x3f800000L, CrMath.exp(0.0f), "expf(+0.0)");
    assertBits(0x3f800000L, CrMath.exp(-0.0f), "expf(-0.0)");
    assertBits(0x7f800000L, CrMath.exp(Float.POSITIVE_INFINITY), "expf(+Infinity)");
    assertBits(0x00000000L, CrMath.exp(Float.NEGATIVE_INFINITY), "expf(-Infinity)");
    assertTrue(Float.isNaN(CrMath.exp(Float.intBitsToFloat(0x7fc00000))),
        "expf(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.exp(Float.intBitsToFloat(0x7f800001))),
        "expf(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.exp(Float.intBitsToFloat(0xffc00001))),
        "expf(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.exp(Float.intBitsToFloat(0x7fffffff))),
        "expf(NaN with every payload bit set)");
    assertBits(0x3f800000L, CrMath.exp(Float.MIN_VALUE), "expf(smallest subnormal)");
    assertBits(0x3f800000L, CrMath.exp(-Float.MIN_VALUE),
        "expf(-smallest subnormal)");
    assertBits(0x7f800000L, CrMath.exp(Float.MAX_VALUE), "expf(Float.MAX_VALUE)");
    assertBits(0x00000000L, CrMath.exp(-Float.MAX_VALUE), "expf(-Float.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.exp(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.exp(Float.intBitsToFloat((int) input)), hex(input));
  }
}
