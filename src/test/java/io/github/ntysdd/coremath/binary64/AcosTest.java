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
 * Tests of {@link CrMath#acos(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/acos.txt}, which the C entry point
 * {@code cr_acos} produced, and against a sample of the upstream worst
 * cases {@code acos.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class AcosTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/acos.txt");

  private static String hex(long input) {
    return "acos(0x" + Vectors.hex(input, 64) + ")";
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
    assertBits(0x3ff921fb54442d18L, CrMath.acos(0.0), "acos(+0.0)");
    assertBits(0x3ff921fb54442d18L, CrMath.acos(-0.0), "acos(-0.0)");
    assertBits(0x0000000000000000L, CrMath.acos(1.0), "acos(1.0)");
    assertBits(0x400921fb54442d18L, CrMath.acos(-1.0), "acos(-1.0)");
    assertTrue(Double.isNaN(CrMath.acos(2.0)), "acos(2.0)");
    assertTrue(Double.isNaN(CrMath.acos(-2.0)), "acos(-2.0)");
    assertTrue(Double.isNaN(CrMath.acos(Double.POSITIVE_INFINITY)), "acos(+Infinity)");
    assertTrue(Double.isNaN(CrMath.acos(Double.NEGATIVE_INFINITY)), "acos(-Infinity)");
    assertTrue(Double.isNaN(CrMath.acos(Double.longBitsToDouble(0x7ff8000000000000L))),
        "acos(0x7ff8000000000000, quiet NaN)");
    assertTrue(Double.isNaN(CrMath.acos(Double.longBitsToDouble(0x7ff0000000000001L))),
        "acos(0x7ff0000000000001, signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.acos(Double.longBitsToDouble(0xfff8000000000001L))),
        "acos(0xfff8000000000001, negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.acos(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "acos(0x7fffffffffffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.acos(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.acos(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.acos(Double.longBitsToDouble(input)), hex(input));
  }
}
