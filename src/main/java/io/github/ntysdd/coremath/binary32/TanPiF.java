/*
 * Correctly rounded tangent of a binary32 value, in half-revolution units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/tanpi/tanpif.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_tanpif -> TanPiF.compute
 *
 * Copyright (c) 2023-2025 Alexei Sibidanov.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Translated mechanically; see the class Javadoc for the deviations.
 */

package io.github.ntysdd.coremath.binary32;

/**
 * Correctly rounded {@code tan(pi*x)} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_tanpif}.
 *
 * <p>The reduction is in {@code float}: {@code 4x} and {@code x} are rounded to
 * the nearest integer, the difference {@code zf = x - round(x)} is small, and
 * the exact multiples of {@code 1/4} are answered from the parity of
 * {@code 4x} without any arithmetic. The remaining arguments are evaluated
 * with a rational function of {@code z = zf} in double precision.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code roundevenf_finite} is {@code (float) Math.rint}: the reference
 *       build selects {@code __builtin_roundevenf} and the whole {@code #else}
 *       with its three inline-assembly variants and its
 *       {@code __builtin_roundf} fallback is dropped, as is the
 *       {@code #include <fenv.h>} that only the dropped
 *       {@code feraiseexcept} needs.
 *   <li>{@code feraiseexcept(FE_INVALID)} is dropped, the JVM exposes no
 *       floating-point environment, and the NaN it accompanies is written as
 *       {@link Float#NaN}, where the C code calls
 *       {@code __builtin_nanf("inf")}; that is the same quiet NaN
 *       {@code 0x7fc00000}.
 *   <li>The three {@code CORE_MATH_SUPPORT_ERRNO} blocks are dropped, there is
 *       no {@code errno} in Java: the {@code EDOM} for {@code x = Inf}, the
 *       {@code ERANGE} of the half-integer overflow and the {@code ERANGE} of
 *       the underflow, together with the comment that lists the rounding
 *       modes.
 *   <li>{@code 1.0f/0.0f} becomes {@link Float#POSITIVE_INFINITY} and
 *       {@code -1.0f/0.0f} becomes {@link Float#NEGATIVE_INFINITY}.
 *   <li>{@code b32u32_u} becomes {@link Float#floatToRawIntBits}; the
 *       reassignment {@code ix.f = zf} is a second {@code floatToRawIntBits},
 *       and {@code ~0u>>1} is {@code ~0 >>> 1}.
 *   <li>{@code __builtin_expect} is dropped, the JIT profiles the branches
 *       itself.
 *   <li>{@code __builtin_copysignf} is {@link Math#copySign}, and the
 *       {@code int k = x4} conversion is {@code (int) x4}.
 *   <li>The rational function is evaluated in {@code double} as upstream
 *       writes it, and the C conversion of {@code r} back to {@code float} is
 *       the explicit {@code (float)} cast.
 * </ul>
 */
public final strictfp class TanPiF {
  private TanPiF() {
  }

  private static final double[] cn = {0x1.921fb54442d19p-1, -0x1.1f458b3e1f8d6p-2,
    0x1.68a34bd0b8f6ap-6, -0x1.e4866f7a25f99p-13};

  private static final double[] cd = {0x1p+0, -0x1.4b4b98d2df3a7p-1, 0x1.8e9926d2bb901p-4,
    -0x1.a6f77fd847eep-9};

  public static float compute(float x) {
    int ix = Float.floatToRawIntBits(x);
    int e = ix & (0xff << 23);
    if (e > (150 << 23)) { // |x| > 2^23
      if (e == (0xff << 23)) { // x = nan or inf
        if ((ix << 9) == 0) { // x = inf
          // the C source sets errno to EDOM and raises FE_INVALID here, and
          // returns __builtin_nanf("inf")
          return Float.NaN;
        }
        return x + x; // x = nan
      }
      return Math.copySign(0.0f, x);
    }
    float x4 = 4.0f*x, nx4 = (float) Math.rint(x4), dx4 = x4-nx4;
    float ni = (float) Math.rint(x), zf = x-ni;
    if (dx4 == 0.0f) { // 4*x integer
      int k = (int) x4;
      if ((k & 1) != 0) return Math.copySign(1.0f, zf); // x = 1/4 mod 1/2
      k &= 6;
      if (k == 0) return Math.copySign(0.0f, x); // x = 0 mod 2
      if (k == 4) return -Math.copySign(0.0f, x); // x = 1 mod 2
      // the C source sets errno to ERANGE here
      if (k == 2) return Float.POSITIVE_INFINITY; // 1.0f/0.0f in the C source
      // now necessarily k=6
      return Float.NEGATIVE_INFINITY; // -1.0f/0.0f in the C source
    }

    // the C source sets errno to ERANGE here (underflow) for
    // |x| <= 0x1.45f3p-128, Java has no errno

    ix = Float.floatToRawIntBits(zf);
    int a = ix & (~0 >>> 1);
    // x=0x1.267004p-2 is not correctly rounded for RNDZ/RNDD by the code below
    if (a == 0x3e933802) return Math.copySign(0x1.44cfbap+0f, zf) + Math.copySign(0x1p-25f, zf);
    // x=-0x1.e4cd0ap-14 is not correctly rounded for RNDU by the code below
    if (a == 0x38f26685)
      return Math.copySign((float) 0x1.7cc304p-12, zf) + Math.copySign(0x1p-37f, zf);
    double z = zf, z2 = z*z;
    double z4 = z2*z2, r = (z-z*z2)*((cn[0]+z2*cn[1])+z4*(cn[2]+z2*cn[3]))
      /(((cd[0]+z2*cd[1])+z4*(cd[2]+z2*cd[3]))*(0.25-z2));
    return (float) r;
  }
}
