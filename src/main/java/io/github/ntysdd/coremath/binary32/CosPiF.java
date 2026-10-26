/*
 * Correctly rounded cos(pi*x) for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/cospi/cospif.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_cospif -> CosPiF.compute
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
 * Correctly rounded {@code cos(pi*x)} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_cospif}.
 *
 * <p>Below {@code 2^-15} the cosine is the {@code 1 - (pi x)^2/2} expansion,
 * above it the same {@code 2^7 x} reduction as {@code SinPiF} feeds the
 * {@code S} table, which holds {@code sin(i*pi/64)} over the full period; the
 * cosine of a multiple of {@code pi/128} is the sine of it shifted by a quarter
 * period.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code feraiseexcept(FE_INVALID)} is dropped: the JVM exposes no
 *       floating-point environment, and the NaN it accompanies is written as
 *       {@link Float#NaN}, where the C code calls
 *       {@code __builtin_nanf("inf")}. That is the same quiet NaN
 *       {@code 0x7fc00000}.
 *   <li>The {@code b32u32_u} union becomes
 *       {@link Float#floatToRawIntBits}; the bit pattern {@code ix} is carried
 *       as an {@code int} and {@code __builtin_expect} is dropped.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma}.
 *   <li>{@code uint32_t} casts before the left shifts are no-ops on the 32-bit
 *       wrapping arithmetic, {@code int} is used throughout, and the
 *       {@code iq = (iq + 1)>>1} shift of the {@code uint32_t} {@code iq}
 *       becomes {@code >>>}. The {@code ax} of the small-argument branch
 *       keeps the
 *       comparison of the C code, which is safe because the sign bit was
 *       cleared.
 *   <li>Where the C code narrows a {@code double} to {@code float} (the
 *       {@code S} entries and the result of the polynomial branch) the cast is
 *       explicit.
 *   <li>{@code static const} tables declared inside the function become
 *       fields: {@code sn}, {@code cn} and {@code S}, the last with all 128
 *       entries.
 * </ul>
 */
