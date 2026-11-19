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
 * Tests of {@link CrMath#log(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/log.txt}, which the C entry point
 * {@code cr_logf} produced.
 */
class LogFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/log.txt");

  private static String hex(long bits) {
    return "logf(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    assertBits(0xff800000L, CrMath.log(0.0f), "logf(+0.0)");
    assertBits(0xff800000L, CrMath.log(-0.0f), "logf(-0.0)");
    assertTrue(Float.isNaN(CrMath.log(-1.0f)), "logf(-1.0)");
    assertBits(0x00000000L, CrMath.log(1.0f), "logf(1.0)");
    assertBits(0x7f800000L, CrMath.log(Float.POSITIVE_INFINITY), "logf(+Infinity)");
    assertTrue(Float.isNaN(CrMath.log(Float.NEGATIVE_INFINITY)), "logf(-Infinity)");
    assertTrue(Float.isNaN(CrMath.log(Float.intBitsToFloat(0x7fc00000))),
        "logf(quiet NaN)");
    assertTrue(Float.isNaN(CrMath.log(Float.intBitsToFloat(0x7f800001))),
        "logf(signalling NaN, smallest payload)");
    assertTrue(Float.isNaN(CrMath.log(Float.intBitsToFloat(0xffc00001))),
        "logf(negative NaN with a payload)");
    assertTrue(Float.isNaN(CrMath.log(Float.intBitsToFloat(0x7fffffff))),
        "logf(NaN with every payload bit set)");
    assertBits(0xc2ce8ed0L, CrMath.log(Float.MIN_VALUE), "logf(smallest subnormal)");
    assertTrue(Float.isNaN(CrMath.log(-Float.MIN_VALUE)),
        "logf(-smallest subnormal)");
    assertBits(0x42b17218L, CrMath.log(Float.MAX_VALUE), "logf(Float.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log(Float.intBitsToFloat((int) input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log(Float.intBitsToFloat((int) input)), hex(input));
  }
}
