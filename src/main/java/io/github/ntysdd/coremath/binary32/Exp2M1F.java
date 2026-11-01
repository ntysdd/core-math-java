/*
 * Correctly-rounded base-2 exponent function biased by 1 for binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/exp2m1/exp2m1f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_exp2m1f -> Exp2M1F.compute
 *
 * Copyright (c) 2022-2025 Alexei Sibidanov.
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
 * Correctly rounded {@code 2^x - 1} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_exp2m1f}.
 *
 * <p>The function is a single branch tree. {@code x <= -25} returns the
 * {@code q[2]} pair, {@code x >= 128} the {@code q[1]} or {@code q[0]} pair,
 * {@code |x| < 8.44e-2/log(2)} evaluates one of the seven polynomials
 * {@code c1} .. {@code c7} that are tuned to nested magnitude bands, and the
 * remaining arguments reduce with {@code a = 16x} against the {@code tb} table
 * of {@code 2^(j/16)} halves and the degree-5 polynomial {@code c7}.
 *
 * <p>Two arguments of the small bands are named explicitly and returned with a
 * two-term sum.
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
 *       fields, in the order of the C source: {@code q}, then the seven
 *       {@code c[]} arrays as {@code c1} .. {@code c7}, then {@code tb}. The
 *       {@code q} entries keep their {@code f} suffix.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, {@code uint32_t} shifts are
 *       arithmetic on {@code int}, and {@code (e + 0x3ffull)<<52} keeps the
 *       {@code long} type of {@code e}.
 *   <li>The {@code int64_t} locals {@code i}, {@code j} and {@code e} of the
 *       reduction are {@code long}, except that {@code j} is cast to
 *       {@code int} where it indexes {@code tb}.
 *   <li>The {@code int special} flag that selects between {@code q[0]} and
 *       {@code q[1]} stays an {@code int}, with a {@code ? 1 : 0} ternary,
 *       because Java's relational operators yield {@code boolean} and the flag
 *       indexes {@code q}. In round-to-nearest-even {@code q[1][0] + q[1][1]}
 *       overflows to {@code +Infinity}, so {@code special} is always 0 and the
 *       {@code q[1]} row is never selected; the C source computes that at run
 *       time instead of testing a rounding mode, so it is kept as it stands.
 *   <li>Where the C code narrows a {@code double} to {@code float} the cast is
 *       explicit: the two named exceptional returns, the {@code r *= z} of the
 *       small bands, and {@code (s-1.0) + w*c0} of the last branch.
 * </ul>
 */
