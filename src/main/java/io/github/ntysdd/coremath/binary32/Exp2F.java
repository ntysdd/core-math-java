/*
 * Correctly rounded exp2 function for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/exp2/exp2f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_exp2f -> Exp2F.compute
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
 * Correctly rounded {@code 2^x} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_exp2f}.
 *
 * <p>Exact powers of two are answered from the exponent field, then the
 * reduction against the {@code tb} table of 2^(k/64) halves, the degree-3
 * polynomial {@code b} and the two bounds; when they disagree the degree-5
 * polynomial {@code c} is evaluated instead. Three hard cases are named
 * explicitly, and {@code as_special} covers {@code x < -149}, {@code x >= 128},
 * the infinities and the NaNs.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code as_special} is {@code asSpecial}; it stays a private static
 *       method.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; {@code sv}, the union holding
 *       2^(k/64) scaled by the exponent, is carried as a {@code long} bit
 *       pattern and materialised once into {@code svf}, which stands for the
 *       {@code sv.f} that the C source reads three times.
 *   <li>{@code static const} tables declared inside the function become fields:
 *       {@code ir}, {@code tb}, {@code b} and {@code c}. {@code ir} is an array
 *       of two floats whose first entry is {@code __builtin_inff()}, which is
 *       {@link Float#POSITIVE_INFINITY}; {@code tb} is an array of the union
 *       {@code {double f; uint64_t u;}} initialised through its float member, so
 *       it becomes a {@code double[]} and the bits are read through
 *       {@link Double#doubleToRawLongBits}.
 *   <li>{@code __builtin_fmax} is {@link Math#max}, which agrees with
 *       {@code fmax} here because {@code y} cannot be a NaN at that point.
 *   <li>{@code errno} assignments are dropped, since no caller can observe
 *       {@code errno}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned} and {@code uint32_t} shifts are
 *       arithmetic on {@code int}, except where the C source casts to
 *       {@code int} first ({@code int msk = (int)t.u>>31}).
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code ub}, {@code lb} and the {@code r} of {@code as_special}) the cast
 *       is explicit.
 * </ul>
 */