public final strictfp class CosPiF {
  private CosPiF() {
  }

  private static final double[] sn = {
       0x1.921fb54442d0fp-37, -0x1.4abbce6102b94p-112, 0x1.4669fa3c58463p-189};

  private static final double[] cn = {
       -0x1.3bd3cc9be45cfp-74, 0x1.03c1f08088742p-150, -0x1.55d1e5eff55a5p-228};

  private static final double[] S = {
       0x0p+0, 0x1.91f65f10dd814p-5, 0x1.917a6bc29b42cp-4, 0x1.2c8106e8e613ap-3,
       0x1.8f8b83c69a60bp-3, 0x1.f19f97b215f1bp-3, 0x1.294062ed59f06p-2, 0x1.58f9a75ab1fddp-2,
       0x1.87de2a6aea963p-2, 0x1.b5d1009e15ccp-2, 0x1.e2b5d3806f63bp-2, 0x1.073879922ffeep-1,
       0x1.1c73b39ae68c8p-1, 0x1.30ff7fce17035p-1, 0x1.44cf325091dd6p-1, 0x1.57d69348cecap-1,
       0x1.6a09e667f3bcdp-1, 0x1.7b5df226aafafp-1, 0x1.8bc806b151741p-1, 0x1.9b3e047f38741p-1,
       0x1.a9b66290ea1a3p-1, 0x1.b728345196e3ep-1, 0x1.c38b2f180bdb1p-1, 0x1.ced7af43cc773p-1,
       0x1.d906bcf328d46p-1, 0x1.e212104f686e5p-1, 0x1.e9f4156c62ddap-1, 0x1.f0a7efb9230d7p-1,
       0x1.f6297cff75cbp-1, 0x1.fa7557f08a517p-1, 0x1.fd88da3d12526p-1, 0x1.ff621e3796d7ep-1,
       0x1p+0, 0x1.ff621e3796d7ep-1, 0x1.fd88da3d12526p-1, 0x1.fa7557f08a517p-1,
       0x1.f6297cff75cbp-1, 0x1.f0a7efb9230d7p-1, 0x1.e9f4156c62ddap-1, 0x1.e212104f686e5p-1,
       0x1.d906bcf328d46p-1, 0x1.ced7af43cc773p-1, 0x1.c38b2f180bdb1p-1, 0x1.b728345196e3ep-1,
       0x1.a9b66290ea1a3p-1, 0x1.9b3e047f38741p-1, 0x1.8bc806b151741p-1, 0x1.7b5df226aafafp-1,
       0x1.6a09e667f3bcdp-1, 0x1.57d69348cecap-1, 0x1.44cf325091dd6p-1, 0x1.30ff7fce17035p-1,
       0x1.1c73b39ae68c8p-1, 0x1.073879922ffeep-1, 0x1.e2b5d3806f63bp-2, 0x1.b5d1009e15ccp-2,
       0x1.87de2a6aea963p-2, 0x1.58f9a75ab1fddp-2, 0x1.294062ed59f06p-2, 0x1.f19f97b215f1bp-3,
       0x1.8f8b83c69a60bp-3, 0x1.2c8106e8e613ap-3, 0x1.917a6bc29b42cp-4, 0x1.91f65f10dd814p-5,
       0x0p+0, -0x1.91f65f10dd814p-5, -0x1.917a6bc29b42cp-4, -0x1.2c8106e8e613ap-3,
       -0x1.8f8b83c69a60bp-3, -0x1.f19f97b215f1bp-3, -0x1.294062ed59f06p-2, -0x1.58f9a75ab1fddp-2,
       -0x1.87de2a6aea963p-2, -0x1.b5d1009e15ccp-2, -0x1.e2b5d3806f63bp-2, -0x1.073879922ffeep-1,
       -0x1.1c73b39ae68c8p-1, -0x1.30ff7fce17035p-1, -0x1.44cf325091dd6p-1, -0x1.57d69348cecap-1,
       -0x1.6a09e667f3bcdp-1, -0x1.7b5df226aafafp-1, -0x1.8bc806b151741p-1, -0x1.9b3e047f38741p-1,
       -0x1.a9b66290ea1a3p-1, -0x1.b728345196e3ep-1, -0x1.c38b2f180bdb1p-1, -0x1.ced7af43cc773p-1,
       -0x1.d906bcf328d46p-1, -0x1.e212104f686e5p-1, -0x1.e9f4156c62ddap-1, -0x1.f0a7efb9230d7p-1,
       -0x1.f6297cff75cbp-1, -0x1.fa7557f08a517p-1, -0x1.fd88da3d12526p-1, -0x1.ff621e3796d7ep-1,
       -0x1p+0, -0x1.ff621e3796d7ep-1, -0x1.fd88da3d12526p-1, -0x1.fa7557f08a517p-1,
       -0x1.f6297cff75cbp-1, -0x1.f0a7efb9230d7p-1, -0x1.e9f4156c62ddap-1, -0x1.e212104f686e5p-1,
       -0x1.d906bcf328d46p-1, -0x1.ced7af43cc773p-1, -0x1.c38b2f180bdb1p-1, -0x1.b728345196e3ep-1,
       -0x1.a9b66290ea1a3p-1, -0x1.9b3e047f38741p-1, -0x1.8bc806b151741p-1, -0x1.7b5df226aafafp-1,
       -0x1.6a09e667f3bcdp-1, -0x1.57d69348cecap-1, -0x1.44cf325091dd6p-1, -0x1.30ff7fce17035p-1,
       -0x1.1c73b39ae68c8p-1, -0x1.073879922ffeep-1, -0x1.e2b5d3806f63bp-2, -0x1.b5d1009e15ccp-2,
       -0x1.87de2a6aea963p-2, -0x1.58f9a75ab1fddp-2, -0x1.294062ed59f06p-2, -0x1.f19f97b215f1bp-3,
       -0x1.8f8b83c69a60bp-3, -0x1.2c8106e8e613ap-3, -0x1.917a6bc29b42cp-4, -0x1.91f65f10dd814p-5};

  public static float compute(float x) {
    int ix = Float.floatToRawIntBits(x);
    int e = (ix>>>23)&0xff;
    if(e == 0xff){
      if((ix << 9) == 0){
        // C raises FE_INVALID here and returns __builtin_nanf("inf")
        return Float.NaN;
      }
      return x + x; // nan
    }
    int m = (ix&(~0>>>9))|1<<23, s = 143 - e, p = e - 112;
    if(p<0) // |x| < 2^-15
    {
      int ax = ix & (~0>>>1);
      // Warning: -0x1.3bd3ccp+2f * x underflows for |x| < 0x1.9f03p-129
      if (ax >= 0x19f030)
        return Math.fma (-0x1.3bd3ccp+2f * x, x, 1.0f);
      else // |x| < 0x1.9f03p-129
        return Math.fma (-x, x, 1.0f);
    }
    if(p>31) {
      if(p>63) return 1.0f;
      int iq = m << (p - 32);
      return (float) S[(iq+32)&127];
    }
    int k = m << p;
    if(k==0){
      int iq = m >> (32-p);
      return (float) S[(iq+32)&127];
    }
    double z = k, z2 = z*z;
    double fs = sn[0] + z2*(sn[1] + z2*sn[2]);
    double fc = cn[0] + z2*(cn[1] + z2*cn[2]);
    int iq = m>>s; iq = (iq + 1)>>>1;
    int is = iq&127, ic = (iq + 32)&127;
    double ts = S[ic], tc = S[is];
    double r = ts + (ts*z2)*fc - (tc*z)*fs;
    return (float) r;
  }
}
