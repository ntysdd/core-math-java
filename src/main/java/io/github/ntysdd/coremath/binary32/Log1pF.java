/*
 * Correctly rounded log(1+x) for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/log1p/log1pf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_log1pf -> Log1pF.compute
 *
 * Copyright (c) 2024-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly rounded {@code log(1+x)} of a binary32 value, the translation of the
 * CORE-MATH {@code cr_log1pf}.
 *
 * <p>A polynomial in {@code x} below {@code 0x1.1p-6}, then the reduction of
 * {@code 1+x} against the {@code x0} and {@code lix} tables of 32 entries, with
 * the {@code c} polynomial and, when the two bounds disagree, the {@code b}
 * polynomial and the final adjustment of the last bit.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code as_special} becomes {@code asSpecial}. Its {@code -1.0f/0.0f} and
 *       {@code 0.0f/0.0f} become {@link Float#NEGATIVE_INFINITY} and
 *       {@link Float#NaN}, and the {@code errno} assignments are dropped, since
 *       no caller can observe {@code errno}.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become
 *       {@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; {@code r}, the union that the C source
 *       rounds into, is carried as a {@code double} and its bits are read
 *       through {@link Double#doubleToRawLongBits}.
 *   <li>{@code static const} tables declared inside the function become fields:
 *       {@code x0}, {@code lix}, {@code b} and {@code c}.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma} with {@code float}
 *       arguments, {@code __builtin_copysignf} and {@code __builtin_fabsf} are
 *       {@link Math#copySign} and {@link Math#abs}, and
 *       {@code __builtin_expect} is dropped.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned} and {@code uint32_t} shifts are
 *       {@code >>>}; {@code uint64_t} arithmetic keeps its bit pattern in a
 *       {@code long}.
 *   <li>Where the C code narrows a {@code double} to {@code float} the cast is
 *       explicit.
 * </ul>
 */
