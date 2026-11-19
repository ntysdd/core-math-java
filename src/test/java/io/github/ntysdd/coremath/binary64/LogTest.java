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
 * Tests of {@link CrMath#log(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/log.txt}, which the C entry point
 * {@code cr_log} produced, and against a sample of the upstream worst cases
 * {@code log.wc}.
 */
class LogTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/log.txt");

  private static String hex(long bits) {
    return "log(0x" + Vectors.hex(bits, 64) + ")";
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
    assertBits(0xfff0000000000000L, CrMath.log(0.0), "log(+0.0)");
    assertBits(0xfff0000000000000L, CrMath.log(-0.0), "log(-0.0)");
    assertTrue(Double.isNaN(CrMath.log(-1.0)), "log(-1.0)");
    assertBits(0x0000000000000000L, CrMath.log(1.0), "log(1.0)");
    assertBits(0x7ff0000000000000L, CrMath.log(Double.POSITIVE_INFINITY),
        "log(+Infinity)");
    assertTrue(Double.isNaN(CrMath.log(Double.NEGATIVE_INFINITY)), "log(-Infinity)");
    assertTrue(Double.isNaN(CrMath.log(Double.longBitsToDouble(0x7ff8000000000000L))),
        "log(quiet NaN)");
    assertTrue(Double.isNaN(CrMath.log(Double.longBitsToDouble(0x7ff0000000000001L))),
        "log(signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.log(Double.longBitsToDouble(0xfff8000000000001L))),
        "log(negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.log(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "log(NaN with every payload bit set)");
    assertBits(0xc0874385446d71c3L, CrMath.log(Double.MIN_VALUE),
        "log(smallest subnormal)");
    assertTrue(Double.isNaN(CrMath.log(-Double.MIN_VALUE)),
        "log(-smallest subnormal)");
    assertBits(0x40862e42fefa39efL, CrMath.log(Double.MAX_VALUE),
        "log(Double.MAX_VALUE)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.log(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.log(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.log(Double.longBitsToDouble(input)), hex(input));
  }
}
