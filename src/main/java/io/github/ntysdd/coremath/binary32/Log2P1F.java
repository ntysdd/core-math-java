/*
 * Correctly-rounded log2(1+x) function for binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/log2p1/log2p1f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_log2p1f -> Log2P1F.compute
 *
 * Copyright (c) 2022-2026 Alexei Sibidanov <sibid@uvic.ca>
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
 * Correctly rounded {@code log2(1+x)} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_log2p1f}.
 *
 * <p>{@code as_special} answers {@code x <= -1}, the infinities and the NaNs;
 * for {@code |x| < 0.0234375} the function returns {@code x} scaled by
 * {@code 1/log(2)}, or evaluates the degree-7 polynomial {@code g} when
 * {@code |x| > 0x1-126*ln(2)}; {@code 1+x = 2^n} is returned as {@code n}; and
 * the rest reduces against the reciprocal table {@code ix} and the shifted
 * logarithm table {@code lix} with the degree-2 polynomial {@code b}, accepting
 * the result when the bound holds. When it does not, the two arguments of
 * {@code tb} are named explicitly and the degree-5 polynomial {@code c} is
 * evaluated instead.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Float#intBitsToFloat}/{@link Double#doubleToRawLongBits};
 *       {@code ux}, {@code ax}, {@code t}, {@code tp}, {@code m} and
 *       {@code xd} are bit patterns.
 *   <li>{@code __attribute__((noinline))} is nothing, {@code __builtin_expect}
 *       is nothing, and the {@code #pragma STDC FENV_ACCESS ON} is dropped. The
 *       {@code errno} assignments of {@code as_special} are dropped too, and its
 *       two arithmetic results {@code -1.0f/0.0f} and {@code 0.0f/0.0f} become
 *       {@link Float#NEGATIVE_INFINITY} and {@link Float#NaN}, since Java has
 *       no {@code FE_DIVBYZERO} or {@code FE_INVALID} to raise; the bit pattern
 *       {@code as_special} writes through the union is not part of the C result.
 *       The {@code errno} assignments of the two small-argument branches are
 *       dropped as well.
 *   <li>The {@code struct {b32u32_u x; float f, df;} tb[]} becomes the three
 *       columns {@code tbX}, {@code tbF} and {@code tbDf}, since both the bit
 *       pattern of {@code x} and the {@code float} members are read.
 *   <li>The {@code static const} tables declared inside the function become
 *       fields, in the order of the C source: {@code tbX}, {@code tbF},
 *       {@code tbDf}, {@code ix}, {@code lix}, {@code b}, {@code c} and
 *       {@code g}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, and the {@code int32_t j} of the
 *       reduction is an {@code int} built from the {@code long} significand.
 *   <li>Where the C code narrows a {@code double} to {@code float} the cast is
 *       explicit: the {@code z*0x1.71547652b82fep+0} return of the smallest
 *       band, the {@code f} of the {@code g} band, {@code lb}, {@code ub} and
 *       the final {@code lb = f}.
 * </ul>
 */
