/*
 * Correctly rounded 10^x exponential function for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/exp10/exp10f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_exp10f -> Exp10F.compute
 *
 * Copyright (c) 2023-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly rounded {@code 10^x} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_exp10f}.
 *
 * <p>Small arguments are answered with a degree-2 polynomial, exact integer
 * powers of ten from the {@code ex} table indexed by the exponent of {@code x},
 * and the other arguments are reduced against {@code 105/2^6} with the
 * {@code tb} table of 2^(k/32), evaluated with the degree-3 polynomial
 * {@code b} and the two bounds {@code ub}/{@code lb}; when they disagree the
 * degree-5 polynomial {@code c} is evaluated instead.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code roundeven_finite} is {@link Math#rint}. The {@code #if} that
 *       defines it selects {@code __builtin_roundeven} for the reference build,
 *       so the software fallback of the {@code #else} branch is dropped, and so
 *       is the {@code #pragma STDC FENV_ACCESS ON}.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; {@code sv}, the union holding
 *       2^(k/32) scaled by the exponent, is carried as a {@code long} bit
 *       pattern and materialised once into {@code svf}, which stands for the
 *       {@code sv.f} that the C source reads three times.
 *   <li>{@code static const} tables declared inside the function become
 *       fields: {@code c}, {@code b}, {@code tb}, {@code ex} and {@code ir}.
 *       {@code ex} is an array of {@code float} initialised with the C decimal
 *       literals, which are all exact in binary32, and {@code tb} is an array
 *       of {@code uint64_t} bit patterns.
 *   <li>{@code __builtin_fmax}, {@code __builtin_popcount} and
 *       {@code __builtin_expect} are {@link Math#max},
 *       {@link Integer#bitCount} and nothing; {@code __builtin_fmax} agrees
 *       with {@code fmax} here because {@code y} cannot be a NaN at that point.
 *       The {@code errno} assignments are dropped, since no caller can observe
 *       {@code errno}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned} and {@code uint32_t} shifts are
 *       arithmetic on {@code int}: {@code ux} is {@code t.u<<1}, whose bit 31
 *       is the sign bit of {@code x} shifted out, and the {@code unsigned k} of
 *       the exact powers of ten wraps, so it is compared unsigned as well.
 *   <li>{@code (uint64_t)(ja>>5)<<52} keeps the arithmetic {@code long} shift
 *       of {@code ja} and the {@code long} shift count.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code 1.0 + z*(...)}, {@code ub}, {@code lb} and the {@code r} of the
 *       underflow path) the cast is explicit.
 *   <li>{@code 0x1p127f * 0x1p127f} stays a multiplication: the C source
 *       overflows it on purpose to return {@code +inf}.
 * </ul>
 */
