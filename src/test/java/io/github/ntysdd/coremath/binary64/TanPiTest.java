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
 * Tests of {@link CrMath#tanpi(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/tanpi.txt}, which the C entry point
 * {@code cr_tanpi} produced, and against a sample of the upstream worst
 * cases {@code tanpi.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class TanPiTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/tanpi.txt");

  private static String hex(long input) {
    return "tanpi(0x" + Vectors.hex(input, 64) + ")";
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
    assertBits(0x0000000000000000L, CrMath.tanpi(0.0), "tanpi(+0.0)");
    assertBits(0x8000000000000000L, CrMath.tanpi(-0.0), "tanpi(-0.0)");
    assertBits(0x3ff0000000000000L, CrMath.tanpi(0.25), "tanpi(0.25)");
    assertBits(0xbff0000000000000L, CrMath.tanpi(-0.25), "tanpi(-0.25)");
    assertBits(0x7ff0000000000000L, CrMath.tanpi(0.5), "tanpi(0.5)");
    assertBits(0xfff0000000000000L, CrMath.tanpi(-0.5), "tanpi(-0.5)");
    assertBits(0x8000000000000000L, CrMath.tanpi(1.0), "tanpi(1.0)");
    assertBits(0x0000000000000000L, CrMath.tanpi(-1.0), "tanpi(-1.0)");
    assertTrue(Double.isNaN(CrMath.tanpi(Double.POSITIVE_INFINITY)), "tanpi(+Infinity)");
    assertTrue(Double.isNaN(CrMath.tanpi(Double.NEGATIVE_INFINITY)), "tanpi(-Infinity)");
    assertTrue(Double.isNaN(CrMath.tanpi(Double.longBitsToDouble(0x7ff8000000000000L))),
        "tanpi(0x7ff8000000000000, quiet NaN)");
    assertTrue(Double.isNaN(CrMath.tanpi(Double.longBitsToDouble(0x7ff0000000000001L))),
        "tanpi(0x7ff0000000000001, signalling NaN, smallest payload)");
    assertTrue(Double.isNaN(CrMath.tanpi(Double.longBitsToDouble(0xfff8000000000001L))),
        "tanpi(0xfff8000000000001, negative NaN with a payload)");
    assertTrue(Double.isNaN(CrMath.tanpi(Double.longBitsToDouble(0x7fffffffffffffffL))),
        "tanpi(0x7fffffffffffffff, NaN with every payload bit set)");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expected) {
    assertBits(expected, CrMath.tanpi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expected) {
    assertBits(expected, CrMath.tanpi(Double.longBitsToDouble(input)), hex(input));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expected) {
    assertBits(expected, CrMath.tanpi(Double.longBitsToDouble(input)), hex(input));
  }
}
