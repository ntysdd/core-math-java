/*
 * Correctly-rounded biased argument base-10 logarithm function for binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/log10p1/log10p1f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_log10p1f -> Log10P1F.compute
 *
 * Copyright (c) 2022-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly rounded {@code log10(1+x)} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_log10p1f}.
 *
 * <p>{@code as_special} answers {@code x <= -1}, the infinities and the NaNs;
 * the exact powers of ten below {@code 10^7} are looked up in {@code st}; the
 * rest reduces {@code 1+x} against the reciprocal table {@code tr} and the
 * shifted logarithm table {@code tl} and evaluates the degree-3 polynomial
 * {@code b}, with a bound on the error that accepts the result when it holds.
 * When it does not, four arguments of {@code tb} are named explicitly, the
 * degree-8 polynomial {@code c} is evaluated instead, and the band
 * {@code |x| < 0x1.2p-5} has its own {@code c0h}/{@code c0l} start.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Float#intBitsToFloat}/{@link Double#doubleToRawLongBits};
 *       {@code ux}, {@code t}, {@code m}, {@code tz} and {@code su} are bit
 *       patterns.
 *   <li>{@code __attribute__((noinline))} is nothing, {@code __builtin_expect}
 *       is nothing, and the {@code #pragma STDC FENV_ACCESS ON} is dropped.
 *       The {@code feraiseexcept} calls of {@code as_special} and its
 *       {@code errno} assignments are dropped: Java exposes no
 *       floating-point environment and no {@code errno}. The two results
 *       {@code as_special} writes through the union stay bit patterns,
 *       {@code 0x1ffu<<23} for {@code -Infinity} and {@code 0x1ff8u<<19} for
 *       the NaN of {@code x < 0}.
 *   <li>The {@code struct {b32u32_u x; float f, df;} tb[]} becomes the three
 *       columns {@code tbX}, {@code tbF} and {@code tbDf}, since both the bit
 *       pattern of {@code x} and the {@code float} members are read; the
 *       {@code union {float f; uint32_t u;} st[]} becomes a {@code float[]}
 *       with {@link Float#floatToRawIntBits} at the comparison, because only
 *       its bit pattern is read.
 *   <li>The {@code static const} tables declared inside the function become
 *       fields, in the order of the C source: {@code tbX}, {@code tbF},
 *       {@code tbDf}, {@code tr}, {@code tl}, {@code st}, {@code b} and
 *       {@code c}, and so do the {@code static const double c0h, c0l} of the
 *       {@code |x| < 0x1p-25} band.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, and the {@code unsigned je} of the
 *       exact powers of ten keeps the wrapping arithmetic and the logical
 *       shift of the C source.
 *   <li>The {@code int32_t e} of the reduction is an {@code int} built from
 *       the {@code long} exponent field, and {@code (0x3ffull<<52)} keeps the
 *       {@code long} type of the union it is stored into.
 *   <li>Where the C code narrows a {@code double} to {@code float} the cast is
 *       explicit: {@code ub}, {@code lb}, the {@code r} of the
 *       {@code |x| < 0x1p-25} band and the final {@code ub = f}.
 * </ul>
 */
