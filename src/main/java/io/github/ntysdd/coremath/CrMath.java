/*
 * Public entry points of the port.
 *
 * No CORE-MATH source corresponds to this file; it is the only local API.
 */

package io.github.ntysdd.coremath;

import io.github.ntysdd.coremath.binary32.CosF;
import io.github.ntysdd.coremath.binary32.SinF;
import io.github.ntysdd.coremath.binary32.TanF;
import io.github.ntysdd.coremath.binary64.Cos;
import io.github.ntysdd.coremath.binary64.Sin;
import io.github.ntysdd.coremath.binary64.Tan;

/**
 * Correctly rounded elementary functions.
 *
 * <p>Every method returns the correctly rounded (0.5 ulp) result of the
 * corresponding CORE-MATH function, with the same bits as the C code, including
 * signed zeros, infinities and subnormals. The bit pattern of a NaN result is
 * not part of the promise; only that the result is a NaN.
 */
public final class CrMath {
  private CrMath() {
  }

  /**
   * Sine of a binary32 value; the translation of CORE-MATH {@code cr_sinf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) sine of {@code x}, in
   * round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code sin(±0.0f)} is {@code ±0.0f}
   *   <li>{@code sin(±Infinity)} is NaN
   *   <li>{@code sin(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, in radians
   * @return the correctly rounded sine of {@code x}
   */
  public static float sin(float x) {
    return SinF.compute(x);
  }

  /**
   * Sine of a binary64 value; the translation of CORE-MATH {@code cr_sin}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) sine of {@code x}, in
   * round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code sin(±0.0)} is {@code ±0.0}
   *   <li>{@code sin(±Infinity)} is NaN
   *   <li>{@code sin(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, in radians
   * @return the correctly rounded sine of {@code x}
   */
  public static double sin(double x) {
    return Sin.compute(x);
  }

  /**
   * Cosine of a binary32 value; the translation of CORE-MATH {@code cr_cosf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) cosine of {@code x}, in
   * round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code cos(±0.0f)} is {@code 1.0f}
   *   <li>{@code cos(±Infinity)} is NaN
   *   <li>{@code cos(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, in radians
   * @return the correctly rounded cosine of {@code x}
   */
  public static float cos(float x) {
    return CosF.compute(x);
  }

  /**
   * Cosine of a binary64 value; the translation of CORE-MATH {@code cr_cos}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) cosine of {@code x}, in
   * round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code cos(±0.0)} is {@code 1.0}
   *   <li>{@code cos(±Infinity)} is NaN
   *   <li>{@code cos(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, in radians
   * @return the correctly rounded cosine of {@code x}
   */
  public static double cos(double x) {
    return Cos.compute(x);
  }

  /**
   * Tangent of a binary32 value; the translation of CORE-MATH {@code cr_tanf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) tangent of {@code x}, in
   * round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code tan(±0.0f)} is {@code ±0.0f}
   *   <li>{@code tan(±Infinity)} is NaN
   *   <li>{@code tan(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, in radians
   * @return the correctly rounded tangent of {@code x}
   */
  public static float tan(float x) {
    return TanF.compute(x);
  }

  /**
   * Tangent of a binary64 value; the translation of CORE-MATH {@code cr_tan}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) tangent of {@code x}, in
   * round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code tan(±0.0)} is {@code ±0.0}
   *   <li>{@code tan(±Infinity)} is NaN
   *   <li>{@code tan(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, in radians
   * @return the correctly rounded tangent of {@code x}
   */
  public static double tan(double x) {
    return Tan.compute(x);
  }
}
