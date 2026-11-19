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
 * Tests of {@link CrMath#sincos(double)} against the vectors of
 * {@code src/test/resources/vectors/binary64/sincos.txt}, which the C entry
 * point {@code cr_sincos} produced, and against a sample of the upstream worst
 * cases {@code sincos.wc}.  The two result columns are the sine and the cosine,
 * in the order of {@link CrMath.SincosResult}.
 */
class SincosTest {

  private static final Vectors V = Vectors.load("/vectors/binary64/sincos.txt");

  private static String hex(long bits) {
    return "sincos(0x" + Vectors.hex(bits, 64) + ")";
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
    CrMath.SincosResult r = CrMath.sincos(0.0);
    assertBits(0x0000000000000000L, r.sin, "sincos(+0.0): sin");
    assertBits(0x3ff0000000000000L, r.cos, "sincos(+0.0): cos");
    r = CrMath.sincos(-0.0);
    assertBits(0x8000000000000000L, r.sin, "sincos(-0.0): sin");
    assertBits(0x3ff0000000000000L, r.cos, "sincos(-0.0): cos");
    r = CrMath.sincos(Double.POSITIVE_INFINITY);
    assertTrue(Double.isNaN(r.sin), "sincos(+Infinity): sin");
    assertTrue(Double.isNaN(r.cos), "sincos(+Infinity): cos");
    r = CrMath.sincos(Double.NEGATIVE_INFINITY);
    assertTrue(Double.isNaN(r.sin), "sincos(-Infinity): sin");
    assertTrue(Double.isNaN(r.cos), "sincos(-Infinity): cos");
    r = CrMath.sincos(Double.longBitsToDouble(0x7ff8000000000000L));
    assertTrue(Double.isNaN(r.sin), "sincos(quiet NaN): sin");
    assertTrue(Double.isNaN(r.cos), "sincos(quiet NaN): cos");
    r = CrMath.sincos(Double.longBitsToDouble(0x7ff0000000000001L));
    assertTrue(Double.isNaN(r.sin), "sincos(signalling NaN): sin");
    assertTrue(Double.isNaN(r.cos), "sincos(signalling NaN): cos");
    r = CrMath.sincos(Double.longBitsToDouble(0xfff8000000000001L));
    assertTrue(Double.isNaN(r.sin), "sincos(negative NaN): sin");
    assertTrue(Double.isNaN(r.cos), "sincos(negative NaN): cos");
    r = CrMath.sincos(Double.longBitsToDouble(0x7fffffffffffffffL));
    assertTrue(Double.isNaN(r.sin), "sincos(NaN, every payload bit): sin");
    assertTrue(Double.isNaN(r.cos), "sincos(NaN, every payload bit): cos");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expectedSin, long expectedCos) {
    CrMath.SincosResult r = CrMath.sincos(Double.longBitsToDouble(input));
    assertBits(expectedSin, r.sin, hex(input) + ": sin");
    assertBits(expectedCos, r.cos, hex(input) + ": cos");
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long input, long expectedSin, long expectedCos) {
    CrMath.SincosResult r = CrMath.sincos(Double.longBitsToDouble(input));
    assertBits(expectedSin, r.sin, hex(input) + ": sin");
    assertBits(expectedCos, r.cos, hex(input) + ": cos");
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expectedSin, long expectedCos) {
    CrMath.SincosResult r = CrMath.sincos(Double.longBitsToDouble(input));
    assertBits(expectedSin, r.sin, hex(input) + ": sin");
    assertBits(expectedCos, r.cos, hex(input) + ": cos");
  }
}
