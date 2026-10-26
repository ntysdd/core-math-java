/*
 * Public entry points of the port.
 *
 * No CORE-MATH source corresponds to this file; it is the only local API.
 */

package io.github.ntysdd.coremath;

import io.github.ntysdd.coremath.binary32.AcosF;
import io.github.ntysdd.coremath.binary32.AsinhF;
import io.github.ntysdd.coremath.binary32.AsinF;
import io.github.ntysdd.coremath.binary32.Atan2F;
import io.github.ntysdd.coremath.binary32.AtanF;
import io.github.ntysdd.coremath.binary32.CbrtF;
import io.github.ntysdd.coremath.binary32.CosF;
import io.github.ntysdd.coremath.binary32.CoshF;
import io.github.ntysdd.coremath.binary32.CosPiF;
import io.github.ntysdd.coremath.binary32.ExpF;
import io.github.ntysdd.coremath.binary32.Exp2F;
import io.github.ntysdd.coremath.binary32.Expm1F;
import io.github.ntysdd.coremath.binary32.HypotF;
import io.github.ntysdd.coremath.binary32.Log10F;
import io.github.ntysdd.coremath.binary32.Log2F;
import io.github.ntysdd.coremath.binary32.LogF;
import io.github.ntysdd.coremath.binary32.Log1pF;
import io.github.ntysdd.coremath.binary32.PowF;
import io.github.ntysdd.coremath.binary32.SinF;
import io.github.ntysdd.coremath.binary32.SinhF;
import io.github.ntysdd.coremath.binary32.SinPiF;
import io.github.ntysdd.coremath.binary32.TanF;
import io.github.ntysdd.coremath.binary32.TanhF;
import io.github.ntysdd.coremath.binary64.Acos;
import io.github.ntysdd.coremath.binary64.Asinh;
import io.github.ntysdd.coremath.binary64.Asin;
import io.github.ntysdd.coremath.binary64.Atan2;
import io.github.ntysdd.coremath.binary64.Atan;
import io.github.ntysdd.coremath.binary64.Cbrt;
import io.github.ntysdd.coremath.binary64.Cos;
import io.github.ntysdd.coremath.binary64.Cosh;
import io.github.ntysdd.coremath.binary64.CosPi;
import io.github.ntysdd.coremath.binary64.Exp;
import io.github.ntysdd.coremath.binary64.Exp2;
import io.github.ntysdd.coremath.binary64.Expm1;
import io.github.ntysdd.coremath.binary64.Hypot;
import io.github.ntysdd.coremath.binary64.Log10;
import io.github.ntysdd.coremath.binary64.Log2;
import io.github.ntysdd.coremath.binary64.Log;
import io.github.ntysdd.coremath.binary64.Log1p;
import io.github.ntysdd.coremath.binary64.Pow;
import io.github.ntysdd.coremath.binary64.Sin;
import io.github.ntysdd.coremath.binary64.Sinh;
import io.github.ntysdd.coremath.binary64.SinPi;
import io.github.ntysdd.coremath.binary64.Tan;
import io.github.ntysdd.coremath.binary64.Tanh;

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

  /**
   * Power of two binary64 values; the translation of CORE-MATH
   * {@code cr_pow}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of {@code x} raised
   * to {@code y}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code pow(±0.0, y)} is {@code ±0.0} for {@code y} a positive odd
   *       integer, {@code +0.0} for other positive {@code y}, and
   *       {@code ±Infinity} for negative {@code y}
   *   <li>{@code pow(±Infinity, y)} is {@code ±0.0} for negative {@code y} and
   *       {@code ±Infinity} for positive {@code y}, with the sign of the odd
   *       integer case
   *   <li>{@code pow(x, ±0.0)} is {@code 1.0} for every {@code x}, NaN
   *       included
   *   <li>{@code pow(1.0, y)} is {@code 1.0} for every {@code y}, NaN and the
   *       infinities included
   *   <li>{@code pow(x, 1.0)} is {@code x}
   *   <li>{@code pow(x, y)} is NaN for a negative finite {@code x} and a
   *       non-integer {@code y}
   *   <li>{@code pow(-1.0, ±Infinity)} is {@code 1.0}
   *   <li>{@code pow(NaN, 0.0)} is {@code 1.0}, {@code pow(1.0, NaN)} is
   *       {@code 1.0}, and every other NaN argument gives a NaN
   * </ul>
   *
   * <p>Note that {@link Math#pow} answers NaN for {@code pow(±1.0, ±Infinity)}
   * and for {@code pow(1.0, NaN)}, where IEEE 754-2019 and CORE-MATH answer
   * {@code 1.0}.
   *
   * @param x the base
   * @param y the exponent
   * @return the correctly rounded {@code x} raised to {@code y}
   */
  public static double pow(double x, double y) {
    return Pow.compute(x, y);
  }

  /**
   * Arc tangent of a binary32 value; the translation of CORE-MATH
   * {@code cr_atanf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arc tangent of
   * {@code x}, in round-to-nearest-even, in the range {@code [-pi/2, pi/2]}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code atan(±0.0f)} is {@code ±0.0f}
   *   <li>{@code atan(±Infinity)} is {@code ±pi/2}, rounded to binary32
   *   <li>{@code atan(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded arc tangent of {@code x}
   */
  public static float atan(float x) {
    return AtanF.compute(x);
  }

  /**
   * Arc tangent of a binary64 value; the translation of CORE-MATH
   * {@code cr_atan}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arc tangent of
   * {@code x}, in round-to-nearest-even, in the range {@code [-pi/2, pi/2]}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code atan(±0.0)} is {@code ±0.0}
   *   <li>{@code atan(±Infinity)} is {@code ±pi/2}, rounded to binary64
   *   <li>{@code atan(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded arc tangent of {@code x}
   */
  public static double atan(double x) {
    return Atan.compute(x);
  }

  /**
   * Arc tangent of {@code y/x} for two binary32 values, with the quadrant
   * chosen from the signs; the translation of CORE-MATH {@code cr_atan2f}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arc tangent of
   * {@code y/x}, in round-to-nearest-even, in the range
   * {@code [-pi, pi]}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code atan2(±0.0f, +0.0f)} is {@code ±0.0f}
   *   <li>{@code atan2(±0.0f, -0.0f)} and {@code atan2(±0.0f, x < 0)} are
   *       {@code ±pi}
   *   <li>{@code atan2(±0.0f, x > 0)} is {@code ±0.0f}
   *   <li>{@code atan2(y > 0, ±0.0f)} is {@code +pi/2} and
   *       {@code atan2(y < 0, ±0.0f)} is {@code -pi/2}
   *   <li>{@code atan2(±y, +Infinity)} is {@code ±0.0f} and
   *       {@code atan2(±y, -Infinity)} is {@code ±pi}
   *   <li>{@code atan2(±Infinity, x)} is {@code ±pi/2}
   *   <li>{@code atan2(±Infinity, +Infinity)} is {@code ±pi/4} and
   *       {@code atan2(±Infinity, -Infinity)} is {@code ±3pi/4}
   *   <li>a NaN argument gives a NaN
   * </ul>
   *
   * @param y the ordinate
   * @param x the abscissa
   * @return the correctly rounded arc tangent of {@code y/x}, quadrant resolved
   */
  public static float atan2(float y, float x) {
    return Atan2F.compute(y, x);
  }

  /**
   * Arc tangent of {@code y/x} for two binary64 values, with the quadrant
   * chosen from the signs; the translation of CORE-MATH {@code cr_atan2}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arc tangent of
   * {@code y/x}, in round-to-nearest-even, in the range {@code [-pi, pi]}.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param y the ordinate
   * @param x the abscissa
   * @return the correctly rounded arc tangent of {@code y/x}, quadrant resolved
   */
  public static double atan2(double y, double x) {
    return Atan2.compute(y, x);
  }

  /**
   * Base-2 logarithm of a binary32 value; the translation of CORE-MATH
   * {@code cr_log2f}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) base-2 logarithm of
   * {@code x}, in round-to-nearest-even.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code log2(1.0f)} is {@code +0.0f}
   *   <li>{@code log2(±0.0f)} is {@code -Infinity}
   *   <li>{@code log2(x)} is NaN for {@code x < 0}
   *   <li>{@code log2(+Infinity)} is {@code +Infinity}
   *   <li>{@code log2(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded base-2 logarithm of {@code x}
   */
  public static float log2(float x) {
    return Log2F.compute(x);
  }

  /**
   * Base-2 logarithm of a binary64 value; the translation of CORE-MATH
   * {@code cr_log2}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) base-2 logarithm of
   * {@code x}, in round-to-nearest-even. In particular it is exact on the
   * powers of two and their reciprocals, like the {@code float} overload.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded base-2 logarithm of {@code x}
   */
  public static double log2(double x) {
    return Log2.compute(x);
  }

  /**
   * Base-10 logarithm of a binary32 value; the translation of CORE-MATH
   * {@code cr_log10f}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) base-10 logarithm of
   * {@code x}, in round-to-nearest-even. In particular it is exact on
   * {@code 10^n} for {@code 0 <= n <= 22}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code log10(1.0f)} is {@code +0.0f}
   *   <li>{@code log10(±0.0f)} is {@code -Infinity}
   *   <li>{@code log10(x)} is NaN for {@code x < 0}
   *   <li>{@code log10(+Infinity)} is {@code +Infinity}
   *   <li>{@code log10(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded base-10 logarithm of {@code x}
   */
  public static float log10(float x) {
    return Log10F.compute(x);
  }

  /**
   * Base-10 logarithm of a binary64 value; the translation of CORE-MATH
   * {@code cr_log10}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) base-10 logarithm of
   * {@code x}, in round-to-nearest-even. In particular it is exact on
   * {@code 10^n} for {@code 0 <= n <= 22}.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded base-10 logarithm of {@code x}
   */
  public static double log10(double x) {
    return Log10.compute(x);
  }

  /**
   * Arcsine of a binary32 value; the translation of CORE-MATH
   * {@code cr_asinf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arcsine of {@code x}, in
   * round-to-nearest-even, in the range {@code [-pi/2, pi/2]}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code asin(±0.0f)} is {@code ±0.0f}
   *   <li>{@code asin(±1.0f)} is {@code ±pi/2}
   *   <li>{@code asin(x)} is NaN for {@code |x| > 1}
   *   <li>{@code asin(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded arcsine of {@code x}
   */
  public static float asin(float x) {
    return AsinF.compute(x);
  }

  /**
   * Arcsine of a binary64 value; the translation of CORE-MATH {@code cr_asin}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arcsine of {@code x}, in
   * round-to-nearest-even, in the range {@code [-pi/2, pi/2]}.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded arcsine of {@code x}
   */
  public static double asin(double x) {
    return Asin.compute(x);
  }

  /**
   * Arccosine of a binary32 value; the translation of CORE-MATH
   * {@code cr_acosf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arccosine of {@code x}, in
   * round-to-nearest-even, in the range {@code [0, pi]}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code acos(1.0f)} is {@code +0.0f}
   *   <li>{@code acos(-1.0f)} is {@code pi}
   *   <li>{@code acos(±0.0f)} is {@code pi/2}
   *   <li>{@code acos(x)} is NaN for {@code |x| > 1}
   *   <li>{@code acos(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded arccosine of {@code x}
   */
  public static float acos(float x) {
    return AcosF.compute(x);
  }

  /**
   * Arccosine of a binary64 value; the translation of CORE-MATH
   * {@code cr_acos}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) arccosine of {@code x}, in
   * round-to-nearest-even, in the range {@code [0, pi]}.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded arccosine of {@code x}
   */
  public static double acos(double x) {
    return Acos.compute(x);
  }

  /**
   * Hyperbolic sine of a binary32 value; the translation of CORE-MATH
   * {@code cr_sinhf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) hyperbolic sine of
   * {@code x}, in round-to-nearest-even. Finite arguments whose result is too
   * large round to {@code ±Infinity}, as the standard requires.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code sinh(±0.0f)} is {@code ±0.0f}
   *   <li>{@code sinh(±Infinity)} is {@code ±Infinity}
   *   <li>{@code sinh(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded hyperbolic sine of {@code x}
   */
  public static float sinh(float x) {
    return SinhF.compute(x);
  }

  /**
   * Hyperbolic sine of a binary64 value; the translation of CORE-MATH
   * {@code cr_sinh}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) hyperbolic sine of
   * {@code x}, in round-to-nearest-even. Finite arguments whose result is too
   * large round to {@code ±Infinity}, as the standard requires.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded hyperbolic sine of {@code x}
   */
  public static double sinh(double x) {
    return Sinh.compute(x);
  }

  /**
   * Hyperbolic cosine of a binary32 value; the translation of CORE-MATH
   * {@code cr_coshf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) hyperbolic cosine of
   * {@code x}, in round-to-nearest-even. Finite arguments whose result is too
   * large round to {@code +Infinity}, as the standard requires.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code cosh(±0.0f)} is {@code 1.0f}
   *   <li>{@code cosh(±Infinity)} is {@code +Infinity}
   *   <li>{@code cosh(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded hyperbolic cosine of {@code x}
   */
  public static float cosh(float x) {
    return CoshF.compute(x);
  }

  /**
   * Hyperbolic cosine of a binary64 value; the translation of CORE-MATH
   * {@code cr_cosh}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) hyperbolic cosine of
   * {@code x}, in round-to-nearest-even. Finite arguments whose result is too
   * large round to {@code +Infinity}, as the standard requires.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded hyperbolic cosine of {@code x}
   */
  public static double cosh(double x) {
    return Cosh.compute(x);
  }

  /**
   * Hyperbolic tangent of a binary32 value; the translation of CORE-MATH
   * {@code cr_tanhf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) hyperbolic tangent of
   * {@code x}, in round-to-nearest-even, in the range {@code [-1, 1]}.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code tanh(±0.0f)} is {@code ±0.0f}
   *   <li>{@code tanh(±Infinity)} is {@code ±1.0f}
   *   <li>{@code tanh(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded hyperbolic tangent of {@code x}
   */
  public static float tanh(float x) {
    return TanhF.compute(x);
  }

  /**
   * Hyperbolic tangent of a binary64 value; the translation of CORE-MATH
   * {@code cr_tanh}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) hyperbolic tangent of
   * {@code x}, in round-to-nearest-even, in the range {@code [-1, 1]}.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded hyperbolic tangent of {@code x}
   */
  public static double tanh(double x) {
    return Tanh.compute(x);
  }

  /**
   * Cube root of a binary32 value; the translation of CORE-MATH
   * {@code cr_cbrtf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) cube root of {@code x}, in
   * round-to-nearest-even, and is exact on every cube.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code cbrt(±0.0f)} is {@code ±0.0f}
   *   <li>{@code cbrt(±Infinity)} is {@code ±Infinity}
   *   <li>{@code cbrt(NaN)} is NaN
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded cube root of {@code x}
   */
  public static float cbrt(float x) {
    return CbrtF.compute(x);
  }

  /**
   * Cube root of a binary64 value; the translation of CORE-MATH {@code cr_cbrt}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) cube root of {@code x}, in
   * round-to-nearest-even, and is exact on every cube.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded cube root of {@code x}
   */
  public static double cbrt(double x) {
    return Cbrt.compute(x);
  }

  /**
   * {@code 2^x} of a binary32 value; the translation of CORE-MATH
   * {@code cr_exp2f}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of two raised to
   * {@code x}, in round-to-nearest-even, and is exact on every power of two the
   * exponent field can express.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code exp2(±0.0f)} is {@code 1.0f}
   *   <li>{@code exp2(-Infinity)} is {@code +0.0f}
   *   <li>{@code exp2(+Infinity)} is {@code +Infinity}
   *   <li>{@code exp2(NaN)} is NaN
   *   <li>a finite argument whose result overflows rounds to
   *       {@code +Infinity}, one whose result underflows to {@code +0.0f}
   * </ul>
   *
   * @param x the exponent
   * @return the correctly rounded {@code 2^x}
   */
  public static float exp2(float x) {
    return Exp2F.compute(x);
  }

  /**
   * {@code 2^x} of a binary64 value; the translation of CORE-MATH
   * {@code cr_exp2}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of two raised to
   * {@code x}, in round-to-nearest-even, and is exact on every power of two the
   * exponent field can express.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the exponent
   * @return the correctly rounded {@code 2^x}
   */
  public static double exp2(double x) {
    return Exp2.compute(x);
  }

  /**
   * {@code log(1+x)} of a binary32 value, accurately for small {@code x}; the
   * translation of CORE-MATH {@code cr_log1pf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) natural logarithm of
   * {@code 1+x}, in round-to-nearest-even. It is not the same function as
   * {@code log(1.0f + x)}: for small {@code x} the addition would lose the bits
   * that this function keeps.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code log1p(±0.0f)} is {@code ±0.0f}
   *   <li>{@code log1p(-1.0f)} is {@code -Infinity}
   *   <li>{@code log1p(x)} is NaN for {@code x < -1}
   *   <li>{@code log1p(+Infinity)} is {@code +Infinity}
   *   <li>{@code log1p(NaN)} is NaN
   * </ul>
   *
   * @param x the argument, added to one
   * @return the correctly rounded natural logarithm of {@code 1+x}
   */
  public static float log1p(float x) {
    return Log1pF.compute(x);
  }

  /**
   * {@code log(1+x)} of a binary64 value, accurately for small {@code x}; the
   * translation of CORE-MATH {@code cr_log1p}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) natural logarithm of
   * {@code 1+x}, in round-to-nearest-even. It is not the same function as
   * {@code log(1.0 + x)}: for small {@code x} the addition would lose the bits
   * that this function keeps.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the argument, added to one
   * @return the correctly rounded natural logarithm of {@code 1+x}
   */
  public static double log1p(double x) {
    return Log1p.compute(x);
  }

  /**
   * {@code e^x - 1} of a binary32 value, accurately for small {@code x}; the
   * translation of CORE-MATH {@code cr_expm1f}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of {@code e^x - 1}, in
   * round-to-nearest-even. It is not the same function as {@code exp(x) - 1.0f}:
   * for small {@code x} the subtraction would lose the bits that this function
   * keeps, and {@code expm1(x)} is exact for every {@code x} whose result fits
   * the format.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code expm1(±0.0f)} is {@code ±0.0f}
   *   <li>{@code expm1(-Infinity)} is {@code -1.0f}
   *   <li>{@code expm1(+Infinity)} is {@code +Infinity}
   *   <li>{@code expm1(NaN)} is NaN
   *   <li>a finite argument whose result underflows rounds to {@code -1.0f} on
   *       the negative side and to {@code +0.0f} on the positive side
   * </ul>
   *
   * @param x the exponent
   * @return the correctly rounded {@code e^x - 1}
   */
  public static float expm1(float x) {
    return Expm1F.compute(x);
  }

  /**
   * {@code e^x - 1} of a binary64 value, accurately for small {@code x}; the
   * translation of CORE-MATH {@code cr_expm1}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of {@code e^x - 1}, in
   * round-to-nearest-even. It is not the same function as {@code exp(x) - 1.0}:
   * for small {@code x} the subtraction would lose the bits that this function
   * keeps, and {@code expm1(x)} is exact for every {@code x} whose result fits
   * the format.
   *
   * <p>The special cases are the ones of the {@code float} overload, with the
   * binary64 constants.
   *
   * @param x the exponent
   * @return the correctly rounded {@code e^x - 1}
   */
  public static double expm1(double x) {
    return Expm1.compute(x);
  }

  /**
   * {@code sin(pi*x)} of a binary32 value; the translation of CORE-MATH
   * {@code cr_sinpif}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) sine of {@code pi*x}, in
   * round-to-nearest-even. It is not {@code (float) Math.sin(Math.PI*x)}: the
   * argument of this function is exact, so the reduction inside it never loses
   * the bits of {@code x} that the {@code pi} constant would smear.
   *
   * <p>Special cases:
   * <ul>
   *   <li>{@code sinpi(±0.0f)} is {@code ±0.0f}
   *   <li>{@code sinpi(±Infinity)} is NaN
   *   <li>{@code sinpi(NaN)} is NaN
   *   <li>{@code sinpi(x)} is {@code ±0.0f} for every nonzero integer
   *       {@code x}, with the sign of {@code x}
   *   <li>{@code sinpi(x)} is {@code ±1.0f} for every half-integer {@code x},
   *       with the sign of {@code sin(pi*x)}
   * </ul>
   *
   * @param x the argument, in units of pi
   * @return the correctly rounded sine of {@code pi*x}
   */
  public static float sinpi(float x) {
    return SinPiF.compute(x);
  }

  /**
   * {@code sin(pi*x)} of a binary64 value; the translation of CORE-MATH
   * {@code cr_sinpi}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) sine of {@code pi*x}, in
   * round-to-nearest-even. The special cases are the ones of the {@code float}
   * overload, with the binary64 constants.
   *
   * @param x the argument, in units of pi
   * @return the correctly rounded sine of {@code pi*x}
   */
  public static double sinpi(double x) {
    return SinPi.compute(x);
  }

  /**
   * {@code cos(pi*x)} of a binary32 value; the translation of CORE-MATH
   * {@code cr_cospif}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) cosine of {@code pi*x}, in
   * round-to-nearest-even. It is not {@code (float) Math.cos(Math.PI*x)}: the
   * argument of this function is exact, so the reduction inside it never loses
   * the bits of {@code x} that the {@code pi} constant would smear.
   *
   * <p>Special cases:
   * <ul>
   *   <li>{@code cospi(±0.0f)} is {@code 1.0f}
   *   <li>{@code cospi(±Infinity)} is NaN
   *   <li>{@code cospi(NaN)} is NaN
   *   <li>{@code cospi(x)} is {@code -1.0f} for every odd integer {@code x} and
   *       {@code 1.0f} for every other integer
   *   <li>{@code cospi(x)} is {@code +0.0f} for every half-integer {@code x}
   * </ul>
   *
   * @param x the argument, in units of pi
   * @return the correctly rounded cosine of {@code pi*x}
   */
  public static float cospi(float x) {
    return CosPiF.compute(x);
  }

  /**
   * {@code cos(pi*x)} of a binary64 value; the translation of CORE-MATH
   * {@code cr_cospi}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) cosine of {@code pi*x}, in
   * round-to-nearest-even. The special cases are the ones of the {@code float}
   * overload, with the binary64 constants.
   *
   * @param x the argument, in units of pi
   * @return the correctly rounded cosine of {@code pi*x}
   */
  public static double cospi(double x) {
    return CosPi.compute(x);
  }

  /**
   * {@code sqrt(x*x + y*y)} of two binary32 values without intermediate
   * overflow or underflow; the translation of CORE-MATH {@code cr_hypotf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) Euclidean distance
   * {@code sqrt(x*x + y*y)}, in round-to-nearest-even. Neither the squares nor
   * their sum are formed in the binary32 format, so a result that fits is
   * returned even when {@code x*x} or {@code y*y} would overflow to infinity.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code hypot(±0.0f, ±0.0f)} is {@code +0.0f}
   *   <li>{@code hypot(x, ±0.0f)} is {@code |x|}
   *   <li>{@code hypot(±Infinity, y)} is {@code +Infinity}, even when {@code y}
   *       is NaN
   *   <li>{@code hypot(NaN, y)} is NaN unless {@code y} is an infinity
   *   <li>a finite result too large for the format rounds to
   *       {@code +Infinity}
   * </ul>
   *
   * @param x one leg
   * @param y the other leg
   * @return the correctly rounded {@code sqrt(x*x + y*y)}
   */
  public static float hypot(float x, float y) {
    return HypotF.compute(x, y);
  }

  /**
   * {@code sqrt(x*x + y*y)} of two binary64 values without intermediate
   * overflow or underflow; the translation of CORE-MATH {@code cr_hypot}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) Euclidean distance
   * {@code sqrt(x*x + y*y)}, in round-to-nearest-even, and keeps the subnormal
   * range exact. The special cases are the ones of the {@code float} overload,
   * with the binary64 constants.
   *
   * @param x one leg
   * @param y the other leg
   * @return the correctly rounded {@code sqrt(x*x + y*y)}
   */
  public static double hypot(double x, double y) {
    return Hypot.compute(x, y);
  }

  /**
   * Inverse hyperbolic sine of a binary32 value; the translation of CORE-MATH
   * {@code cr_asinhf}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of
   * {@code log(x + sqrt(x*x + 1))}, in round-to-nearest-even, and is the exact
   * {@code x} for every {@code x} small enough that the correction vanishes.
   *
   * <p>Special cases, matching IEEE 754-2019:
   * <ul>
   *   <li>{@code asinh(±0.0f)} is {@code ±0.0f}
   *   <li>{@code asinh(±Infinity)} is {@code ±Infinity}
   *   <li>{@code asinh(NaN)} is NaN
   *   <li>the function is odd and monotonic, so a subnormal argument never
   *       underflows to anything but itself
   * </ul>
   *
   * @param x the argument
   * @return the correctly rounded inverse hyperbolic sine
   */
  public static float asinh(float x) {
    return AsinhF.compute(x);
  }

  /**
   * Inverse hyperbolic sine of a binary64 value; the translation of CORE-MATH
   * {@code cr_asinh}.
   *
   * <p>The result is the correctly rounded (0.5 ulp) value of
   * {@code log(x + sqrt(x*x + 1))}, in round-to-nearest-even. The special cases
   * are the ones of the {@code float} overload, with the binary64 constants.
   *
   * @param x the argument
   * @return the correctly rounded inverse hyperbolic sine
   */
  public static double asinh(double x) {
    return Asinh.compute(x);
  }
}