public final strictfp class Exp2F {
  private Exp2F() {
  }

  // deal with x=nan, x < -149 and x >= 128
  private static float asSpecial(float x) {
    int t = Float.floatToRawIntBits(x);
    int ux = t<<1;
    if(Integer.compareUnsigned(ux, 0xff<<24) >= 0) { // x is inf or nan
      if(Integer.compareUnsigned(ux, 0xff<<24) > 0) return x + x; // x = nan
      return ir[t>>>31]; // x = +-inf
    }
    if(Integer.compareUnsigned(t, 0xc3150000)>=0){ // x < -149
      double z = x, y = 0x1p-149 + (z + 149)*0x1p-150;
      y = Math.max(y, 0x1p-151);
      float r = (float)y;
      // the C source sets errno to ERANGE here on underflow
      return r;
    }
    // now x >= 128
    float r = 0x1p127f * 0x1p127f;
    // the C source sets errno to ERANGE here on overflow
    return r;
  }

  // static const float ir[] inside as_special() in the C source
  private static final float[] ir = {Float.POSITIVE_INFINITY, 0.0f};

  // static const b64u64_u tb[] inside cr_exp2f() in the C source
  private static final double[] tb =
      {0x1.0000000000000p+0, 0x1.02c9a3e778061p+0, 0x1.059b0d3158574p+0, 0x1.0874518759bc8p+0,
       0x1.0b5586cf9890fp+0, 0x1.0e3ec32d3d1a2p+0, 0x1.11301d0125b51p+0, 0x1.1429aaea92de0p+0,
       0x1.172b83c7d517bp+0, 0x1.1a35beb6fcb75p+0, 0x1.1d4873168b9aap+0, 0x1.2063b88628cd6p+0,
       0x1.2387a6e756238p+0, 0x1.26b4565e27cddp+0, 0x1.29e9df51fdee1p+0, 0x1.2d285a6e4030bp+0,
       0x1.306fe0a31b715p+0, 0x1.33c08b26416ffp+0, 0x1.371a7373aa9cbp+0, 0x1.3a7db34e59ff7p+0,
       0x1.3dea64c123422p+0, 0x1.4160a21f72e2ap+0, 0x1.44e086061892dp+0, 0x1.486a2b5c13cd0p+0,
       0x1.4bfdad5362a27p+0, 0x1.4f9b2769d2ca7p+0, 0x1.5342b569d4f82p+0, 0x1.56f4736b527dap+0,
       0x1.5ab07dd485429p+0, 0x1.5e76f15ad2148p+0, 0x1.6247eb03a5585p+0, 0x1.6623882552225p+0,
       0x1.6a09e667f3bcdp+0, 0x1.6dfb23c651a2fp+0, 0x1.71f75e8ec5f74p+0, 0x1.75feb564267c9p+0,
       0x1.7a11473eb0187p+0, 0x1.7e2f336cf4e62p+0, 0x1.82589994cce13p+0, 0x1.868d99b4492edp+0,
       0x1.8ace5422aa0dbp+0, 0x1.8f1ae99157736p+0, 0x1.93737b0cdc5e5p+0, 0x1.97d829fde4e50p+0,
       0x1.9c49182a3f090p+0, 0x1.a0c667b5de565p+0, 0x1.a5503b23e255dp+0, 0x1.a9e6b5579fdbfp+0,
       0x1.ae89f995ad3adp+0, 0x1.b33a2b84f15fbp+0, 0x1.b7f76f2fb5e47p+0, 0x1.bcc1e904bc1d2p+0,
       0x1.c199bdd85529cp+0, 0x1.c67f12e57d14bp+0, 0x1.cb720dcef9069p+0, 0x1.d072d4a07897cp+0,
       0x1.d5818dcfba487p+0, 0x1.da9e603db3285p+0, 0x1.dfc97337b9b5fp+0, 0x1.e502ee78b3ff6p+0,
       0x1.ea4afa2a490dap+0, 0x1.efa1bee615a27p+0, 0x1.f50765b6e4540p+0, 0x1.fa7c1819e90d8p+0};

  private static final double[] b = {1, 0x1.62e42fef4c4e7p-1, 0x1.ebfd1b232f475p-3, 0x1.c6b19384ecd93p-5};

  private static final double[] c =
        {0x1.62e42fefa39efp-1, 0x1.ebfbdff82c58fp-3, 0x1.c6b08d702e0edp-5, 0x1.3b2ab6fb92e5ep-7,
         0x1.5d886e6d54203p-10, 0x1.430976b8ce6efp-13};

  public static float compute(float x) {
    int t = Float.floatToRawIntBits(x);
    if((t&0xffff)==0){ // x maybe integer
      int k = ((t>>>23)&0xff)-127; // 2^k <= |x| < 2^(k+1)
      if(k>=0 && k<9 && (t<<(9+k)) == 0){
        // x integer, with 1 <= |x| < 2^9
        int msk = t>>31;
        int m = ((t&0x7fffff)|(1<<23))>>(23-k);
        m = (m^msk) - msk + 127;
        if(m>0 && m<255){
          return Float.intBitsToFloat(m<<23);
        } else if(m<=0 && m>-23){
          /* If f(x) underflows but is exact, no underflow exception should be
             raised (cf IEEE 754-2019). */
          return Float.intBitsToFloat(1<<(22+m));
        }
      }
    }
    int ux = t<<1;
    if (Integer.compareUnsigned(ux, 0x86000000)>=0 || Integer.compareUnsigned(ux, 0x65000000)<0){
      // |x| >= 128 or x=nan or |x| < 0x1p-26
      if(Integer.compareUnsigned(ux, 0x65000000)<0) return 1.0f + x; // |x| < 0x1p-26
      // if x < -149 or 128 <= x we call as_special()
      if(!(Integer.compareUnsigned(t, 0xc3000000)>=0 && Integer.compareUnsigned(t, 0xc3150000)<0))
        return asSpecial(x);
    }
    double offd = 0x1.8p46, xd = x, h = xd - ((xd + offd) - offd), h2 = h*h;
    int u = Float.floatToRawIntBits(x + 0x1.8p17f);
    long sv = Double.doubleToRawLongBits(tb[u&0x3f]);
    sv += ((long)(u>>>6))<<52;
    // sv.f of the C source, which reads it three times below
    double svf = Double.longBitsToDouble(sv);
    double r = svf*((b[0] + h*b[1]) + h2*(b[2] + h*b[3])), eps = 0x1.3d8p-33;
    float ub = (float)r, lb = (float)(r - r*eps);
    if(ub != lb){
      if(Integer.compareUnsigned(ux, 0x79e7526e)<=0){
        if(t == 0x3b429d37) return 0x1.00870ap+0f - 0x1p-25f;
        if(t == 0xbcf3a937) return 0x1.f58d62p-1f - 0x1p-26f;
        if(t == 0xb8d3d026) return 0x1.fff6d2p-1f + 0x1p-26f;
      }
      r = svf + (svf*h)*((c[0] + h*c[1]) + h2*((c[2] + h*c[3]) + h2*(c[4] + h*c[5])));
      ub = (float)r;
    }
    // the C source sets errno to ERANGE here when x < -126
    return ub;
  }
}
