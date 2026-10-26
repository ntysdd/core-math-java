/*
 * Correctly rounded e^x-1 function for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/expm1/expm1f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_expm1f -> Expm1F.compute
 *
 * Copyright (c) 2024 Alexei Sibidanov.
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
 * Correctly rounded {@code e^x - 1} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_expm1f}.
 *
 * <p>A degree-7 polynomial in {@code x} below 0.15625, then the reduction of the
 * argument against the {@code td} table of 32 entries at the step 1/32 with the
 * {@code c} polynomial and, when the two bounds disagree, the {@code ch}
 * polynomial; {@code x < -17.32} answers {@code -1 + 2^-26} directly.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code roundeven_finite} is {@link Math#rint}. The {@code #if} that
 *       defines it selects {@code __builtin_roundeven} for the reference build,
 *       so the software fallback of the {@code #else} branch is dropped.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; the bit patterns {@code u} and
 *       {@code sv} of the reduction are carried as {@code long}.
 *   <li>{@code (uint64_t *)((void*)td)}, the bit view of the {@code double}
 *       table, is {@link Double#doubleToRawLongBits} applied to the entry; the
 *       table keeps the spelling of the C source.
 *   <li>{@code static const} tables declared inside the function become fields:
 *       {@code c}, {@code ch}, {@code td} and {@code b}.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma} with {@code float}
 *       arguments, {@code __builtin_fabsf} is {@link Math#abs}, and
 *       {@code __builtin_expect} is dropped.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned} and {@code uint32_t} shifts are
 *       {@code >>>}, since {@code ax} and {@code ux} keep the sign bit.
 *   <li>The {@code errno} assignments are dropped, since no caller can observe
 *       {@code errno}.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code ub} and {@code lb}) the cast is explicit.
 * </ul>
 */
public final strictfp class Expm1F {
  private Expm1F() {
  }

  private static final double[] c =
      {1, 0x1.62e42fef4c4e7p-6, 0x1.ebfd1b232f475p-13, 0x1.c6b19384ecd93p-20};

  private static final double[] ch =
      {0x1.62e42fefa39efp-6, 0x1.ebfbdff82c58fp-13, 0x1.c6b08d702e0edp-20, 0x1.3b2ab6fb92e5ep-27,
       0x1.5d886e6d54203p-35, 0x1.430976b8ce6efp-43};

  private static final double[] td =
      {0x1p+0, 0x1.059b0d3158574p+0, 0x1.0b5586cf9890fp+0, 0x1.11301d0125b51p+0,
       0x1.172b83c7d517bp+0, 0x1.1d4873168b9aap+0, 0x1.2387a6e756238p+0, 0x1.29e9df51fdee1p+0,
       0x1.306fe0a31b715p+0, 0x1.371a7373aa9cbp+0, 0x1.3dea64c123422p+0, 0x1.44e086061892dp+0,
       0x1.4bfdad5362a27p+0, 0x1.5342b569d4f82p+0, 0x1.5ab07dd485429p+0, 0x1.6247eb03a5585p+0,
       0x1.6a09e667f3bcdp+0, 0x1.71f75e8ec5f74p+0, 0x1.7a11473eb0187p+0, 0x1.82589994cce13p+0,
       0x1.8ace5422aa0dbp+0, 0x1.93737b0cdc5e5p+0, 0x1.9c49182a3f09p+0, 0x1.a5503b23e255dp+0,
       0x1.ae89f995ad3adp+0, 0x1.b7f76f2fb5e47p+0, 0x1.c199bdd85529cp+0, 0x1.cb720dcef9069p+0,
       0x1.d5818dcfba487p+0, 0x1.dfc97337b9b5fp+0, 0x1.ea4afa2a490dap+0, 0x1.f50765b6e454p+0};

  private static final double[] b =
        {0x1.fffffffffffc2p-2, 0x1.55555555555fep-3, 0x1.555555559767fp-5, 0x1.1111111098dc1p-7,
         0x1.6c16bca988aa9p-10, 0x1.a01a07658483fp-13, 0x1.a05b04d2c3503p-16, 0x1.71de3a960b5e3p-19};

  public static float compute(float x) {
    double iln2 = 0x1.71547652b82fep+5, big = 0x1.8p52;
    int ux = Float.floatToRawIntBits(x), ax = ux<<1;
    double z = x;
    if(Integer.compareUnsigned(ax, 0x7c400000)<0){ // |x| < 0.15625
      if(Integer.compareUnsigned(ax, 0x676a09e8)<0){ // |x| < 0x1.6a09e8p-24
        if(ax==0) return x; // x = +-0
        float res = Math.fma(Math.abs(x),0x1p-25f,x);
        // expm1(x) underflows for |x| < 2^-126, and for x=-0x1p-126 and RNDZ
        // the C source sets errno to ERANGE here on underflow
        return res;
      }
      double z2 = z*z, z4 = z2*z2;
      double r = z + z2*((b[0]+z*b[1]) + z2*(b[2]+z*b[3]) + z4*((b[4]+z*b[5]) + z2*(b[6]+z*b[7])));
      return (float)r;
    }
    if(Integer.compareUnsigned(ax, 0x8562e430)>=0){  // |x| >= 0x1.62e43p+6 ~ 88.72
      if(Integer.compareUnsigned(ax, 0xff<<24)>0) return x + x; // nan
      if((ux>>>31) != 0){ // x < 0
        if(ax==(0xff<<24)) return -1.0f;
        /* The expression -1.0f + 0x1p-26f should be evaluated at run
           time, with the current rounding mode, and not constant folded at
           compile time. See the comment about -frounding-math in README.
           Also, the expression -1.0f + 0x1p-26f is valid on a larger range:
           for x < -0x1.154244p+4 for rndn, and for x < -0x1.0a2b22p+4 for
           directed roundings. But this would need a different test for
           x > 0 and x < 0. */
        return -1.0f + 0x1p-26f;
      }
      if(ax==(0xff<<24)) return x * x; // +/-inf
      // the C source sets errno to ERANGE here on overflow
      return 0x1.fffffep127f * x;
    }
    double a = iln2*z, ia = Math.rint(a), h = a - ia, h2 = h*h;
    long u = Double.doubleToRawLongBits(ia + big);
    double c2 = c[2] + h*c[3], c0 = c[0] + h*c[1];
    long sv = Double.doubleToRawLongBits(td[(int)(u&0x1f)]) + ((u>>>5)<<52);
    double svf = Double.longBitsToDouble(sv);
    double r = (c0 + h2*c2)*svf - 1.0;
    float ub = (float)r, lb = (float)(r - svf*0x1.3b3p-33);
    if(ub != lb){
      if(Integer.compareUnsigned(ux, 0xc18aa123)>0) // x < -17.32
        return -1.0f + 0x1p-26f;
      double iln2h = 0x1.7154765p+5, iln2l = 0x1.5c17f0bbbe88p-26;
      double s = svf;
      h = (iln2h*z - ia) + iln2l*z;
      h2 = h*h;
      double w = s*h;
      r = (s-1) + w*((ch[0] + h*ch[1]) + h2*((ch[2] + h*ch[3]) + h2*(ch[4] + h*ch[5])));
      ub = (float)r;
    }
    return ub;
  }
}
