/*
 * Correctly-rounded hyperbolic sine function for binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/sinh/sinhf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_sinhf -> SinhF.compute
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
 * Correctly rounded hyperbolic sine of a binary32 value, the translation of the
 * CORE-MATH {@code cr_sinhf}.
 *
 * <p>Small arguments use a polynomial in {@code x^2}, then {@code |x|} is
 * reduced modulo {@code log(2)/2} by the {@code tb} table of exponent halves and
 * evaluated with the {@code c} and {@code ch} polynomials, the second one only
 * when the two bounds disagree.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code roundeven_finite} is {@link Math#rint}. The {@code #if} that
 *       defines it selects {@code __builtin_roundeven} for the reference build,
 *       so the software fallback of the {@code #else} branch, with its inline
 *       assembly and its {@code __builtin_round} case analysis, is dropped.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; {@code sp} and {@code sm} are carried
 *       as {@code long} bit patterns and converted at each read.
 *   <li>{@code static const} tables declared inside {@code cr_sinhf} become
 *       fields: {@code c}, {@code ch}, {@code tb} and {@code cp}.
 *   <li>{@code st} is an array of an anonymous struct whose first member is the
 *       union {@code {float arg; uint32_t uarg;}}, followed by the two floats
 *       {@code rh} and {@code rl}. Java has no anonymous union, and only
 *       {@code uarg} is ever read, so the three columns become three
 *       one-element arrays, {@code stUarg}, {@code stRh} and {@code stRl}, with
 *       the {@code float} columns keeping the C literals plus an {@code f}
 *       suffix so that they round like the C initializers do.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma} with {@code float}
 *       arguments, and {@code __builtin_fabsf}, {@code __builtin_fabs} and
 *       {@code __builtin_copysignf} are {@link Math#abs} and
 *       {@link Math#copySign}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}; {@code uint64_t} arithmetic
 *       ({@code (uint64_t)(jp>>5)<<52}) is the same wrapping arithmetic on
 *       {@code long}.
 *   <li>The {@code errno} assignments are dropped, since no caller can observe
 *       {@code errno}.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code ub}, {@code lb} and the polynomial result of the
 *       {@code |x| < 0.125} branch) the cast is explicit.
 * </ul>
 */
