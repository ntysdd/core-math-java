package io.github.ntysdd.coremath.binary64;

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
 * Tests of {@link CrMath#sin(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/sin.txt}, which the C entry point
 * {@code cr_sin} produced, and against a sample of the upstream worst cases
 * {@code sin.wc}.
 */
class SinTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/sin.txt");

  private static String hex(long bits) {
    return "sin(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0x0000000000000000L, CrMath.sin(0.0), "sin(+0.0)");
    assertBits(0x8000000000000000L, CrMath.sin(-0.0), "sin(-0.0)");
    assertTrue(Double.isNaN(CrMath.sin(Double.POSITIVE_INFINITY)), "sin(+Infinity)");
    assertTrue(Double.isNaN(CrMath.sin(Double.NEGATIVE_INFINITY)), "sin(-Infinity)");
    assertTrue(Double.isNaN(CrMath.sin(Double.longBitsToDouble(0x7ff8000000000000L))),
        "sin(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.sin(Double.longBitsToDouble(0x7ff0000000000001L))),
        "sin(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.sin(Double.longBitsToDouble(0xfff8000000000001L))),
        "sin(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.sin(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "sin(NaN with every payload bit set)");
    assertBits(0x0000000000000001L, CrMath.sin(Double.MIN_VALUE),
        "sin(smallest subnormal)");
    assertBits(0x8000000000000001L, CrMath.sin(-Double.MIN_VALUE),
        "sin(-smallest subnormal)");
    assertBits(0x3f7452fc98b34e97L, CrMath.sin(Double.MAX_VALUE),
        "sin(Double.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.sin(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.sin(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.sin(Double.longBitsToDouble(input)), hex(input));
  }
}
