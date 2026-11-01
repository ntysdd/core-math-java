/*
 * Correctly rounded true gamma function for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/tgamma/tgammaf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_tgammaf -> TgammaF.compute
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
 * Correctly rounded {@code tgamma} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_tgammaf}.
 *
 * <p>The file computes in {@code double}: {@code |x| < 2^-18} uses the
 * three-term expansion of {@code 1/gamma(x)} around the pole at zero, the
 * integer arguments are a plain product, and the remaining range is a
 * degree-15 polynomial in {@code x - 3.5}, corrected by the product of the
 * factors down to the nearest half-integer. Two tables of exceptional
 * arguments, {@code tb} for the tiny branch and the same {@code tb} for the
 * main one, replace results that fall within a few ulp of a rounding boundary;
 * the test is on the low 28 bits of the {@code double} before the conversion to
 * {@code float}.
 *
 * <p>Deviations from the C source, all of them forced by the language or by
 * the mapping table in AGENTS.md:
 * <ul>
 *   <li>{@code roundeven_finite} is {@link Math#rint}, since the reference
 *       build defines it as {@code __builtin_roundeven}.
 *   <li>{@code __builtin_fma} is {@link Math#fma}, {@code __builtin_fabs} is
 *       {@link Math#abs}, {@code __builtin_floorf} is {@code (float)}
 *       {@link Math#floor}, {@code __builtin_copysign} is
 *       {@link Math#copySign}, and {@code __builtin_expect (c, n)} is
 *       {@code c}.
 *   <li>The {@code errno} assignments are dropped, together with the
 *       {@code #ifdef CORE_MATH_SUPPORT_ERRNO} blocks that contain nothing
 *       else. {@code #pragma STDC FENV_ACCESS ON} is dropped with them.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become {@code int}
 *       and {@code long} bit patterns through
 *       {@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}.
 *   <li>The {@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, and the sign test {@code t.u>>31}
 *       is {@code u < 0}, the same test on the signed {@code int}.
 *   <li>{@code x / x} for {@code x = -Infinity} and {@code 0.0f / 0.0f} for
 *       the negative integers are {@link Float#NaN}; Java has no
 *       floating-point exceptions to raise. The {@code 0x1p127f * 0x1p127f}
 *       of the overflow case is kept, since Java overflows to
 *       {@code +Infinity} on the same operands.
 *   <li>The {@code struct {b32u32_u x; float f, df;} tb[]} becomes the three
 *       columns {@code tbX}, {@code tbF} and {@code tbDf}, since both the bit
 *       comparison and the sum read every row; the rows whose {@code x} member
 *       the C source initialises through {@code .u} carry the same binary32
 *       value as a Java hex literal. {@code sizeof(tb)/sizeof(tb[0])} is
 *       {@code tbX.length}.
 *   <li>The {@code volatile double d} of the tiny branch is a plain
 *       {@code double}: Java has no reason to keep it in a register against
 *       the exception flags, which it does not have.
 *   <li>Every narrowing the C source performs implicitly carries the explicit
 *       {@code (float)} cast: {@code float r = f}, {@code float r = f} of the
 *       main branch, {@code float fx = __builtin_floorf (x)} and
 *       {@code return t0} of the integer branch.
 *   <li>{@code INT32_MIN} is {@link Integer#MIN_VALUE} and the {@code int}
 *       truncations ({@code int k = fx}, {@code int jm = fabs(i)}) carry the
 *       explicit cast that the C conversion does implicitly.
 * </ul>
 *
 * <p>The {@code if (x == 0.0f)} of the integer branch is kept exactly as the
 * C source has it even though it cannot be taken: the {@code |x| < 2^-18}
 * branch returns {@code 1/x} for both zeros before the integer branch is
 * reached. The instrumentation run described below hit it zero times.
 *
 * <p>Verified against a local build of the C entry point (gcc 13.2.0,
 * {@code -O2 -ffp-contract=off -mfma}): 5,000,000 random binary32 inputs,
 * 5,000,000 random binary64 inputs through {@code Tgamma}, the whole
 * {@code tgamma.wc} (545,538 lines), and 5,326 binary32 plus 21,903 binary64
 * targeted inputs around every comparison threshold and every {@code tb}
 * argument; no mismatch. A counter copy hit every other arm, including both
 * {@code tb} lookups and the {@code 0x1p127f * 0x1p127f} overflow.
 */