public final strictfp class Exp2M1F {
  private Exp2M1F() {
  }

  private static final float[][] q = {{0x1.fffffep127f, 0x1.fffffep127f},
                                 {0x1.fffffep127f, 0x1p+103f},
                                 {-1.0f, 0x1p-26f}};

  // the first static const double c[] inside cr_exp2m1f()
  private static final double[] c1 =
                    {0x1.62e42fefa39efp-1, 0x1.ebfbdff8548fdp-3, 0x1.c6b08d704a06dp-5};

  // the second static const double c[] inside cr_exp2m1f()
  private static final double[] c2 =
                  {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c58fp-3, 0x1.c6b08dc82b347p-5, 0x1.3b2ab6fbad172p-7};

  // the third static const double c[] inside cr_exp2m1f()
  private static final double[] c3 =
                {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c068p-3, 0x1.c6b08d704a6dcp-5, 0x1.3b2ac262c3eedp-7,
                 0x1.5d87fe7af779ap-10};

  // the fourth static const double c[] inside cr_exp2m1f()
  private static final double[] c4 =
              {0x1.62e42fefa39fp-1, 0x1.ebfbdff82c58dp-3, 0x1.c6b08d7011d13p-5, 0x1.3b2ab6fbd267dp-7,
               0x1.5d88a81cea49ep-10, 0x1.430912ea9b963p-13};

  // the fifth static const double c[] inside cr_exp2m1f()
  private static final double[] c5 =
            {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c639p-3, 0x1.c6b08d7049f1cp-5, 0x1.3b2ab6f5243bdp-7,
             0x1.5d87fe80a9e6cp-10, 0x1.430d0b9257fa8p-13, 0x1.ffcbfc4cf0952p-17};

  // the sixth static const double c[] inside cr_exp2m1f()
  private static final double[] c6 =
          {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c591p-3, 0x1.c6b08d704cf6bp-5, 0x1.3b2ab6fba00cep-7,
           0x1.5d87fdfdaadb4p-10, 0x1.4309137333066p-13, 0x1.ffe5e90daf7ddp-17, 0x1.62c0220eed731p-20};

  // the seventh static const double c[] inside cr_exp2m1f()
  private static final double[] c7 =
        {0x1.62e42fefa398bp-5, 0x1.ebfbdff84555ap-11, 0x1.c6b08d4ad86d3p-17,
         0x1.3b2ad1b1716a2p-23, 0x1.5d7472718ce9dp-30, 0x1.4a1d7f457ac56p-37};

  private static final double[] tb =
        {0x1p+0, 0x1.0b5586cf9890fp+0, 0x1.172b83c7d517bp+0, 0x1.2387a6e756238p+0,
         0x1.306fe0a31b715p+0, 0x1.3dea64c123422p+0, 0x1.4bfdad5362a27p+0, 0x1.5ab07dd485429p+0,
         0x1.6a09e667f3bcdp+0, 0x1.7a11473eb0187p+0, 0x1.8ace5422aa0dap+0, 0x1.9c49182a3f09p+0,
         0x1.ae89f995ad3adp+0, 0x1.c199bdd85529cp+0, 0x1.d5818dcfba487p+0, 0x1.ea4afa2a490dap+0};

  public static float compute(float x) {
    int t = Float.floatToRawIntBits(x);
    double z = x;
    int ux = t, ax = ux&(~0>>>1);
    if(Integer.compareUnsigned(ux, 0xc1c80000) >= 0){ // x <= -25
      if(Integer.compareUnsigned(ax, 0xff<<23) > 0) return x + x; // nan
      // avoid spurious inexact exception for -Inf
      return (ux == 0xff800000) ? q[2][0] : q[2][0] + q[2][1];
    } else if(Integer.compareUnsigned(ax, 0x43000000) >= 0){ // x >= 128
      if(Integer.compareUnsigned(ax, 0xff<<23) > 0) return x + x; // nan
      // for x=128 and rounding downward or to zero, there is no overflow
      int special = (x == 128.0f && (q[1][0] + q[1][1] == q[1][0])) ? 1 : 0;
      // the C source sets errno to ERANGE here when !special
      // avoid spurious inexact exception for +Inf
      return (ux == 0x7f800000) ? x : q[special][0] + q[special][1];
    } else if (Integer.compareUnsigned(ax, 0x3df95f1f) < 0){ // |x| < 8.44e-2/log(2)
      double z2 = z*z, r;
      if (Integer.compareUnsigned(ax, 0x3d67a4cc) < 0){ // |x| < 3.92e-2/log(2)
        if (Integer.compareUnsigned(ax, 0x3caa2fee) < 0){ // |x| < 1.44e-2/log(2)
          if (Integer.compareUnsigned(ax, 0x3bac1405) < 0){ // |x| < 3.64e-3/log(2)
            if (Integer.compareUnsigned(ax, 0x3a358876) < 0){ // |x| < 4.8e-4/log(2)
              if (Integer.compareUnsigned(ax, 0x37d32ef6) < 0){ // |x| < 1.745e-5/log(2)
                if (Integer.compareUnsigned(ax, 0x331fdd82) < 0){ // |x| < 2.58e-8/log(2)
                  if (Integer.compareUnsigned(ax, 0x2538aa3b) < 0){ // |x| < 0x1.715476p-53
                    /* exp2m1(x) underflows: for |x| <= 0x1.715476p-126 for
                       rounding toward zero, for |x| <= 0x1.715474p-126 for
                       rounding to nearest/away; the C source sets errno to
                       ERANGE there */
                    r = 0x1.62e42fefa39efp-1;
                  } else {
                    r = 0x1.62e42fefa39fp-1 + z * 0x1.ebfbdff82c58fp-3;
                  }
                } else {
                  if (ux == 0xb3d85005) return (float)(-0x1.2bdf76p-24 - 0x1.8p-77);
                  if (ux == 0x3338428d) return (float)(0x1.fee08ap-26 + 0x1p-80);
                  r = c1[0] + z * (c1[1] + z * c1[2]);
                }
              } else {
                if (ux == 0x388bca4f) return (float)(0x1.839702p-15 - 0x1.8p-68);
                r = (c2[0] + z * c2[1]) + z2 * (c2[2] + z * c2[3]);
              }
            } else {
              r = (c3[0] + z * c3[1]) + z2 * (c3[2] + z * (c3[3] + z * c3[4]));
            }
          } else {
            r = (c4[0] + z * c4[1]) + z2 * ((c4[2] + z * c4[3]) + z2 * (c4[4] + z * c4[5]));
          }
        } else {
          r = (c5[0] + z * c5[1]) + z2 * ((c5[2] + z * c5[3])
              + z2 * (c5[4] + z * (c5[5] + z * c5[6])));
        }
      } else {
        r = ((c6[0] + z * c6[1]) + z2 * (c6[2] + z * c6[3]))
            + (z2*z2) * ((c6[4] + z * c6[5]) + z2 * (c6[6] + z * c6[7]));
      }
      r *= z;
      return (float)r;
    } else {
      double a = 16.0*z, ia = Math.floor(a), h = a - ia, h2 = h*h;
      long i = (long)ia, j = i&0xf, e = i - j;
      e >>= 4;
      double s = tb[(int)j];
      long su = (e + 0x3ffL)<<52;
      s *= Double.longBitsToDouble(su);
      double c0 = c7[0] + h*c7[1];
      double c2 = c7[2] + h*c7[3];
      double c4 = c7[4] + h*c7[5];
      c0 += h2*(c2 + h2*c4);
      double w = s*h;
      return (float)((s-1.0) + w*c0);
    }
  }
}