public final strictfp class Log2P1F {
  private Log2P1F() {
  }

  private static float as_special(float x) {
    int t = Float.floatToRawIntBits(x);
    int ux = t, ax = ux<<1;
    if(ux == 0xbf800000){ // x = -1.0
      // the C source sets errno to ERANGE here
      return Float.NEGATIVE_INFINITY; // the C source computes -1.0f/0.0f
    }
    if(ux == 0x7f800000) return x; // +inf
    if(Integer.compareUnsigned(ax, 0xff000000) > 0) return x + x; // nan
    // the C source sets errno to EDOM here
    return Float.NaN; // the C source computes 0.0f/0.0f
  }

  // the x member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbX = {0x1.7a13c6p+30f, -0x1.da285cp-5f};

  // the f member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbF = {0x1.e90026p+4f, -0x1.60549p-4f};

  // the df member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbDf = {0x1p-21f, 0x1p-29f};

  // the reciprocal 1/(1+j/64) is rounded to 24 bits
  private static final double[] ix = {
      0x1p+0, 0x1.f81f82p-1, 0x1.f07c2p-1, 0x1.e9131ap-1,
      0x1.e1e1e2p-1, 0x1.dae608p-1, 0x1.d41d42p-1, 0x1.cd8568p-1,
      0x1.c71c72p-1, 0x1.c0e07p-1, 0x1.bacf92p-1, 0x1.b4e81cp-1,
      0x1.af286cp-1, 0x1.a98ef6p-1, 0x1.a41a42p-1, 0x1.9ec8eap-1,
      0x1.99999ap-1, 0x1.948b1p-1, 0x1.8f9c18p-1, 0x1.8acb9p-1,
      0x1.861862p-1, 0x1.818182p-1, 0x1.7d05f4p-1, 0x1.78a4c8p-1,
      0x1.745d18p-1, 0x1.702e06p-1, 0x1.6c16c2p-1, 0x1.681682p-1,
      0x1.642c86p-1, 0x1.605816p-1, 0x1.5c9882p-1, 0x1.58ed24p-1,
      0x1.555556p-1, 0x1.51d07ep-1, 0x1.4e5e0ap-1, 0x1.4afd6ap-1,
      0x1.47ae14p-1, 0x1.446f86p-1, 0x1.414142p-1, 0x1.3e22ccp-1,
      0x1.3b13b2p-1, 0x1.381382p-1, 0x1.3521dp-1, 0x1.323e34p-1,
      0x1.2f684cp-1, 0x1.2c9fb4p-1, 0x1.29e412p-1, 0x1.27350cp-1,
      0x1.24924ap-1, 0x1.21fb78p-1, 0x1.1f7048p-1, 0x1.1cf06ap-1,
      0x1.1a7b96p-1, 0x1.181182p-1, 0x1.15b1e6p-1, 0x1.135c82p-1,
      0x1.111112p-1, 0x1.0ecf56p-1, 0x1.0c9714p-1, 0x1.0a681p-1,
      0x1.08421p-1, 0x1.0624dep-1, 0x1.041042p-1, 0x1.020408p-1, 0x1p-1};

  // the logarithm of the reciprocal is biased by 0x1.dp-45 so log2p1_fast(x) - log2p1(x) < 0
  private static final double[] lix = {
      0x1.dp-45, -0x1.6e7966ead50c5p-6, -0x1.6bad2043a6a91p-5, -0x1.0eb392fe78f6fp-4,
      -0x1.663f6e3b3bd32p-4, -0x1.bc841cd433853p-4, -0x1.08c587b8a7d19p-3, -0x1.32aea1c2dd96p-3,
      -0x1.5c01a22e687e4p-3, -0x1.84c2be74443dap-3, -0x1.acf5de2afbd5ap-3, -0x1.d49ee012d2a36p-3,
      -0x1.fbc16a1ed1966p-3, -0x1.11307dc445c4cp-2, -0x1.2440796db6523p-2, -0x1.37124a7b0e1dap-2,
      -0x1.49a7834b7d089p-2, -0x1.5c01a2e712f36p-2, -0x1.6e22207523bcdp-2, -0x1.800a59ccb4b43p-2,
      -0x1.91bba6c447a2fp-2, -0x1.a3375ec336f01p-2, -0x1.b47ebfcfdd0dap-2, -0x1.c592fb2eea99p-2,
      -0x1.d6753b20857bp-2, -0x1.e726a9208b01ep-2, -0x1.f7a8543486f32p-2, -0x1.03fda781da376p-1,
      -0x1.0c104f268ec39p-1, -0x1.140c9fb5a8dafp-1, -0x1.1bf31371c6a2p-1, -0x1.23c41b2f88f63p-1,
      -0x1.2b803302a31a2p-1, -0x1.3327c82828c7dp-1, -0x1.3abb40a7ec0afp-1, -0x1.423b07f511315p-1,
      -0x1.49a785d1d0f4ap-1, -0x1.51011934bf518p-1, -0x1.584820b2f56a4p-1, -0x1.5f7cfece7619p-1,
      -0x1.66a00716cedc6p-1, -0x1.6db194ce2d40ap-1, -0x1.74b1fcac361d3p-1, -0x1.7ba1911bb9cf6p-1,
      -0x1.82809cff91ccap-1, -0x1.894f76c358469p-1, -0x1.900e62e869cdfp-1, -0x1.96bdabfeb6a28p-1,
      -0x1.9d5d9dab023d9p-1, -0x1.a3ee7f670bf3cp-1, -0x1.aa708efbac12bp-1, -0x1.b0e414a155bfcp-1,
      -0x1.b74949237d9f7p-1, -0x1.bda06f68b3e6ep-1, -0x1.c3e9ca1704a0fp-1, -0x1.ca258b4fc9ea1p-1,
      -0x1.d053f44c0c9e7p-1, -0x1.d675400a8d4b1p-1, -0x1.dc899d687d98ep-1, -0x1.e29144ae898b8p-1,
      -0x1.e88c6ca77b00ep-1, -0x1.ee7b44ce9bdc6p-1, -0x1.f45e05f15ca47p-1, -0x1.fa34e145a695p-1,
      -0x1.ffffffffffe3p-1};

  private static final double[] b = {
      0x1.7154765bab3edp+0, -0x1.71574d692522fp-1, 0x1.ec60b55c8f05p-2};

  private static final double[] c = {
      0x1.71547652b8314p+0, -0x1.71547652b7f67p-1, 0x1.ec709db872c6dp-2,
      -0x1.715476b06590ep-2, 0x1.277c72c128c69p-2, -0x1.ec4ff30af701bp-3};

  private static final double[] g = {
      0x1.4ae0bf64f73a1p-26, -0x1.71547652b82fap-1, 0x1.ec709dc3bd7dep-2, -0x1.71547652e6faap-2,
      0x1.2776c0ff5c16ep-2, -0x1.ec70942dfbb5bp-3, 0x1.a673c6b6e2fa3p-3, -0x1.71b0db8113c46p-3};

  public static float compute(float x) {
    double z = x;
    int t = Float.floatToRawIntBits(x);
    int ux = t;
    if(Integer.compareUnsigned(ux, 0xbf800000) >= 0) return as_special(x); // x<=-1, x=-inf, x=-nan
    int ax = ux&(~0>>>1);
    if(Integer.compareUnsigned(ax, 0x7f800000) >= 0) return as_special(x); // x=+inf, x=+nan
    if(Integer.compareUnsigned(ax, 0x3cc00000) < 0){ // |x|<0.0234375
      if(Integer.compareUnsigned(ax, 0x58b90b) <= 0){ // |x|<=0x1-126*ln(2)
        if(ax==0) return x; // log2p1(-0.0) = -0.0 and log2p1(+0.0) = +0.0
        // the C source sets errno to ERANGE here
        return (float)(z*0x1.71547652b82fep+0);
      } else {
        double z2 = z*z, z4 = z2*z2;
        double f = z*((g[0] + z*g[1]) + z2*(g[2] + z*g[3]) + z4*((g[4] + z*g[5]) + z2*(g[6] + z*g[7])));
        f += z*0x1.715476p+0; // the product is exact
        return (float)f;
      }
    }
    long tp = Double.doubleToRawLongBits(z + 1.0);
    int e = (int)((tp>>>52) - 0x3ffL);
    long m = tp&(~0L>>>12);
    if(m == 0) return (float)e;  // do not raise the inexact exception for 1+x = 2^n
    int j = (int)((m + (1L<<(52-7)))>>>(52-6));
    long xd = m | 0x3ffL<<52;
    double d = Double.longBitsToDouble(xd)*ix[j] - 1.0, d2 = d*d, el = e - lix[j];
    // d is exact for x < 0x1.04p+29
    double f = (el + d*b[0]) + d2*(b[1] + d*b[2]);
    float lb = (float)f, ub = (float)(f + 0x1.661p-32);
    if(lb==ub) return lb;
    for(int i=0;i<2;i++)
      if(ux == Float.floatToRawIntBits(tbX[i])) return tbF[i] + tbDf[i];
    double c0 = c[0] + d*c[1];
    double c2 = c[2] + d*c[3];
    double c4 = c[4] + d*c[5];
    c0 += d2*(c2 + d2*c4);
    f = e + (0x1.dp-45 - lix[j]) + d*c0;
    lb = (float)f;
    return lb;
  }
}
