/*
 * Correctly rounded atanh(x) for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/atanh/atanhf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atanhf -> AtanhF.compute
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
 * Correctly rounded {@code atanh(x)} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_atanhf}.
 *
 * <p>The file works on the difference of two logarithms,
 * {@code atanh(x) = (log(1+x) - log(1-x))/2}: it reduces {@code 1+x} and
 * {@code 1-x} to a {@code zn}/{@code zd} pair by rounding their significands
 * onto a 26-bit grid, reads {@code tr}, {@code tl} and {@code ln2n} at the
 * resulting indices, and refines with the {@code cp} polynomial when the two
 * bounds disagree. Below {@code 0x1.5a3116p-5} the result is
 * {@code fma(x, 2^-25, x)}, with a four-coefficient polynomial up to
 * {@code 0x1.3p-5}, and {@code asSpecial} answers the arguments at and beyond
 * one.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code 0.0f/0.0f}, which {@code asSpecial} uses to return a NaN and
 *       raise {@code FE_INVALID}, is written as {@link Float#NaN}; the pole
 *       {@code x/0.0f} keeps its shape, since a Java division by zero yields
 *       the infinities of the right sign without raising anything. The
 *       {@code errno} assignments inside the {@code CORE_MATH_SUPPORT_ERRNO}
 *       blocks are dropped.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}: {@code ux} and {@code ax} are
 *       {@code int} bit patterns and {@code tn}/{@code td} are {@code long}.
 *       {@code __builtin_expect} is dropped.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma}, {@code __builtin_fabsf} is
 *       {@link Math#abs} and {@code __builtin_clz} is
 *       {@link Integer#numberOfLeadingZeros}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned} and {@code uint32_t} shifts are
 *       {@code >>>}, including the {@code ((ux<<8)|1ul<<31)>>(126-e)} of the
 *       significand reduction; {@code (uint64_t)(((int64_t)mn<<20) |
 *       ((int64_t)1023<<52))} is the {@code long} expression
 *       {@code ((long)mn<<20) | ((long)1023<<52)}.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code float ub = r}, {@code lb = r + sgn*0.226e-9} and
 *       {@code ub = sgn*r}) the cast is explicit.
 *   <li>{@code static const} tables declared inside the function become
 *       fields: {@code tr}, {@code tl}, {@code ln2n}, {@code b}, {@code s} and
 *       the two tables named {@code c}, which are {@code cSmall} (four
 *       coefficients) and {@code cLarge} (seven).
 * </ul>
 */
