/*
 * Correctly rounded reciprocal square root of a binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/rsqrt/rsqrtf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_rsqrtf -> RsqrtF.compute
 *
 * Copyright (c) 2025 Paul Zimmermann
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
 * Correctly rounded {@code 1/sqrt(x)} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_rsqrtf}.
 *
 * <p>One expression, {@code sqrt(x)*(c/x)} in double precision, where
 * {@code c} is 1 except for the three argument patterns for which the plain
 * expression is one ulp off in the reference build; those are corrected with a
 * factor just below 1. The special cases are the ones of the binary64 overload.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} becomes {@link Float#floatToRawIntBits}. {@code ix.u}
 *       is a {@code uint32_t}, so {@code ix.u >= 0xff<<23} must not be written
 *       as a signed comparison: with the sign bit set, a negative {@code int}
 *       is below {@code 0xff<<23} in Java and above it in C. Both tests go
 *       through {@link Integer#compareUnsigned}, and the shifts that can move
 *       bit 31 out are {@code >>>}.
 *   <li>Every {@code CORE_MATH_SUPPORT_ERRNO} assignment is dropped, there is
 *       no {@code errno} in Java, and {@code feraiseexcept (FE_INVALID)} goes
 *       with it; {@code __builtin_nanf ("<0")} is {@link Float#NaN}.
 *   <li>{@code __builtin_expect} is dropped, the JIT profiles the branches
 *       itself, and {@code __builtin_sqrt} is {@link Math#sqrt}.
 *   <li>The {@code #ifdef __INTEL_CLANG_COMPILER} block, which forwards
 *       {@code rsqrtf} to the compiler's {@code invsqrtf}, is an unbuilt
 *       {@code #if} variant and is dropped.
 *   <li>The {@code double} result of {@code sq*(c/xd)} is converted with the
 *       explicit {@code (float)} cast, as the C return does.
 * </ul>
 */
public final strictfp class RsqrtF {
  private RsqrtF() {
  }

  public static float compute(float x) {
    double xd = x;
    int ix = Float.floatToRawIntBits(x);
    if (Integer.compareUnsigned(ix, 0xff << 23) >= 0 || ix == 0) {
      if ((ix << 1) == 0)
      {
        // the C source sets errno to ERANGE here (pole error)
        return 1.0f/x; // +/-0
      }
      if ((ix >>> 31) != 0) {
        ix &= ~0 >>> 1;
        if (Integer.compareUnsigned(ix, 0xff << 23) > 0) return x + x; // nan
        // the C source sets errno to EDOM here and raises FE_INVALID
        return Float.NaN; // __builtin_nanf("<0") in the C source
      }
      if ((ix << 9) == 0) return 0.0f;
      return x + x; // nan
    }
    double c = 1.0, sq = Math.sqrt(xd);
    int m = ix << 8;
    if (ix == 0x2f7e2a || m == 0xbdf8a800 || m == 0x55b7bd00) {
      /* The expression 1/xd*sqrt(xd) fails to return the correct rounding for
         x=0x1.7bf15p-128*2^(2k) and x=0x1.ab6f7ap-125*2^(2k) with k>=0, for rndu,
         where the returned value is 1 ulp too large. We multiply by a correcting
         factor just below 1, which guarantees correct rounding in these cases. */
      c = 0x1.ffffffffffffep-1;
    }
    return (float) (sq*(c/xd));
  }
}