public final strictfp class Log10P1F {
  private Log10P1F() {
  }

  private static float as_special(float x) {
    int t = Float.floatToRawIntBits(x);
    if(t == 0xbf800000){// -1.0
      // the C source sets errno to ERANGE here and raises FE_DIVBYZERO
      t = 0x1ff<<23;
      return Float.intBitsToFloat(t);
    }
    if(t == 0x7f800000) return x; // +inf
    int ax = t<<1;
    if(Integer.compareUnsigned(ax, 0xff000000) > 0) return x + x; // nan
    // the C source sets errno to EDOM here and raises FE_INVALID
    t = 0x1ff8<<19;
    return Float.intBitsToFloat(t); // x<0
  }

  // the x member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbX =
      {0x1.34f8p-12f, -0x1.a3c2e6p-31f, -0x1.1d4db2p-9f, -0x1.0dff72p-4f};

  // the f member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbF =
      {0x1.0c53cap-13f, -0x1.6c999ep-32f, -0x1.f029c4p-11f, -0x1.e53536p-6f};

  // the df member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbDf = {0x1p-38f, 0x1p-57f, 0x1p-36f, -0x1p-31f};

  // reciprocal of 1+(j+0.5)/64 rounded to 24 bits
  private static final double[] tr = {
      0x1.fc07fp-1, 0x1.f4465ap-1, 0x1.ecc07cp-1, 0x1.e573acp-1, 0x1.de5d6ep-1, 0x1.d77b66p-1,
      0x1.d0cb58p-1, 0x1.ca4b3p-1, 0x1.c3f8fp-1, 0x1.bdd2b8p-1, 0x1.b7d6c4p-1, 0x1.b20364p-1,
      0x1.ac5702p-1, 0x1.a6d01ap-1, 0x1.a16d4p-1, 0x1.9c2d14p-1, 0x1.970e5p-1, 0x1.920fb4p-1,
      0x1.8d3018p-1, 0x1.886e6p-1, 0x1.83c978p-1, 0x1.7f406p-1, 0x1.7ad22p-1, 0x1.767dcep-1,
      0x1.724288p-1, 0x1.6e1f76p-1, 0x1.6a13cep-1, 0x1.661ec6p-1, 0x1.623fa8p-1, 0x1.5e75bcp-1,
      0x1.5ac056p-1, 0x1.571ed4p-1, 0x1.539094p-1, 0x1.501502p-1, 0x1.4cab88p-1, 0x1.49539ep-1,
      0x1.460cbcp-1, 0x1.42d662p-1, 0x1.3fb014p-1, 0x1.3c995ap-1, 0x1.3991c2p-1, 0x1.3698ep-1,
      0x1.33ae46p-1, 0x1.30d19p-1, 0x1.2e025cp-1, 0x1.2b404ap-1, 0x1.288b02p-1, 0x1.25e228p-1,
      0x1.234568p-1, 0x1.20b47p-1, 0x1.1e2ef4p-1, 0x1.1bb4a4p-1, 0x1.194538p-1, 0x1.16e068p-1,
      0x1.1485fp-1, 0x1.12358ep-1, 0x1.0fef02p-1, 0x1.0db20ap-1, 0x1.0b7e6ep-1, 0x1.0953f4p-1,
      0x1.07326p-1, 0x1.05198p-1, 0x1.03091cp-1, 0x1.010102p-1};

  // logarithm of the reciprocals biased by 0x1.58ep-43
  private static final double[] tl = {
      0x1.bafd550786257p-9, 0x1.49b08209ec64p-7, 0x1.10a82eca6416p-6, 0x1.7adc46340fc4p-6,
      0x1.e3806e7ccbebp-6, 0x1.255026bdcb233p-5, 0x1.5823964d3c8dcp-5, 0x1.8a3fb08692a5fp-5,
      0x1.bba9a137364d7p-5, 0x1.ec664cb24c458p-5, 0x1.0e3d294d154ccp-4, 0x1.25f5217a7e5dfp-4,
      0x1.3d5d3200e0e36p-4, 0x1.547774e40ea5ap-4, 0x1.6b45ddb283c9cp-4, 0x1.81ca67d4c05e1p-4,
      0x1.9806d71561d18p-4, 0x1.adfd09f848345p-4, 0x1.c3aea856ca97fp-4, 0x1.d91d540edcaep-4,
      0x1.ee4ab8dbebb39p-4, 0x1.019c29971c034p-3, 0x1.0bf3d1e104ae2p-3, 0x1.162d08ca9a7bep-3,
      0x1.204881c31ba38p-3, 0x1.2a46ea803f1cbp-3, 0x1.3428e0134104bp-3, 0x1.3def1007f5f74p-3,
      0x1.479a066a5fa87p-3, 0x1.512a631ebcdecp-3, 0x1.5aa0b67441c74p-3, 0x1.63fd84e6e41dep-3,
      0x1.6d41602642abbp-3, 0x1.766cc236f8424p-3, 0x1.7f80367f521b3p-3, 0x1.887c2f002ebc7p-3,
      0x1.916128c710c17p-3, 0x1.9a2f9609731f8p-3, 0x1.a2e7e853a516cp-3, 0x1.ab8a901fe9635p-3,
      0x1.b417f6bfa9e71p-3, 0x1.bc907da9eace3p-3, 0x1.c4f494e895d78p-3, 0x1.cd44987634191p-3,
      0x1.d580e68cc0a87p-3, 0x1.dda9df79b84d2p-3, 0x1.e5bfd37200ee8p-3, 0x1.edc325e4a0f81p-3,
      0x1.f5b4297735a7cp-3, 0x1.fd93318156892p-3, 0x1.02b042b675879p-2, 0x1.068e3fa975162p-2,
      0x1.0a63b3535192bp-2, 0x1.0e30c45ab291fp-2, 0x1.11f595eceef01p-2, 0x1.15b24abf1aeb1p-2,
      0x1.196704f31e753p-2, 0x1.1d13ec95021b6p-2, 0x1.20b91bd192db3p-2, 0x1.2456b26c914d5p-2,
      0x1.27ecd5ea10d93p-2, 0x1.2b7b9d4731bd4p-2, 0x1.2f032bca9e44bp-2, 0x1.32839caee0d8ap-2};

  // static const union{float f; uint32_t u;} st[] inside cr_log10p1f()
  private static final float[] st = {
      0x125p+0f, 0x1.2p+3f, 0x1.8cp+6f, 0f, 0x1.f38p+9f, 0f, 0x1.3878p+13f, 0x1.869fp+16f,
      0f, 0x1.e847ep+19f, 0f, 0x1.312cfep+23f, 0f, 0f, 0f, 0f};

  private static final double[] b = {
      0x1.bcb7b150bf33dp-2, -0x1.bcb7b14b2164ep-3, 0x1.287de1f406bedp-3, -0x1.bcbfad32135bdp-4};

  private static final double[] c = {
      0x1.bcb7b1526e50ep-2, -0x1.bcb7b1526e48ep-3, 0x1.287a7636f422fp-3, -0x1.bcb7b15514181p-4,
      0x1.63c62778ff0d1p-4, -0x1.287a581961505p-4, 0x1.fc3f60b6c20a5p-5, -0x1.bdb55f5990c49p-5,
      0x1.8c4ba9c7c0692p-5};

  // static const double c0h, c0l inside cr_log10p1f() in the C source
  private static final double c0h = 0x1.bcb7b15p-2;
  private static final double c0l = 0x1.37287195355bbp-33;

  public static float compute(float x) {
    double ln10 = 0x1.34413509f79ffp-2, ln10h = 0x1.34413509f8p-2, ln10l = -0x1.80433b83b532ap-44;
    double z = x;
    int t = Float.floatToRawIntBits(x);
    int ux = t;
    if(Integer.compareUnsigned(ux, 0xbf800000) >= 0) return as_special(x); // x <= -1, -inf, -nan
    if(ux >= 0x7f800000) return as_special(x); // +inf, +nan
    if(ux == Float.floatToRawIntBits(st[(ux>>>24)&0xf])) {
      int ie = ux;
      ie >>= 23;
      int je = ie - 126;
      je = (je*0x9a209a8)>>>29;
      return (float)je;
    }
    long tz = Double.doubleToRawLongBits(z + 1.0);
    long m = tz&(~0L>>>12);
    int e = (int)((tz>>>52) - 1023), j = (int)(m>>>46);
    tz = m | (0x3ffL<<52);
    if(m == 0) {
      if(ux == 0 || ux == 0x80000000) return x; // return signed zero
    }
    double v = Double.longBitsToDouble(tz)*tr[j] - 1, v2 = v*v;
    double f = (e*ln10 + tl[j]) + v*((b[0] + v*b[1]) + v2*(b[2] + v*b[3]));
    float ub = (float)f, lb = (float)(f + 0x1.56ap-42);
    if(ub != lb){
      for(int i=0;i<4;i++)
        if(ux == Float.floatToRawIntBits(tbX[i])) return tbF[i] + tbDf[i];
      double lj = tl[j] + 0x1.58ep-43;
      int ax = ux&(~0>>>1);
      if(Integer.compareUnsigned(ax, 0x3d100000) < 0){ // |x| < 0x1.2p-5
        if(Integer.compareUnsigned(ax, 0x33000000) < 0){ // |x| < 0x1p-25
          float r = (float)(z*c0h + z*(c0l + z*c[1]));
          // the C source sets errno to ERANGE here for |x| <= 0x1-126*ln(10)
          return r;
        }
        e = 0;
        v = x;
        v2 = v*v;
        lj = 0.0;
      }
      double v4 = v2*v2;
      f = v*(((c[0] + v*c[1]) + v2*(c[2] + v*c[3])) + v4*((c[4] + v*c[5]) + v2*((c[6] + v*c[7]) + v2*c[8])));
      f += e*ln10l;
      f += lj;
      f += e*ln10h;
      ub = (float)f;
    }
    return ub;
  }
}