public final strictfp class Exp10F {
  private Exp10F() {
  }

  // static const float ir[] inside cr_exp10f() in the C source
  private static final float[] ir = {Float.POSITIVE_INFINITY, 0.0f};

  private static final double[] c =
      {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c58fp-3, 0x1.c6b08d702e0edp-5,
       0x1.3b2ab6fb92e5ep-7, 0x1.5d886e6d54203p-10, 0x1.430976b8ce6efp-13};

  private static final double[] b =
      {1, 0x1.62e42fef4c4e7p-6, 0x1.ebfd1b232f475p-13, 0x1.c6b19384ecd93p-20};

  private static final long[] tb =
      {0x3ff0000000000000L, 0x3ff059b0d3158574L, 0x3ff0b5586cf9890fL, 0x3ff11301d0125b51L,
       0x3ff172b83c7d517bL, 0x3ff1d4873168b9aaL, 0x3ff2387a6e756238L, 0x3ff29e9df51fdee1L,
       0x3ff306fe0a31b715L, 0x3ff371a7373aa9cbL, 0x3ff3dea64c123422L, 0x3ff44e086061892dL,
       0x3ff4bfdad5362a27L, 0x3ff5342b569d4f82L, 0x3ff5ab07dd485429L, 0x3ff6247eb03a5585L,
       0x3ff6a09e667f3bcdL, 0x3ff71f75e8ec5f74L, 0x3ff7a11473eb0187L, 0x3ff82589994cce13L,
       0x3ff8ace5422aa0dbL, 0x3ff93737b0cdc5e5L, 0x3ff9c49182a3f090L, 0x3ffa5503b23e255dL,
       0x3ffae89f995ad3adL, 0x3ffb7f76f2fb5e47L, 0x3ffc199bdd85529cL, 0x3ffcb720dcef9069L,
       0x3ffd5818dcfba487L, 0x3ffdfc97337b9b5fL, 0x3ffea4afa2a490daL, 0x3fff50765b6e4540L};

  // static const float ex[] inside cr_exp10f() in the C source
  private static final float[] ex =
      {10f, 100f, 1000f, 10000f, 100000f, 1000000f, 10000000f, 100000000f, 1000000000f, 10000000000f};

  public static float compute(float x) {
    double iln102 = 0x1.a934f0979a371p+6, iln102h = 0x1.a934f09p+1, iln102l = 0x1.e68dc57f2496p-29;
    int t = Float.floatToRawIntBits(x);
    double z = x;
    int ux = t<<1;
    if (Integer.compareUnsigned(ux, 0x84344134)>0 || Integer.compareUnsigned(ux, 0x72adf1c6)<0){
      // |x| > 0x1.344134p+5 or x=nan or |x| < 0x1.adf1c6p-13
      if(Integer.compareUnsigned(ux, 0x72adf1c6)<0) // |x| < 0x1.adf1c6p-13
        return (float)(1.0 + z*(0x1.26bb1bbb55516p+1
            + z*(0x1.53524c73cea69p+1 + z*0x1.0470591de2ca4p+1)));
      if(Integer.compareUnsigned(ux, 0xff<<24) >= 0) { // x is inf or nan
        if(Integer.compareUnsigned(ux, 0xff<<24) > 0) return x + x; // x = nan
        return ir[t>>>31]; // x = +-inf
      }
      if(Integer.compareUnsigned(t, 0xc23369f4)>0){ // x < -0x1.66d3e8p+5
        double y = 0x1p-149 + (z + 0x1.66d3e7bd9a403p+5)*0x1.a934f0979a37p-149;
        y = Math.max(y, 0x1p-151);
        float r = (float)y;
        // the C source sets errno to ERANGE here on underflow
        return r;
      }
      if(Integer.compareUnsigned(t, 0x80000000)<0){ // x > 0x1.344134p+5
        float r = 0x1p127f * 0x1p127f;
        // the C source sets errno to ERANGE here on overflow
        return r;
      }
    }
    if((t<<12)==0){
      int k = (t >>> 20) - 1016;
      if(Integer.compareUnsigned(k, 26)<=0){
        int bt = 1<<k, msk = 0x7551101;
        if((bt&msk)!=0) return ex[Integer.bitCount(msk&(bt-1))];
      }
    }
    double a = iln102*z, ia = Math.rint(a), h = a - ia;
    long ja = (long)ia;
    long sv = tb[(int)(ja&0x1f)] + ((ja>>5)<<52);
    double svf = Double.longBitsToDouble(sv);
    double h2 = h*h, r = ((b[0] + h*b[1]) + h2*(b[2] + h*(b[3])))*svf;
    float ub = (float)r, lb = (float)(r - r*1.45e-10);
    if(ub != lb){
      h = (iln102h*z - ia*0.03125) + iln102l*z;
      double s = svf;
      h2 = h*h;
      double w = s*h;
      r = s + w*((c[0] + h*c[1]) + h2*((c[2] + h*c[3]) + h2*(c[4] + h*c[5])));
      ub = (float)r;
    }
    // the C source sets errno to ERANGE here when x <= -0x1.2f7032p+5
    return ub;
  }
}
