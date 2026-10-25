/*
 * Public entry points of the port.
 *
 * No CORE-MATH source corresponds to this file; it is the only local API.
 */

package io.github.ntysdd.coremath;

import io.github.ntysdd.coremath.binary32.SinF;
import io.github.ntysdd.coremath.binary64.Sin;

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
}
