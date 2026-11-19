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
 * Tests of {@link CrMath#sincos(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/sincos.txt}, which the C entry
 * point {@code cr_sincosf} produced.  The two result columns are the sine and
 * the cosine, in the order of {@link CrMath.SincosResultF}.
 */
class SincosFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/sincos.txt");

  private static String hex(long bits) {
    return "sincosf(0x" + Vectors.hex(bits, 32) + ")";
  }

  private static Stream<Arguments> directed() {
    return Vectors.stream(V.directed);
  }

  private static Stream<Arguments> random() {
    return Vectors.stream(V.random);
  }

  @Test
  void specialValues() {
    CrMath.SincosResultF r = CrMath.sincos(0.0f);
    assertBits(0x00000000L, r.sin, "sincosf(+0.0): sin");
    assertBits(0x3f800000L, r.cos, "sincosf(+0.0): cos");
    r = CrMath.sincos(-0.0f);
    assertBits(0x80000000L, r.sin, "sincosf(-0.0): sin");
    assertBits(0x3f800000L, r.cos, "sincosf(-0.0): cos");
    r = CrMath.sincos(Float.POSITIVE_INFINITY);
    assertTrue(Float.isNaN(r.sin), "sincosf(+Infinity): sin");
    assertTrue(Float.isNaN(r.cos), "sincosf(+Infinity): cos");
    r = CrMath.sincos(Float.NEGATIVE_INFINITY);
    assertTrue(Float.isNaN(r.sin), "sincosf(-Infinity): sin");
    assertTrue(Float.isNaN(r.cos), "sincosf(-Infinity): cos");
    r = CrMath.sincos(Float.intBitsToFloat(0x7fc00000));
    assertTrue(Float.isNaN(r.sin), "sincosf(quiet NaN): sin");
    assertTrue(Float.isNaN(r.cos), "sincosf(quiet NaN): cos");
    r = CrMath.sincos(Float.intBitsToFloat(0x7f800001));
    assertTrue(Float.isNaN(r.sin), "sincosf(signalling NaN): sin");
    assertTrue(Float.isNaN(r.cos), "sincosf(signalling NaN): cos");
    r = CrMath.sincos(Float.intBitsToFloat(0xffc00001));
    assertTrue(Float.isNaN(r.sin), "sincosf(negative NaN): sin");
    assertTrue(Float.isNaN(r.cos), "sincosf(negative NaN): cos");
    r = CrMath.sincos(Float.intBitsToFloat(0x7fffffff));
    assertTrue(Float.isNaN(r.sin), "sincosf(NaN, every payload bit): sin");
    assertTrue(Float.isNaN(r.cos), "sincosf(NaN, every payload bit): cos");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long input, long expectedSin, long expectedCos) {
    CrMath.SincosResultF r = CrMath.sincos(Float.intBitsToFloat((int) input));
    assertBits(expectedSin, r.sin, hex(input) + ": sin");
    assertBits(expectedCos, r.cos, hex(input) + ": cos");
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long input, long expectedSin, long expectedCos) {
    CrMath.SincosResultF r = CrMath.sincos(Float.intBitsToFloat((int) input));
    assertBits(expectedSin, r.sin, hex(input) + ": sin");
    assertBits(expectedCos, r.cos, hex(input) + ": cos");
  }
}