public final strictfp class Log1pF {
  private Log1pF() {
  }

  private static float asSpecial(float x) {
    int t = Float.floatToRawIntBits(x);
    if(t == 0xbf800000){// +0.0
      // the C source sets errno to ERANGE here
      return Float.NEGATIVE_INFINITY; // -1.0f/0.0f in the C source, to raise FE_DIVBYZERO
    }
    if(t == 0x7f800000) return x; // +inf
    int ax = t<<1;
    if(Integer.compareUnsigned(ax, 0xff000000) > 0) return x + x; // nan
    // the C source sets errno to EDOM here
    return Float.NaN; // 0.0f/0.0f in the C source, to raise FE_INVALID
  }

  private static final double[] x0 = {
      0x1.f81f80p-1, 0x1.e9131cp-1, 0x1.dae608p-1, 0x1.cd8568p-1, 0x1.c0e070p-1, 0x1.b4e81cp-1,
      0x1.a98ef8p-1, 0x1.9ec8e8p-1, 0x1.948b10p-1, 0x1.8acb90p-1, 0x1.818180p-1, 0x1.78a4c8p-1,
      0x1.702e04p-1, 0x1.681680p-1, 0x1.605818p-1, 0x1.58ed24p-1, 0x1.51d080p-1, 0x1.4afd6cp-1,
      0x1.446f88p-1, 0x1.3e22ccp-1, 0x1.381380p-1, 0x1.323e34p-1, 0x1.2c9fb4p-1, 0x1.27350cp-1,
      0x1.21fb78p-1, 0x1.1cf06cp-1, 0x1.181180p-1, 0x1.135c80p-1, 0x1.0ecf58p-1, 0x1.0a6810p-1,
      0x1.0624dcp-1, 0x1.020408p-1};

  private static final double[] lix = {
      0x1.fc0b0b1599ce4p-7, 0x1.77457a64a42abp-5, 0x1.341d74627847dp-4, 0x1.a926d8a568810p-4,
      0x1.0d77e8cd667aap-3, 0x1.44d2b38d15679p-3, 0x1.7ab886a16b2b3p-3, 0x1.af3c9b686996dp-3,
      0x1.e27075e30cc37p-3, 0x1.0a3250a767d98p-2, 0x1.229423bd2662ep-2, 0x1.3a64c596c3292p-2,
      0x1.51aadd530e505p-2, 0x1.686c85e9e0177p-2, 0x1.7eaf7df859caep-2, 0x1.94793ee2403b3p-2,
      0x1.a9cec5a9cf512p-2, 0x1.beb4d3baa086fp-2, 0x1.d32fe2a03d8b5p-2, 0x1.e744257d97431p-2,
      0x1.faf58cf7bdfe7p-2, 0x1.0723e6d1e5599p-1, 0x1.109f3b52ec2f3p-1, 0x1.19ee6a7693fc5p-1,
      0x1.23130d9c03597p-1, 0x1.2c0e9cc4604f1p-1, 0x1.34e28bd9e5837p-1, 0x1.3d9028a72cd5fp-1,
      0x1.4618b9c1dd52dp-1, 0x1.4e7d825b8d20bp-1, 0x1.56bf9fab56a02p-1, 0x1.5ee02ab258ccap-1};

  private static final double[] b =
      {0x1p+0, -0x1p-1, 0x1.5555555556f6bp-2, -0x1.00000000029b9p-2,
       0x1.9999988d176e4p-3, -0x1.55555418889a7p-3, 0x1.24adeca50e2bcp-3, -0x1.001ba33bf57cfp-3};

  private static final double[] c =
      {0x1.ffffffe1eac82p-1, -0x1.ffffff7da1724p-2, 0x1.5564d8fa59d0cp-2, -0x1.001219d3dba2ap-2};

  public static float compute(float x) {
    double z = x;
    int t = Float.floatToRawIntBits(x);
    int ux = t;
    if(Integer.compareUnsigned(ux, 0xbf800000) >= 0) return asSpecial(x); // x<=-1, x=-inf, x=-nan
    int ax = ux&(~0>>>1);
    if(Integer.compareUnsigned(ax, 0x7f800000) >= 0) return asSpecial(x); // x=+inf, x=+nan
    if(Integer.compareUnsigned(ax, 0x3c880000) < 0){ // |x| < 0x1.1p-6
      if(Integer.compareUnsigned(ax, 0x33000000) < 0){ // |x| < 0x1p-25
        if(ax == 0) return x;
        float res = Math.fma(x,-x,x);
        /* log1p(x) underflows exactly when |res| < 2^-126
           and for x=-0x1.fffffcp-127 and rounding downwards */
        // the C source sets errno to ERANGE here on underflow
        return res;
      }
      double z2 = z*z, z4 = z2*z2;
      double f = z2*((b[1] + z*b[2]) + z2*(b[3] + z*b[4]) + z4*(b[5] + z*(b[6] + z*b[7])));
      double r = z + f;
      if((Double.doubleToRawLongBits(r)&0xfffffffL) == 0) r += 0x1p14*(f + (z - r));
      return (float)r;
    } else {
      long tp = Double.doubleToRawLongBits(z + 1.0);
      int e = (int)((tp>>>52)-0x3ff);
      long m52 = tp&(~0L>>>12);
      int j = (int)((tp>>>(52-5))&31);
      double xd = Double.longBitsToDouble(m52 | (0x3ffL<<52));
      z = xd*x0[j] - 1; // z is exact for x<0x1.0cp+30
      double ln2 = 0x1.62e42fefa39efp-1;
      double z2 = z*z, r = (ln2*e + lix[j]) + z*((c[0] + z*c[1]) + z2*(c[2] + z*c[3]));
      double eps = 2.1555e-11;
      float ub = (float)r, lb = (float)(r - eps);
      if(ub != lb){
        double z4 = z2*z2, f = z2*((b[1] + z*b[2]) + z2*(b[3] + z*b[4]) + z4*(b[5] + z*(b[6] + z*b[7])));
        double lj = lix[j] - 0x1.7654p-37; // subtract the offset
        double ln2l = 0x1.7f7d1cf79abcap-20, ln2h = 0x1.62e4p-1;
        double Lh = ln2h * e, Ll = ln2l * e;
        Ll += z;
        double rh = Lh + lj, rl = ((Lh - rh) + lj) + (Ll + f);
        float fh = (float)(rh + rl);
        double Fl = (rh - (double)fh) + rl;
        float fl = (float)Fl, tfl = fl*2.0f;
        if((fh + tfl)-fh == tfl)
          fl += Math.copySign(0.5f,(float)(Fl-(double)fl))*Math.abs(fl);
        ub = fh + fl;
      }
      return ub;
    }
  }
}