public final strictfp class SinhF {
  private SinhF() {
  }

  // static const struct {union{float arg; uint32_t uarg;}; float rh, rl;} st[]
  // inside cr_sinhf() in the C source, one array per column; the arg member of
  // the union is never read
  private static final int[] stUarg = {0x74250bfe};
  private static final float[] stRh = {0x1.250bfep-11f};
  private static final float[] stRl = {0x1p-36f};

  private static final double[] c =
      {1, 0x1.62e42fef4c4e7p-6, 0x1.ebfd1b232f475p-13, 0x1.c6b19384ecd93p-20};

  private static final double[] ch =
      {1, 0x1.62e42fefa39efp-6, 0x1.ebfbdff82c58fp-13, 0x1.c6b08d702e0edp-20, 0x1.3b2ab6fb92e5ep-27,
       0x1.5d886e6d54203p-35, 0x1.430976b8ce6efp-43};

  private static final long[] tb =
      {0x3fe0000000000000L, 0x3fe059b0d3158574L, 0x3fe0b5586cf9890fL, 0x3fe11301d0125b51L,
       0x3fe172b83c7d517bL, 0x3fe1d4873168b9aaL, 0x3fe2387a6e756238L, 0x3fe29e9df51fdee1L,
       0x3fe306fe0a31b715L, 0x3fe371a7373aa9cbL, 0x3fe3dea64c123422L, 0x3fe44e086061892dL,
       0x3fe4bfdad5362a27L, 0x3fe5342b569d4f82L, 0x3fe5ab07dd485429L, 0x3fe6247eb03a5585L,
       0x3fe6a09e667f3bcdL, 0x3fe71f75e8ec5f74L, 0x3fe7a11473eb0187L, 0x3fe82589994cce13L,
       0x3fe8ace5422aa0dbL, 0x3fe93737b0cdc5e5L, 0x3fe9c49182a3f090L, 0x3fea5503b23e255dL,
       0x3feae89f995ad3adL, 0x3feb7f76f2fb5e47L, 0x3fec199bdd85529cL, 0x3fecb720dcef9069L,
       0x3fed5818dcfba487L, 0x3fedfc97337b9b5fL, 0x3feea4afa2a490daL, 0x3fef50765b6e4540L};

  private static final double[] cp =
        {0x1.5555555555555p-3, 0x1.11111111146e1p-7, 0x1.a01a00930dda6p-13, 0x1.71f92198aa6e9p-19};

  public static float compute(float x) {
    double iln2 = 0x1.71547652b82fep+5;
    int ux = Float.floatToRawIntBits(x)<<1;
    double z = x;
    if(Integer.compareUnsigned(ux, 0x8565a9f8)>0){ // |x| > 0x1.65a9f8p+6
      float sgn = Math.copySign(2.0f, x);
      if(Integer.compareUnsigned(ux, 0xff000000)>=0) {
        if((ux<<8) != 0) return x + x; // nan
        return x; // +-inf
      }
      float r = sgn*0x1.fffffep127f;
      // the C source sets errno to ERANGE here
      return r;
    }
    if(Integer.compareUnsigned(ux, 0x7c000000)<0){ // |x| < 0.125
      if(Integer.compareUnsigned(ux, 0x74250bfe)<=0){ // |x| <= 0x1.250bfep-11
        if(Integer.compareUnsigned(ux, 0x66000000)<0) { // |x| < 0x1p-24
          /* The Taylor expansion of sinh(x) at x=0 is x + x^3/6 + o(x^3),
             thus for |x| >= 2^-126 we have no underflow, whatever the
             rounding mode.
             For |x| < 2^-126 and rounding towards zero, we have underflow.
             For x = nextbelow(2^-126) = 0x1.fffffcp-127, sinh(x) would round
             upward to 0x1.fffffep-127 with unbounded exponent range, which is not
             representable, thus we have underflow too.
             In summary, we have underflow whenever |x| < 2^-126. */
          // the C source sets errno to ERANGE here on underflow
          return Math.fma(x, Math.abs(x), x);
        }
        if(stUarg[0] == ux){
          float sgn = Math.copySign(1.0f, x);
          return sgn*stRh[0] + sgn*stRl[0];
        }
        return (x*0x1.555556p-3f)*(x*x) + x;
      }
      double z2 = z*z, z4 = z2*z2;
      return (float)(z + (z2*z)*((cp[0] + z2*cp[1]) + z4*(cp[2] + z2*(cp[3]))));
    }
    double a = iln2*z, ia = Math.rint(a), h = a - ia, h2 = h*h;
    long ja = Double.doubleToRawLongBits(ia + 0x1.8p52);
    long jp = ja, jm = -jp;
    long sp = tb[(int)(jp&31)] + ((jp>>5)<<52), sm = tb[(int)(jm&31)] + ((jm>>5)<<52);
    double te = c[0] + h2*c[2], to = (c[1] + h2*c[3]);
    double rp = Double.longBitsToDouble(sp)*(te + h*to),
           rm = Double.longBitsToDouble(sm)*(te - h*to), r = rp - rm;
    float ub = (float)r, lb = (float)(r  - 1.52e-10*r);
    if(ub != lb){
      double iln2h = 0x1.7154765p+5, iln2l = 0x1.5c17f0bbbe88p-26;
      h = (iln2h*z - ia) + iln2l*z;
      h2 = h*h;
      te = ch[0] + h2*ch[2] + (h2*h2)*(ch[4] + h2*ch[6]);
      to = ch[1] + h2*(ch[3] + h2*ch[5]);
      r = Double.longBitsToDouble(sp)*(te + h*to) - Double.longBitsToDouble(sm)*(te - h*to);
      ub = (float)r;
    }
    return ub;
  }
}
