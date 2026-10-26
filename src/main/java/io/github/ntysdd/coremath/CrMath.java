/*
 * Public entry points of the port.
 *
 * No CORE-MATH source corresponds to this file; it is the only local API.
 */

package io.github.ntysdd.coremath;

import io.github.ntysdd.coremath.binary32.CosF;
import io.github.ntysdd.coremath.binary32.ExpF;
import io.github.ntysdd.coremath.binary32.LogF;
import io.github.ntysdd.coremath.binary32.PowF;
import io.github.ntysdd.coremath.binary32.SinF;
import io.github.ntysdd.coremath.binary32.TanF;
import io.github.ntysdd.coremath.binary64.Cos;
import io.github.ntysdd.coremath.binary64.Exp;
import io.github.ntysdd.coremath.binary64.Log;
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

  /**
   * Exponential of a binary32 value; the translation of CORE-MATH
   * {@code cr_expf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) {@code e} to the power
   * {@code x}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code exp(+0.0f)} and {@code exp(-0.0f)} are {@code 1.0f}
   *   <li>{@code exp(-Infinity)} is {@code +0.0f}
   *   <li>{@code exp(+Infinity)} is {@code +Infinity}
   *   <li>{@code exp(NaN)} is NaN
   *   <li>results too large to represent are {@code +Infinity}, results too
   *       small are {@code +0.0f}
   * </ul>
   *
   * @param x the exponent
   * @return the correctly rounded exponential of {@code x}
   */
  public static float exp(float x) {
    return ExpF.compute(x);
  }

  /**
   * Exponential of a binary64 value; the translation of CORE-MATH
   * {@code cr_exp}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) {@code e} to the power
   * {@code x}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code exp(+0.0)} and {@code exp(-0.0)} are {@code 1.0}
   *   <li>{@code exp(-Infinity)} is {@code +0.0}
   *   <li>{@code exp(+Infinity)} is {@code +Infinity}
   *   <li>{@code exp(NaN)} is NaN
   *   <li>results too large to represent are {@code +Infinity}, results too
   *       small are {@code +0.0}
   * </ul>
   *
   * @param x the exponent
   * @return the correctly rounded exponential of {@code x}
   */
  public static double exp(double x) {
    return Exp.compute(x);
  }

  /**
   * Natural logarithm of a binary32 value; the translation of CORE-MATH
   * {@code cr_logf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) natural logarithm of
   * {@code x}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code log(1.0f)} is {@code +0.0f}
   *   <li>{@code log(+0.0f)} and {@code log(-0.0f)} are
   *       {@code -Infinity}
   *   <li>{@code log(x)} is NaN for {@code x < 0}
   *   <li>{@code log(+Infinity)} is {@code +Infinity}
   *   <li>{@code log(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded natural logarithm of {@code x}
   */
  public static float log(float x) {
    return LogF.compute(x);
  }

  /**
   * Natural logarithm of a binary64 value; the translation of CORE-MATH
   * {@code cr_log}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) natural logarithm of
   * {@code x}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code log(1.0)} is {@code +0.0}
   *   <li>{@code log(+0.0)} and {@code log(-0.0)} are {@code -Infinity}
   *   <li>{@code log(x)} is NaN for {@code x < 0}
   *   <li>{@code log(+Infinity)} is {@code +Infinity}
   *   <li>{@code log(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded natural logarithm of {@code x}
   */
  public static double log(double x) {
    return Log.compute(x);
  }

  /**
   * Power of two binary32 values; the translation of CORE-MATH
   * {@code cr_powf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of {@code x} raised
   * to {@code y}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code pow(±0.0f, y)} is {@code ±0.0f} for {@code y} a positive odd
   *       integer, {@code +0.0f} for other positive {@code y}, and
   *       {@code ±Infinity} for negative {@code y}
   *   <li>{@code pow(±Infinity, y)} is {@code ±0.0f} for negative {@code y} and
   *       {@code ±Infinity} for positive {@code y}, with the sign of the odd
   *       integer case
   *   <li>{@code pow(x, ±0.0f)} is {@code 1.0f} for every {@code x}, NaN
   *       included
   *   <li>{@code pow(1.0f, y)} is {@code 1.0f} for every {@code y}
   *   <li>{@code pow(x, 1.0f)} is {@code x}
   *   <li>{@code pow(x, y)} is NaN for a negative finite {@code x} and a
   *       non-integer {@code y}
   *   <li>{@code pow(-1.0f, ±Infinity)} is {@code 1.0f}
   *   <li>{@code pow(NaN, 0.0f)} is {@code 1.0f}, {@code pow(1.0f, NaN)} is
   *       {@code 1.0f}, and every other NaN argument gives a NaN
   * </ul>
   *
   * @param x the base
   * @param y the exponent
   * @return the correctly rounded {@code x} raised to {@code y}
   */
  public static float pow(float x, float y) {
    return PowF.compute(x, y);
  }
}
