/*
 * Correctly rounded arctangent of y/x for binary32 values, in half-revolution
 * units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/atan2pi/atan2pif.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atan2pif -> Atan2PiF.compute
 *
 * Copyright (c) 2023-2025 Paul Zimmermann
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
 * Correctly rounded {@code atan2(y, x)/pi} of two binary32 values, the
 * translation of the CORE-MATH {@code cr_atan2pif}.
 *
 * <p>The fast path divides the smaller magnitude by the larger in double
 * precision, evaluates a rational approximation of degree 6 over 6 on that
 * ratio, multiplies by the ratio and adds the quadrant offset {@code off[i]};
 * when the result is close enough to a boundary for the last bits to matter,
 * the division and the polynomial are redone in double-double with the 32-row
 * table {@code c}, and the result is nudged with the {@code 0x1p-10} trick of
 * the last block. The quadrant tables {@code m}, {@code off}, {@code sgnf},
 * {@code sgn}, {@code s} and {@code off2} select the signs.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} and {@code b64u64_u} become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Double#doubleToRawLongBits}; {@code ~0u>>1} is {@code ~0 >>> 1},
 *       and the {@code uy>>31} and {@code ux>>31} sign tests are
 *       {@code >>> 31}.
 *   <li>{@code uint32_t yinf}, {@code xinf} and {@code gt} are only tested, so
 *       they are {@code boolean}; the {@code gt} that also indexes {@code m} is
 *       written {@code gt ? 1 : 0}.
 *   <li>{@code (res.u<<1) > 0x6d40000000000000} is an unsigned comparison, so
 *       it goes through {@link Long#compareUnsigned};
 *       {@code ((res.u+8)&0xfffffff) <= 16} is masked to 28 bits and stays
 *       signed. {@code !(d.u<<12)} is {@code (d << 12) == 0}.
 *   <li>The {@code CORE_MATH_SUPPORT_ERRNO} block of the {@code 0x1p-126f}
 *       underflow is dropped, there is no {@code errno} in Java.
 *   <li>{@code __builtin_expect} is dropped, the JIT profiles the branches
 *       itself; {@code __builtin_fma} and {@code __builtin_fabs} are
 *       {@link Math#fma} and {@link Math#abs}.
 *   <li>{@code double *} output parameters become one-element {@code double}
 *       arrays: {@code l} of {@code muldd}, {@code cl} and {@code l} of
 *       {@code polydd}, and {@code zh}, {@code zl}, {@code z2l}, {@code pl}.
 *   <li>{@code const double *c} rows of the double-double table are kept as
 *       {@code double[][] c}, one row per entry.
 *   <li>The final {@code float rf = r} is the explicit {@code (float)} cast of
 *       the double result.
 * </ul>
 */