public final strictfp class AtanhF {
  private AtanhF() {
  }

  private static final double[] tr = {
       0x1.fc07f02p-1, 0x1.f44659ep-1, 0x1.ecc07b3p-1, 0x1.e573ac9p-1, 0x1.de5d6e4p-1,
       0x1.d77b655p-1, 0x1.d0cb58fp-1, 0x1.ca4b305p-1, 0x1.c3f8f02p-1, 0x1.bdd2b8ap-1,
       0x1.b7d6c3ep-1, 0x1.b20364p-1, 0x1.ac5701bp-1, 0x1.a6d01a7p-1, 0x1.a16d3f9p-1,
       0x1.9c2d14fp-1, 0x1.970e4f8p-1, 0x1.920fb4ap-1, 0x1.8d3018dp-1, 0x1.886e5f1p-1,
       0x1.83c977bp-1, 0x1.7f405fdp-1, 0x1.7ad2209p-1, 0x1.767dce4p-1, 0x1.724287fp-1,
       0x1.6e1f76bp-1, 0x1.6a13cd1p-1, 0x1.661ec6ap-1, 0x1.623fa77p-1, 0x1.5e75bb9p-1,
       0x1.5ac056bp-1, 0x1.571ed3cp-1, 0x1.5390949p-1, 0x1.5015015p-1, 0x1.4cab887p-1,
       0x1.49539e4p-1, 0x1.460cbc8p-1, 0x1.42d6626p-1, 0x1.3fb014p-1, 0x1.3c995a4p-1,
       0x1.3991c2cp-1, 0x1.3698df4p-1, 0x1.33ae45bp-1, 0x1.30d1901p-1, 0x1.2e025cp-1,
       0x1.2b404adp-1, 0x1.288b013p-1, 0x1.25e2271p-1, 0x1.2345679p-1, 0x1.20b470cp-1,
       0x1.1e2ef3bp-1, 0x1.1bb4a4p-1, 0x1.1945381p-1, 0x1.16e0689p-1, 0x1.1485f0ep-1,
       0x1.12358e7p-1, 0x1.0fef011p-1, 0x1.0db20a9p-1, 0x1.0b7e6ecp-1, 0x1.0953f39p-1,
       0x1.073260ap-1, 0x1.05197f8p-1, 0x1.03091b5p-1, 0x1.010101p-1};

  private static final double[] tl = {
       0x1.fe02a69106789p-9, 0x1.7b91b1155b11bp-7, 0x1.39e87ba1ebd6p-6, 0x1.b42dd713971bfp-6,
       0x1.16536ee637ae1p-5, 0x1.51b073c96183fp-5, 0x1.8c345da019b21p-5, 0x1.c5e5492abc743p-5,
       0x1.fec912fbbeabbp-5, 0x1.1b72ad33f67ap-4, 0x1.371fc1f6e8f74p-4, 0x1.526e5e5a1b438p-4,
       0x1.6d60fe601d21dp-4, 0x1.87fa06438c911p-4, 0x1.a23bc223ab563p-4, 0x1.bc28673a58cd6p-4,
       0x1.d5c216b8fbb91p-4, 0x1.ef0adcaec5936p-4, 0x1.040259530d041p-3, 0x1.1058bf8d24ad5p-3,
       0x1.1c898c09d99fbp-3, 0x1.2895a13e286a3p-3, 0x1.347dd9a447d55p-3, 0x1.404308716a7e4p-3,
       0x1.4be5f963b78a1p-3, 0x1.5767718015a6cp-3, 0x1.62c82f3a5c795p-3, 0x1.6e08eab13a1e4p-3,
       0x1.792a55fe147a2p-3, 0x1.842d1d9928b17p-3, 0x1.8f11e873a62c7p-3, 0x1.99d958207e08bp-3,
       0x1.a484090c1bb0ap-3, 0x1.af129324b786bp-3, 0x1.b9858970710fbp-3, 0x1.c3dd7a6ddad4dp-3,
       0x1.ce1af0b65f3ebp-3, 0x1.d83e725022f3ep-3, 0x1.e2488197c6c26p-3, 0x1.ec399d3d68ccp-3,
       0x1.f6123fac028acp-3, 0x1.ffd2e07e7f498p-3, 0x1.04bdf9e3b26d2p-2, 0x1.0986f4fa93521p-2,
       0x1.0e4498651cc8cp-2, 0x1.12f719595efbcp-2, 0x1.179eabb0a99a1p-2, 0x1.1c3b81e933c25p-2,
       0x1.20cdcd0e0ab6ep-2, 0x1.2555bcf50f7cbp-2, 0x1.29d37ff34b08bp-2, 0x1.2e47437640268p-2,
       0x1.32b1338401d71p-2, 0x1.37117b5c147b6p-2, 0x1.3b6844a13fc23p-2, 0x1.3fb5b857f6f42p-2,
       0x1.43f9fe2f7ce67p-2, 0x1.48353d11488dfp-2, 0x1.4c679b014ee3ap-2, 0x1.50913cc03686bp-2,
       0x1.54b2468259498p-2, 0x1.58cadb57d7989p-2, 0x1.5cdb1dcaa1765p-2, 0x1.60e32f46788d9p-2};

  private static final double[] ln2n = {
       0x1.62e42fedb2a44p-2, 0x1.62e42feeab21ap-1, 0x1.0a2b23f33e789p+0, 0x1.62e42fef27604p+0,
       0x1.bb9d3beb1048p+0, 0x1.0a2b23f37c97ep+1, 0x1.3687a9f1710bcp+1, 0x1.62e42fef657fap+1,
       0x1.8f40b5ed59f38p+1, 0x1.bb9d3beb4e676p+1, 0x1.e7f9c1e942db4p+1, 0x1.0a2b23f39ba79p+2,
       0x1.205966f295e18p+2, 0x1.3687a9f1901b7p+2, 0x1.4cb5ecf08a556p+2, 0x1.62e42fef848f5p+2,
       0x1.791272ee7ec93p+2, 0x1.8f40b5ed79032p+2, 0x1.a56ef8ec733d1p+2, 0x1.bb9d3beb6d77p+2,
       0x1.d1cb7eea67b0fp+2, 0x1.e7f9c1e961eaep+2, 0x1.fe2804e85c24dp+2, 0x1.0a2b23f3ab2f6p+3};

  private static final double[] b = {
       0x1.fffffffce5a6ap-2, -0x1.0001f81ec0ab8p-2, 0x1.555a0f53d79a5p-3};

  private static final double[] s = {1, -1};

  private static final double[] cSmall = {
       0x1.5555555555527p-2, 0x1.9999999ba4ee8p-3, 0x1.24922c280990ap-3, 0x1.c8236aae809c6p-4};

  private static final double[] cLarge = {
       0x1p-1, -0x1.000000000001bp-2, 0x1.55555555555bap-3, -0x1.fffffff26d72ep-4,
       0x1.99999989035p-4, -0x1.555c39cb9ee8p-4, 0x1.24992d8b014a1p-4};

  private static float asSpecial(float x) {
    int t = Float.floatToRawIntBits(x);
    int ax = t<<1;
    if(Integer.compareUnsigned(ax, 0x7f000000) == 0){ // +-1
      return x/0.0f; // to raise FE_DIVBYZERO
    }
    if(Integer.compareUnsigned(ax, 0xff000000) > 0) return x + x; // nan
    // C returns 0.0f/0.0f here to raise FE_INVALID
    return Float.NaN;
  }

  public static float compute(float x) {
    // Calculate atanh(x) using the difference of two logarithms -- atanh(x) = (ln(1+x) - ln(1-x))/2
    int t = Float.floatToRawIntBits(x);
    int ux = t, ax = ux<<1;
    if(Integer.compareUnsigned(ax, 0x7a300000) < 0 || Integer.compareUnsigned(ax, 0x7f000000) >= 0){
      // |x| < 0x1.5a3116p-5 or x is NaN/Inf
      if(Integer.compareUnsigned(ax, 0x7f000000) >= 0) return asSpecial(x); // NaN/Inf
      if(Integer.compareUnsigned(ax, 0x73713744) < 0) { // |x| < 0x1.f5a956p-12
        if(ax == 0) return x; // x = +-0
        /* The Taylor expansion of atanh(x) at x=0 is x + x^3/3 + o(x^3),
           thus for |x| >= 2^-126 we have no underflow, whatever the
           rounding mode.
           For |x| < 2^-126 and rounding towards zero, we have underflow.
           For x = nextbelow(2^-126) = 0x1.fffffcp-127, atanh(x) would round
           upward to 0x1.fffffep-127 with unbounded exponent range, which is not
           representable, thus we have underflow too.
           In summary, we have underflow whenever |x| < 2^-126. */
        return Math.fma(x, 0x1p-25f, x); // |x| < 0.000352112(0x1.713744p-12)
      } else { // |x| < 0x1.3p-5
        double z = x, z2 = z*z, z4 = z2*z2;
        double r = cSmall[0] + z2*cSmall[1] + z4*(cSmall[2] + z2*cSmall[3]);
        return (float) (z + (z*z2)*r);
      }
    }
    double sgn = s[ux>>>31];
    int e = ax>>>24, md = ((ux<<8)|1<<31)>>>(126-e), mn = -md;
    int nz = Integer.numberOfLeadingZeros(mn) + 1;
    mn <<= nz;
    int jn = mn>>>26, jd = md>>>26;
    long tn = Integer.toUnsignedLong(mn)<<20 | ((long)1023<<52);
    long td = Integer.toUnsignedLong(md)<<20 | ((long)1023<<52);
    double zn = Double.longBitsToDouble(tn)*tr[jn] - 1, zd = Double.longBitsToDouble(td)*tr[jd] - 1;
    double zn2 = zn*zn, zd2 = zd*zd;
    double rn = ((tl[jn] - ln2n[nz-1]) + zn*b[0]) + zn2*(b[1] + zn*b[2]);
    double rd = (tl[jd] + zd*b[0]) + zd2*(b[1] + zd*b[2]);
    double r = sgn*(rd - rn);
    float ub = (float) r, lb = (float) (r + sgn*0.226e-9);
    if(ub != lb){
      double zn4 = zn2*zn2, zd4 = zd2*zd2;
      double fn = zn*(((cLarge[0] + zn*cLarge[1]) + zn2*(cLarge[2] + zn*cLarge[3]))
          + zn4*((cLarge[4] + zn*cLarge[5]) + zn2*cLarge[6]));
      fn += 0x1.0ca86c3898dp-50*nz;
      fn += tl[jn];
      double en = nz*0x1.62e42fefa3ap-2;
      double fd = zd*(((cLarge[0] + zd*cLarge[1]) + zd2*(cLarge[2] + zd*cLarge[3]))
          + zd4*((cLarge[4] + zd*cLarge[5]) + zd2*cLarge[6]));
      fd += tl[jd];
      r = fd - fn + en;
      ub = (float) (sgn*r);
    }
    return ub;
  }
}
