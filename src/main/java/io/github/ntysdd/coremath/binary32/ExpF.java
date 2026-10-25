/*
 * Correctly-rounded exponential of binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/exp/expf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_expf -> ExpF.compute
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
 * Correctly rounded exponential of a binary32 value, the translation of the
 * CORE-MATH {@code cr_expf}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code c}, {@code b} and {@code tb} tables declared inside
 *       {@code cr_expf} become fields, and so does the {@code ir} array that
 *       holds the two results for an infinite argument.
 *   <li>{@code __builtin_fmax} becomes {@link Math#max}.
 *   <li>{@code 0x1p127f * 0x1p127f} and the double products that overflow or
 *       underflow are kept: Java also returns an infinity or a zero for them, it
 *       only does not raise a flag.
 *   <li>{@code uint32_t} comparisons are unsigned through
 *       {@link Integer#compareUnsigned}.
 * </ul>
 */
public final strictfp class ExpF {
  private ExpF() {
  }

  // static const double c[] and b[], and static const uint64_t tb[] inside
  // cr_expf() in the C source
  private static final double[] c =
    {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c58fp-3, 0x1.c6b08d702e0edp-5,
     0x1.3b2ab6fb92e5ep-7, 0x1.5d886e6d54203p-10, 0x1.430976b8ce6efp-13};
  private static final double[] b =
    {1, 0x1.62e42fef4c4e7p-1, 0x1.ebfd1b232f475p-3, 0x1.c6b19384ecd93p-5};
  private static final long[] tb =
    {0x3ff0000000000000L, 0x3ff02c9a3e778061L, 0x3ff059b0d3158574L, 0x3ff0874518759bc8L,
     0x3ff0b5586cf9890fL, 0x3ff0e3ec32d3d1a2L, 0x3ff11301d0125b51L, 0x3ff1429aaea92de0L,
     0x3ff172b83c7d517bL, 0x3ff1a35beb6fcb75L, 0x3ff1d4873168b9aaL, 0x3ff2063b88628cd6L,
     0x3ff2387a6e756238L, 0x3ff26b4565e27cddL, 0x3ff29e9df51fdee1L, 0x3ff2d285a6e4030bL,
     0x3ff306fe0a31b715L, 0x3ff33c08b26416ffL, 0x3ff371a7373aa9cbL, 0x3ff3a7db34e59ff7L,
     0x3ff3dea64c123422L, 0x3ff4160a21f72e2aL, 0x3ff44e086061892dL, 0x3ff486a2b5c13cd0L,
     0x3ff4bfdad5362a27L, 0x3ff4f9b2769d2ca7L, 0x3ff5342b569d4f82L, 0x3ff56f4736b527daL,
     0x3ff5ab07dd485429L, 0x3ff5e76f15ad2148L, 0x3ff6247eb03a5585L, 0x3ff6623882552225L,
     0x3ff6a09e667f3bcdL, 0x3ff6dfb23c651a2fL, 0x3ff71f75e8ec5f74L, 0x3ff75feb564267c9L,
     0x3ff7a11473eb0187L, 0x3ff7e2f336cf4e62L, 0x3ff82589994cce13L, 0x3ff868d99b4492edL,
     0x3ff8ace5422aa0dbL, 0x3ff8f1ae99157736L, 0x3ff93737b0cdc5e5L, 0x3ff97d829fde4e50L,
     0x3ff9c49182a3f090L, 0x3ffa0c667b5de565L, 0x3ffa5503b23e255dL, 0x3ffa9e6b5579fdbfL,
     0x3ffae89f995ad3adL, 0x3ffb33a2b84f15fbL, 0x3ffb7f76f2fb5e47L, 0x3ffbcc1e904bc1d2L,
     0x3ffc199bdd85529cL, 0x3ffc67f12e57d14bL, 0x3ffcb720dcef9069L, 0x3ffd072d4a07897cL,
     0x3ffd5818dcfba487L, 0x3ffda9e603db3285L, 0x3ffdfc97337b9b5fL, 0x3ffe502ee78b3ff6L,
     0x3ffea4afa2a490daL, 0x3ffefa1bee615a27L, 0x3fff50765b6e4540L, 0x3fffa7c1819e90d8L};

  // static const float ir[] inside cr_expf() in the C source
  private static final float[] ir = {Float.POSITIVE_INFINITY, 0.0f};

  public static float compute(float x) {
    double iln2 = 0x1.71547652b82fep+0, big = 0x1.8p46;
    int u = Float.floatToRawIntBits(x);
    double z = x, a = iln2*z;
    long uu = Double.doubleToRawLongBits(a + big);
    int ux = u<<1;
    if (Integer.compareUnsigned(ux, 0x8562e42e)>0 || Integer.compareUnsigned(ux, 0x6f93813e)<0){
      // |x| > 0x1.62e42ep+6 or x=nan or |x| < 0x1.93813ep-16
      if(Integer.compareUnsigned(ux, 0x6f93813e)<0) // |x| < 0x1.93813ep-16
        return (float)(1.0 + z*(1 + z*0.5));
      if(Integer.compareUnsigned(ux, 0xff<<24) >= 0) { // x is inf or nan
        if(Integer.compareUnsigned(ux, 0xff<<24) > 0) return x + x; // x = nan
        return ir[u>>>31]; // x = +-inf
      }
      if(Integer.compareUnsigned(u, 0xc2ce8ec0)>0){ // x < -0x1.9d1d8p+6
        double y = 0x1p-149 + (z + 0x1.9d1d9fccf477p+6)*0x1.71547652b82edp-150;
        y = Math.max(y, 0x1p-151);
        float r = (float)y;
        return r;
      }
      if((u>>>31)==0 && Integer.compareUnsigned(u, 0x42b17217)>0){ // x > 0x1.62e42ep+6
        float r = 0x1p127f * 0x1p127f;
        return r;
      }
    }
    double ia = big - Double.longBitsToDouble(uu), h = a + ia;
    double sv = Double.longBitsToDouble(tb[(int)(uu&0x3f)] + ((uu>>>6)<<52));
    double h2 = h*h, r = ((b[0] + h*b[1]) + h2*(b[2] + h*(b[3])))*sv;
    float ub = (float)r, lb = (float)(r - r*1.45e-10);
    if(ub != lb){
      double iln2h = 0x1.7154765p+0, iln2l = 0x1.5c17f0bbbe88p-31;
      h = (iln2h*z + ia) + iln2l*z;
      double s = sv;
      h2 = h*h;
      double w = s*h;
      r = s + w*((c[0] + h*c[1]) + h2*((c[2] + h*c[3]) + h2*(c[4] + h*c[5])));
      ub = (float)r;
    }
    return ub;
  }
}
