/*
 * Correctly-rounded base-10 exponent function biased by 1 for binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/exp10m1/exp10m1f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_exp10m1f -> Exp10M1F.compute
 *
 * Copyright (c) 2022-2025 Alexei Sibidanov, Paul Zimmermann.
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
 * Correctly rounded {@code 10^x - 1} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_exp10m1f}.
 *
 * <p>The function is a single branch tree. {@code x < -7.52575} returns the
 * {@code q[1]} pair, {@code x > 38.5318} the {@code q[0]} pair,
 * {@code |x| < 0.1549/log(10)} evaluates one of the six polynomials
 * {@code cp1} .. {@code cp6} that are tuned to nested magnitude bands (with two
 * arguments named explicitly), the exact powers of ten are answered by the
 * {@code (ux<<11)==0} test, and the remaining arguments reduce with
 * {@code a = 16 log10(10) x} against the {@code tb} table and the degree-5
 * polynomial {@code c}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Double#doubleToRawLongBits}/{@link Double#longBitsToDouble};
 *       {@code ux}, {@code ax}, {@code t} and the {@code su} of the reduction
 *       are {@code int} or {@code long} bit patterns.
 *   <li>{@code __builtin_expect} is nothing and {@code __builtin_floor} is
 *       {@link Math#floor}; the {@code errno} assignments are dropped, together
 *       with the underflow block whose only other statement is a
 *       {@code __builtin_fabsf} test. The {@code #pragma STDC FENV_ACCESS ON}
 *       is dropped.
 *   <li>{@code static const} tables declared inside the function become
 *       fields, in the order of the C source: {@code c}, {@code tb}, {@code q},
 *       then the six {@code cp[]} arrays as {@code cp1} .. {@code cp6}. The
 *       {@code q} entries keep their {@code f} suffix, and the
 *       {@code const double iln10h}/{@code iln10l} of the entry point stay
 *       local variables, as in the C source.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, {@code uint32_t} shifts are
 *       arithmetic on {@code int}, and the {@code unsigned k = (ux>>21) - 0x1fc}
 *       of the exact powers of ten wraps, so it is compared unsigned as well;
 *       {@code (e + 0x3ffull)<<52} keeps the {@code long} type of {@code e}.
 *   <li>The {@code int64_t} locals {@code i}, {@code j} and {@code e} of the
 *       reduction are {@code long}, except that {@code j} is cast to
 *       {@code int} where it indexes {@code tb}.
 *   <li>Where the C code narrows a {@code double} to {@code float}, namely the
 *       {@code r *= z} of the small bands and {@code (s-1.0) + w*c0} of the last
 *       branch, the cast is explicit. The {@code 10.0f-1.0f} ... of the exact
 *       powers of ten keep their {@code f} suffix and stay {@code float}
 *       arithmetic.
 * </ul>
 */