public final strictfp class Atan2PiF {
  private Atan2PiF() {
  }

  private static double muldd(double xh, double xl, double ch, double cl, double[] l) {
    double ahlh = ch*xl, alhh = cl*xh, ahhh = ch*xh, ahhl = Math.fma(ch, xh, -ahhh);
    ahhl += alhh + ahlh;
    ch = ahhh + ahhl;
    l[0] = (ahhh - ch) + ahhl;
    return ch;
  }

  private static double polydd(double xh, double xl, int n, double[][] c, double[] l) {
    int i = n-1;
    double[] cl = new double[1];
    double ch = c[i][0];
    cl[0] = c[i][1];
    while(--i>=0){
      ch = muldd(xh, xl, ch, cl[0], cl);
      double th = ch + c[i][0], tl = (c[i][0] - th) + ch;
      ch = th;
      cl[0] += tl + c[i][1];
    }
    l[0] = cl[0];
    return ch;
  }

  private static final double[] cn = {0x1.45f306dc9c883p-2, 0x1.988d83a142adap-1,
    0x1.747bebf492057p-1, 0x1.2cc5645094ff3p-2, 0x1.a0521c711ab66p-5, 0x1.881b8058b9a0dp-9,
    0x1.b16ff514a0afp-16};

  private static final double[] cd = {0x1p+0, 0x1.6b8b143a3f6dap+1, 0x1.8421201d18ed5p+1,
    0x1.8221d086914ebp+0, 0x1.670657e3a07bap-2, 0x1.0f4951fd1e72dp-5, 0x1.b3874b8798286p-11};

  private static final double[] m = {0, 1};

  private static final double[] off = {0.0f, 0.5f, 1.0f, 0.5f, -0.0f, -0.5f, -1.0f, -0.5f};

  private static final float[] sgnf = {1, -1};

  private static final double[] sgn = {1, -1};

  private static final float[] s = {0.25f, 0.75f, -0.25f, -0.75f};

  private static final double[] off2 = {0.25, 0.75, -0.25, -0.75};

  private static final double[][] c = {
    {0x1.45f306dc9c883p-2,-0x1.6b01ec5513324p-56},
    {-0x1.b2995e7b7b604p-4,0x1.e402b0c13eedcp-58},
    {0x1.04c26be3b06cfp-4,-0x1.571d178a53efp-60},
    {-0x1.7483758e69c03p-5,0x1.819a6ed7aaf38p-63},
    {0x1.21bb9452523ffp-5,-0x1.234d866fb9807p-60},
    {-0x1.da1bace3cc54ep-6,-0x1.c84f6ada49294p-64},
    {0x1.912b1c23345ddp-6,-0x1.534890fbc165p-60},
    {-0x1.5bade52f5f52ap-6,0x1.f783bafc832f6p-60},
    {0x1.32c69d084c5cp-6,0x1.042d155953025p-60},
    {-0x1.127bcfb3e8c7dp-6,-0x1.85aae199a7b6bp-60},
    {0x1.f0af43b11a731p-7,0x1.8f0356356663p-61},
    {-0x1.c57e86801029ep-7,0x1.dcdf3e3b38eb4p-61},
    {0x1.a136408617ea1p-7,0x1.a71affb36c6c4p-63},
    {-0x1.824ac7814ba37p-7,0x1.8928b295c0898p-61},
    {0x1.6794e32ea5471p-7,0x1.0b4334fb41e63p-61},
    {-0x1.501d57f643d97p-7,0x1.516785bf1376ep-61},
    {0x1.3adf02ff2400ap-7,-0x1.b0e30bb8c8076p-62},
    {-0x1.267702f94faap-7,-0x1.7a4d3a1850cc6p-62},
    {0x1.10dce97099686p-7,0x1.fcc208eee2571p-61},
    {-0x1.eee49cdad8002p-8,-0x1.9109b3f1bab82p-64},
    {0x1.af93bc191a929p-8,0x1.069fd3b47d7bp-62},
    {-0x1.6240751b54675p-8,-0x1.72dc8cfd03b6fp-62},
    {0x1.0b61e84080884p-8,0x1.825824c80941bp-63},
    {-0x1.6a72a8a74e3a5p-9,0x1.8786a82fd117ep-63},
    {0x1.aede3217d939dp-10,-0x1.93b626982e1fep-68},
    {-0x1.b66568f09ebeep-11,-0x1.704a39121d0a5p-66},
    {0x1.73af3977fa973p-12,-0x1.aa050e2244ea3p-68},
    {-0x1.fc69d85ed28c9p-14,0x1.867f17b764cap-68},
    {0x1.0c883a9270162p-15,-0x1.6842833896dd9p-70},
    {-0x1.9a0b27b6dfe15p-18,0x1.427fc2f4e1327p-73},
    {0x1.91e15e7ab5bdcp-21,-0x1.730dbc6279d0dp-77},
    {-0x1.7b1119c1ff867p-25,0x1.145f9980759c4p-79},
  };

  public static float compute(float y, float x) {
    int tx = Float.floatToRawIntBits(x), ty = Float.floatToRawIntBits(y);
    int ux = tx, uy = ty, ax = ux & (~0 >>> 1), ay = uy & (~0 >>> 1);
    if (ay >= (0xff << 23) || ax >= (0xff << 23)) {
      if (ay > (0xff << 23)) return x + y; // nan
      if (ax > (0xff << 23)) return x + y; // nan
      boolean yinf = ay == (0xff << 23), xinf = ax == (0xff << 23);
      if (yinf & xinf) {
        if ((ux >>> 31) != 0)
          return 0.75f*sgnf[uy >>> 31];
        else
          return 0.25f*sgnf[uy >>> 31];
      }
      if (xinf) {
        if ((ux >>> 31) != 0)
          return sgnf[uy >>> 31];
        else
          return 0.0f*sgnf[uy >>> 31];
      }
      if (yinf) {
        return 0.5f*sgnf[uy >>> 31];
      }
    }
    if (ay == 0) {
      if ((ay | ax) == 0) {
        int i = (uy >>> 31)*4 + (ux >>> 31)*2;
        return (float) off[i];
      }
      if ((ux >>> 31) == 0) return 0.0f*sgnf[uy >>> 31];
    }
    if (ax == ay) {
      int i = (uy >>> 31)*2 + (ux >>> 31);
      return s[i];
    }
    boolean gt = ay > ax;
    int i = (uy >>> 31)*4 + (ux >>> 31)*2 + (gt ? 1 : 0);

    double zx = x, zy = y;
    int gt1 = gt ? 1 : 0;
    double z = (m[gt1]*zx + m[1 - gt1]*zy)/(m[gt1]*zy + m[1 - gt1]*zx);
    double r = cn[0], z2 = z*z;
    z *= sgn[gt ? 1 : 0];
    // avoid spurious underflow in the polynomial evaluation excluding extremely small arguments
    if (z2 > 0x1p-54) {
      double z4 = z2*z2, z8 = z4*z4;
      double cn0 =     r + z2*cn[1];
      double cn2 = cn[2] + z2*cn[3];
      double cn4 = cn[4] + z2*cn[5];
      double cn6 = cn[6];
      cn0 += z4*cn2;
      cn4 += z4*cn6;
      cn0 += z8*cn4;
      double cd0 = cd[0] + z2*cd[1];
      double cd2 = cd[2] + z2*cd[3];
      double cd4 = cd[4] + z2*cd[5];
      double cd6 = cd[6];
      cd0 += z4*cd2;
      cd4 += z4*cd6;
      cd0 += z8*cd4;
      r = cn0/cd0;
    }
    r = z*r + off[i];
    long res = Double.doubleToRawLongBits(r);
    if (Long.compareUnsigned(res << 1, 0x6d40000000000000L) > 0
        && ((res + 8) & 0xfffffffL) <= 16) {
      // |res| > 0x1p-149
      if (ax == ay) {
        r = off2[(uy >>> 31)*2 + (ux >>> 31)];
      } else {
        double zh, zl;
        if (!gt) {
          zh = zy/zx;
          zl = Math.fma(zh, -zx, zy)/zx;
        } else {
          zh = zx/zy;
          zl = Math.fma(zh, -zy, zx)/zy;
        }
        double[] z2l = new double[1];
        double z2h = muldd(zh, zl, zh, zl, z2l);
        double[] pl = new double[1];
        double ph = polydd(z2h, z2l[0], 32, c, pl);
        zh *= sgn[gt ? 1 : 0];
        zl *= sgn[gt ? 1 : 0];
        ph = muldd(zh, zl, ph, pl[0], pl);
        double sh = ph + off[i], sl = ((off[i] - sh) + ph) + pl[0];
        float rf = (float) sh;
        double th = rf, dh = sh - th, tm = dh + sl;
        r = th + tm;
        double d = r - th;
        if (((Double.doubleToRawLongBits(d)) << 12) == 0) {
          double ad = Math.abs(d), am = Math.abs(tm);
          if (ad > am) r -= d*0x1p-10;
          if (ad < am) r += d*0x1p-10;
        }
      }
    }
    float rf = (float) r;
    // the C source sets errno to ERANGE here (underflow) for |rf| < 0x1p-126f
    return rf;
  }
}