public strictfp class TgammaF {

  private TgammaF() {
  }

  // the x member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbX = {
      0x1.bd0d52p-48f, 0x1.c0a8eap-48f, -0x1.77df66p-19f, 0x1.f76aep-7f, 0x1.d10da2p+4f,
      -0x1.cfa2eep+1f, -0x1.33b462p-4f, -0x1.a988b4p-1f, 0x1.dceffcp+4f, 0x1.0874c8p+0f};

  // the f member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbF = {
      0x1.268266p+47f, 0x1.242422p+47f, -0x1.5cb6e4p+18f, 0x1.021d9p+6f, 0x1.33136ap+98f,
      0x1.f6850cp-3f, -0x1.befe66p+3f, -0x1.a6b4ecp+2f, 0x1.d3631cp+101f, 0x1.f6c638p-1f};

  // the df member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbDf = {
      0x1p22f, 0x1p22f, 0x1p-7f, 0x1p-19f, 0x1p73f,
      0x1p-28f, -0x1p-22f, 0x1p-23f, -0x1p-76f, 0x1p-26f};

  // static const float sgn[2]
  /* For x < -42, x non-integer, |gamma(x)| < 2^-151.  */
  private static final float[] sgn = {0x1p-127f, -0x1p-127f};

  private static final double[] c =
      {0x1.c9a76be577123p+0, 0x1.8f2754ddcf90dp+0, 0x1.0d1191949419bp+0, 0x1.e1f42cf0ae4a1p-2,
       0x1.82b358a3ab638p-3, 0x1.e1f2b30cd907bp-5, 0x1.240f6d4071bd8p-6, 0x1.1522c9f3cd012p-8,
       0x1.1fd0051a0525bp-10, 0x1.9808a8b96c37ep-13, 0x1.b3f78e01152b5p-15, 0x1.49c85a7e1fd04p-18,
       0x1.471ca49184475p-19, -0x1.368f0b7ed9e36p-23, 0x1.882222f9049efp-23, -0x1.a69ed2042842cp-25};

  public static float compute(float x) {
    int u = Float.floatToRawIntBits(x);
    int ax = u<<1;
    if(Integer.compareUnsigned(ax, 0xff<<24) >= 0){ /* x=NaN or +/-Inf */
      if(ax==(0xff<<24)){ /* x=+/-Inf */
        if(u < 0){ /* x=-Inf */
          // the C source sets errno to EDOM here
          return Float.NaN; // the C source computes x / x
        }
        return x; /* x=+Inf */
      }
      return x + x; /* x=NaN, where x+x ensures the "Invalid operation"
                       exception is set if x is sNaN, and it yields a qNaN */
    }
    double z = x;
    if(Integer.compareUnsigned(ax, 0x6d000000) < 0){ /* |x| < 0x1p-18 */
      double d = (0x1.fa658c23b1578p-1 - 0x1.d0a118f324b63p-1*z)*z - 0x1.2788cfc6fb619p-1;
      double f = 1.0/z + d;
      float r = (float) f;
      /* tgamma(x) overflows for:
       * 0 <= x < 0x1p-128 whatever the rounding mode
       * x = 0x1p-128 and rounding to nearest or away from zero
         (in which case the result is +Inf)
       * -0x1p-128 <= x <= 0 whatever the rounding mode
       */
      long rt = Double.doubleToRawLongBits(f);
      if(((rt+2)&0xfffffff) < 4){
        for(int i=0;i<tbX.length;i++)
          if(u==Float.floatToRawIntBits(tbX[i])) return tbF[i] + tbDf[i];
      }
      return r;
    }
    float fx = (float) Math.floor(x);
    if(x >= 0x1.18522p+5f){
      /* The C standard says that if the function overflows,
         errno is set to ERANGE. */
      return 0x1p127f * 0x1p127f;
    }
    /* compute k only after the overflow check, otherwise the cast to integer
       might overflow */
    int k;
    if(x <= -0x1p+31f) {
      k = Integer.MIN_VALUE;
    }
    else {
      k = (int) fx;
    }
    if(fx==x){ /* x is integer */
      // this arm cannot be taken: |0| < 0x1p-18, so the branch above has
      // already returned 1/x; the C source keeps it and so does the port
      if(x == 0.0f){
        return 1.0f/x;
      }
      if(x < 0.0f) {
        // the C source sets errno to EDOM here
        return Float.NaN; // the C source computes 0.0f / 0.0f
      }
      double t0 = 1, x0 = 1;
      for(int i=1; i<k; i++, x0 += 1.0) t0 *= x0;
      return (float) t0;
    }
    if(x<-42.0f){ /* negative non-integer */
      /* For x < -42, x non-integer, |gamma(x)| < 2^-151.  */
      /* The C standard says that if the function underflows,
         errno is set to ERANGE. */
      return 0x1p-127f * sgn[k&1];
    }

    double m = z - 0x1.7p+1, i = Math.rint(m), step = Math.copySign(1.0,i);
    double d = m - i, d2 = d*d, d4 = d2*d2, d8 = d4*d4;
    double f = (c[0] + d*c[1]) + d2*(c[2] + d*c[3]) + d4*((c[4] + d*c[5]) + d2*(c[6] + d*c[7]))
      + d8*((c[8] + d*c[9]) + d2*(c[10] + d*c[11]) + d4*((c[12] + d*c[13]) + d2*(c[14] + d*c[15])));
    int jm = (int) Math.abs(i);
    double w = 1;
    if(jm != 0){
      z -= 0.5 + step*0.5;
      w = z;
      for(int j=jm-1; j != 0; j--) {z -= step; w *= z;}
    }
    if(i<=-0.5) w = 1/w;
    f *= w;
    long rt = Double.doubleToRawLongBits(f);
    float r = (float) f;
    /* Deal with exceptional cases.  */
    if(((rt+2)&0xfffffff) < 8){
      for(int j=0;j<tbX.length;j++) {
        if(u==Float.floatToRawIntBits(tbX[j])) return tbF[j] + tbDf[j];
      }
    }
    return r;
  }
}