public final strictfp class Exp10M1F {
  private Exp10M1F() {
  }

  private static final double[] c =
      {0x1.62e42fefa398bp-5, 0x1.ebfbdff84555ap-11, 0x1.c6b08d4ad86d3p-17,
       0x1.3b2ad1b1716a2p-23, 0x1.5d7472718ce9dp-30, 0x1.4a1d7f457ac56p-37};

  private static final double[] tb =
      {0x1p+0, 0x1.0b5586cf9890fp+0, 0x1.172b83c7d517bp+0, 0x1.2387a6e756238p+0,
       0x1.306fe0a31b715p+0, 0x1.3dea64c123422p+0, 0x1.4bfdad5362a27p+0, 0x1.5ab07dd485429p+0,
       0x1.6a09e667f3bcdp+0, 0x1.7a11473eb0187p+0, 0x1.8ace5422aa0dap+0, 0x1.9c49182a3f09p+0,
       0x1.ae89f995ad3adp+0, 0x1.c199bdd85529cp+0, 0x1.d5818dcfba487p+0, 0x1.ea4afa2a490dap+0};

  private static final float[][] q = {{0x1.fffffep127f, 0x1.fffffep127f}, {-1.0f, 0x1p-26f}};

  // the first static const double cp[] inside cr_exp10m1f()
  private static final double[] cp1 =
                    {0x1.26bb1bbb55515p+1, 0x1.53524c73cea69p+1, 0x1.0470595038cc2p+1, 0x1.2bd7609fe1561p+0};

  // the second static const double cp[] inside cr_exp10m1f()
  private static final double[] cp2 =
                  {0x1.26bb1bbb55516p+1, 0x1.53524c73ce6dbp+1, 0x1.0470591de3024p+1, 0x1.2bd76b79060e6p+0,
                   0x1.1429ffd3a963dp-1};

  // the third static const double cp[] inside cr_exp10m1f()
  private static final double[] cp3 =
                {0x1.26bb1bbb55516p+1, 0x1.53524c73cea67p+1, 0x1.0470591dc2953p+1, 0x1.2bd760a004d64p+0,
                 0x1.142a85da6f072p-1, 0x1.a7ed70725b00ep-3};

  // the fourth static const double cp[] inside cr_exp10m1f()
  private static final double[] cp4 =
              {0x1.26bb1bbb55516p+1, 0x1.53524c73ceadep+1, 0x1.0470591de2bb4p+1, 0x1.2bd76099a9d33p+0,
               0x1.1429ffd829b0bp-1, 0x1.a7f2a6a0f7dc8p-3, 0x1.16e4dfbce0f56p-4};

  // the fifth static const double cp[] inside cr_exp10m1f()
  private static final double[] cp5 =
            {0x1.26bb1bbb55515p+1, 0x1.53524c73cea6ap+1, 0x1.0470591de476p+1, 0x1.2bd7609fd4ee2p+0,
             0x1.1429ff70a9b48p-1, 0x1.a7ed71259ba5bp-3, 0x1.16f3004fb3ac1p-4, 0x1.4116b0388aa9fp-6};

  // the sixth static const double cp[] inside cr_exp10m1f()
  private static final double[] cp6 =
          {0x1.26bb1bbb55515p+1, 0x1.53524c73cea42p+1, 0x1.0470591de2d1dp+1, 0x1.2bd760a010a53p+0,
           0x1.1429ffd16170cp-1, 0x1.a7ed6b2a0d97fp-3, 0x1.16e4e37fa51e4p-4, 0x1.4147fe4c1676fp-6,
           0x1.4897c4b3e329ap-8};

  public static float compute(float x) {
    double iln10h = 0x1.a934f09p+1*16, iln10l = 0x1.e68dc57f2496p-29*16;
    int t = Float.floatToRawIntBits(x);
    double z = x;
    int ux = t, ax = ux&(~0>>>1);
    if(Integer.compareUnsigned(ux, 0xc0f0d2f1) > 0){ // x < -7.52575
      if(Integer.compareUnsigned(ax, 0xff<<23) > 0) return x + x; // nan
      // for x=-Inf, don't raise the inexact exception
      return (ux == 0xff800000) ? q[1][0] : q[1][0] + q[1][1];
    } else if(Integer.compareUnsigned(ax, 0x421a209a) > 0){  // x > 38.5318
      if(Integer.compareUnsigned(ax, 0xff<<23) >= 0) return x + x; // +Inf or NaN
      // the C source sets errno to ERANGE here on overflow
      return q[0][0] + q[0][1];
    } else if (Integer.compareUnsigned(ax, 0x3d89c604) < 0){ // |x| < 0.1549/log(10)
      double z2 = z*z, r;
      if (Integer.compareUnsigned(ax, 0x3d1622fb) < 0){ // |x| < 8.44e-2/log(10)
        if (Integer.compareUnsigned(ax, 0x3c8b76a3) < 0){ // |x| < 3.92e-2/log(10)
          if (Integer.compareUnsigned(ax, 0x3bcced04) < 0){ // |x| < 1.44e-2/log(10)
            if (Integer.compareUnsigned(ax, 0x3acf33eb) < 0){ // |x| < 3.64e-3/log(10)
              if (Integer.compareUnsigned(ax, 0x395a966b) < 0){ // |x| < 4.8e-4/log(10)
                if (Integer.compareUnsigned(ax, 0x36fe4a4b) < 0){ // |x| < 1.745e-5/log(10)
                  if (Integer.compareUnsigned(ax, 0x32407f39) < 0){ // |x| < 2.58e-8/log(10)
                    if (Integer.compareUnsigned(ax, 0x245e5bd9) < 0){ // |x|<0x1.bcb7b2p-55
                      /* for |x| <= 0x1.bcb7bp-128, exp10m1(x) underflows,
                         except for rounding away from zero; the C source sets
                         errno to ERANGE there */
                      r = 0x1.26bb1bbb55516p+1;
                    } else {
                      if (ux == 0x2c994b7b) return 0x1.60f974p-37f - 0x1p-90f;
                      r = 0x1.26bb1bbb55516p+1 + z * 0x1.53524c73cea69p+1;
                    }
                  } else {
                    if (ux == 0xb6fa215b) return -0x1.1ff87ep-16f + 0x1p-68f;
                    r = 0x1.26bb1bbb55516p+1 + z * (0x1.53524c73ea62fp+1 + z * 0x1.0470591de2c75p+1);
                  }
                } else {
                  r = (cp1[0] + z * cp1[1]) + z2 * (cp1[2] + z * cp1[3]);
                }
              } else {
                r = (cp2[0] + z * cp2[1]) + z2 * (cp2[2] + z * (cp2[3] + z * cp2[4]));
              }
            } else {
              r = (cp3[0] + z * cp3[1]) + z2 * ((cp3[2] + z * cp3[3]) + z2 * (cp3[4] + z * cp3[5]));
            }
          } else {
            r = (cp4[0] + z * cp4[1]) + z2 * ((cp4[2] + z * cp4[3]) + z2 * (cp4[4] + z * (cp4[5] + z * cp4[6])));
          }
        } else {
          r = ((cp5[0] + z * cp5[1]) + z2 * (cp5[2] + z * cp5[3])) + (z2*z2) * ((cp5[4] + z * cp5[5]) + z2 * (cp5[6] + z * cp5[7]));
        }
      } else {
        r = ((cp6[0] + z * cp6[1]) + z2 * (cp6[2] + z * cp6[3])) + (z2*z2) * ((cp6[4] + z * cp6[5]) + z2 * (cp6[6] + z * (cp6[7] + z *cp6[8])));
      }
      r *= z;
      return (float)r;
    } else { // -7.52575 < x < -0.1549/log(10) or 0.1549/log(10) < x < 38.5318
      if((ux<<11)==0){
        int k = (ux>>>21) - 0x1fc;
        if(Integer.compareUnsigned(k, 0xb) <= 0){
          if(k== 0) return 10.0f-1.0f;
          if(k== 4) return 100.0f-1.0f;
          if(k== 6) return 1000.0f-1.0f;
          if(k== 8) return 10000.0f-1.0f;
          if(k== 9) return 100000.0f-1.0f;
          if(k==10) return 1000000.0f-1.0f;
          if(k==11) return 10000000.0f-1.0f;
        }
      }
      double a = iln10h*z, ia = Math.floor(a), h = (a - ia) + iln10l*z;
      long i = (long)ia, j = i&0xf, e = i - j;
      e >>= 4;
      double s = tb[(int)j];
      long su = (e + 0x3ffL)<<52;
      s *= Double.longBitsToDouble(su);
      double h2 = h*h;
      double c0 = c[0] + h*c[1];
      double c2 = c[2] + h*c[3];
      double c4 = c[4] + h*c[5];
      c0 += h2*(c2 + h2*c4);
      double w = s*h;
      return (float)((s-1.0) + w*c0);
    }
  }
}
