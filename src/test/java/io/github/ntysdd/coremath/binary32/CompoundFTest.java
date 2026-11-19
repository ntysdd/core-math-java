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
 * Tests of {@link CrMath#compound(float)} against the vectors of
 * {@code src/test/resources/vectors/binary32/compound.txt}, which the C entry point
 * {@code cr_compoundf} produced, and against a sample of the upstream worst
 * cases {@code compound.wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class CompoundFTest {

  private static final Vectors V = Vectors.load("/vectors/binary32/compound.txt");

  private static String hex(long y, long x) {
    return "compoundf(0x" + Vectors.hex(y, 32) + ", 0x"
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
    assertBits(0x3f800000L, CrMath.compound(0.0f, 1.0f), "compoundf(0.0f, 1.0f)");
    assertBits(0x3f800000L, CrMath.compound(0.0f, 10.0f), "compoundf(0.0f, 10.0f)");
    assertBits(0x3f800000L, CrMath.compound(1.0f, 0.0f), "compoundf(1.0f, 0.0f)");
    assertBits(0x40000000L, CrMath.compound(1.0f, 1.0f), "compoundf(1.0f, 1.0f)");
    assertBits(0x40800000L, CrMath.compound(1.0f, 2.0f), "compoundf(1.0f, 2.0f)");
    assertBits(0x3e800000L, CrMath.compound(-0.5f, 2.0f), "compoundf(-0.5f, 2.0f)");
    assertBits(0x3f800000L, CrMath.compound(-0.0f, 1.0f), "compoundf(-0.0f, 1.0f)");
    assertBits(0x7f800000L, CrMath.compound(1.0f, Float.POSITIVE_INFINITY), "compoundf(1.0f, +Infinity)");
    assertBits(0x7f800000L, CrMath.compound(Float.POSITIVE_INFINITY, 1.0f), "compoundf(+Infinity, 1.0f)");
    assertTrue(Float.isNaN(CrMath.compound(-2.0f, 0.5f)), "compoundf(-2.0f, 0.5f)");
    assertTrue(Float.isNaN(CrMath.compound(Float.intBitsToFloat(0x7fc00000), 1.0f)), "compoundf(NaN, 1.0f)");
    assertTrue(Float.isNaN(CrMath.compound(Float.intBitsToFloat(0x7fc00000), 1.0f)),
        "compoundf(0x7fc00000, 1.0): quiet NaN");
    assertTrue(Float.isNaN(CrMath.compound(Float.intBitsToFloat(0x7f800001), 1.0f)),
        "compoundf(0x7f800001, 1.0): signalling NaN, smallest payload");
    assertTrue(Float.isNaN(CrMath.compound(Float.intBitsToFloat(0xffc00001), 1.0f)),
        "compoundf(0xffc00001, 1.0): negative NaN with a payload");
    assertTrue(Float.isNaN(CrMath.compound(Float.intBitsToFloat(0x7fffffff), 1.0f)),
        "compoundf(0x7fffffff, 1.0): NaN with every payload bit set");
  }

  @ParameterizedTest
  @MethodSource("directed")
  void directedCases(long y, long x, long expected) {
    assertBits(expected, CrMath.compound(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("worstCases")
  void upstreamWorstCases(long y, long x, long expected) {
    assertBits(expected, CrMath.compound(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }

  @ParameterizedTest
  @MethodSource("random")
  void randomSweep(long y, long x, long expected) {
    assertBits(expected, CrMath.compound(Float.intBitsToFloat((int) y), Float.intBitsToFloat((int) x)), hex(y, x));
  }
}
