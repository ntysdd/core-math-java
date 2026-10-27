/*
 * Correctly rounded tangent of a binary64 value, in half-revolution units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/tanpi/tanpi.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_tanpi -> TanPi.compute
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

package io.github.ntysdd.coremath.binary64;

/**
 * Correctly rounded {@code tan(pi*x)} of a binary64 value, the translation of
 * the CORE-MATH {@code cr_tanpi}.
 *
 * <p>From {@code 0x1p-12} up the argument is reduced in two steps. The first
 * one splits {@code x} into the bits of weight at most {@code 2^-7}, kept in
 * {@code z}, and the tangent of the remaining multiple of {@code pi/128},
 * which the table {@code T} holds as a double-double at one entry per
 * {@code iq} in {@code [0,31]}; inverse and reflection cover the other 32
 * octants. When the two-sided bound on the error of that result does not
 * settle the rounding, the second step scales {@code z} by {@code 2^-63} and
 * runs the same reduction again with a five-term polynomial in {@code z^2}.
 * Below {@code 0x1p-12} the tangent is evaluated directly from its Taylor
 * series about zero, with a scaling by a power of two below {@code 0x1p-916}
 * so that nothing underflows before the final scaling back.
 *
 * <p>{@link #asTanpiDatabase} takes the arguments whose reduced value is exact
 * or whose two candidates are more than 102 binades apart, and reads the
 * answer from the 49 exceptional triples of {@code db}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b64u64_u} becomes {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}, with the bit patterns carried in
 *       {@code long} locals. The shifts of {@code aix}, {@code ax},
 *       {@code m}, {@code iq}, {@code sm}, {@code k} and {@code kq} are
 *       {@code >>>} where the C value is a {@code uint64_t}; {@code ix >> 63},
 *       {@code sgn}, {@code ms} and {@code jq} keep the arithmetic {@code >>}
 *       of the C {@code int64_t}. No test needs {@link Long#compareUnsigned}:
 *       every {@code uint64_t} comparison of the C code is an equality or a
 *       {@code &}-test, and the {@code er} and {@code de} of the tail are
 *       masked down to 52 and 11 bits, so they are never negative as
 *       {@code long}.
 *   <li>{@code __builtin_expect} is dropped, the JIT profiles the branches
 *       itself, as is {@code __attribute__((noinline))} on
 *       {@code as_tanpi_database}.
 *   <li>The {@code CORE_MATH_SUPPORT_ERRNO} blocks are dropped: the two
 *       {@code ERANGE} of the overflow, the {@code EDOM} of {@code x = Inf}
 *       and the {@code ERANGE} of the underflow. With them go the
 *       {@code volatile double dummy = x * x} that exists only to raise the
 *       underflow flag and the comment about the rounding modes.
 *   <li>{@code 0.0/0.0} becomes {@link Double#NaN}, {@code 1.0/0.0} is
 *       {@link Double#POSITIVE_INFINITY} and {@code -1.0/0.0} is
 *       {@link Double#NEGATIVE_INFINITY}, each with the comment naming the C
 *       expression.
 *   <li>{@code __builtin_fma} is {@link Math#fma}, {@code __builtin_fabs} is
 *       {@link Math#abs} and {@code __builtin_copysign} is
 *       {@link Math#copySign}.
 *   <li>{@code double *} output parameters become one-element {@code double}
 *       arrays, including {@code tl} of {@code cr_tanpi}, whose address the C
 *       code passes to every helper.
 *   <li>{@code static const} tables declared inside the functions become
 *       fields in the order of the C source. A class holds two {@code s2},
 *       two {@code ch} and two {@code cl}, so they become {@code s2a} and
 *       {@code s2b}, {@code chMain} and {@code chSmall}, {@code clMain} and
 *       {@code clSmall}, where {@code Main} names the first reduction and
 *       {@code Small} the {@code |x| < 0x1p-12} branch.
 *   <li>{@code sizeof(db)/sizeof(db[0])} becomes {@code db.length}; the
 *       {@code iq} and {@code ms+1} subscripts of {@code T} and {@code s2}
 *       are written as {@code (int)} casts, Java does not index with a
 *       {@code long}.
 *   <li>The {@code sc} and {@code isc} unions of the {@code |x| < 0x1p-916}
 *       branch are built as {@code long} bit patterns; their {@code .f} member
 *       is read once into the locals {@code scf} and {@code iscf}, which the
 *       C code reads three and two times.
 * </ul>
 */
public final strictfp class TanPi {
  private TanPi() {
  }

  private static double fasttwosum(double x, double y, double[] e) {
    double s = x + y, z = s - x;
    e[0] = y - z;
    return s;
  }

  private static double fasttwosub(double x, double y, double[] e) {
    double s = x - y, z = x - s;
    e[0] = z - y;
    return s;
  }

  private static double muldd_acc(double xh, double xl, double ch, double cl, double[] l) {
    double ahlh = ch*xl, alhh = cl*xh, ahhh = ch*xh, ahhl = Math.fma(ch, xh, -ahhh);
    ahhl += alhh + ahlh;
    return fasttwosum (ahhh, ahhl, l);
  }

  private static double mulddd(double xh, double xl, double ch, double[] l) {
    double ahlh = ch*xl, ahhh = ch*xh, ahhl = Math.fma(ch, xh, -ahhh);
    ahhl += ahlh;
    ch = ahhh + ahhl;
    l[0] = (ahhh - ch) + ahhl;
    return ch;
  }

  private static double fastsumddd(double xh, double xl, double ch, double[] l) {
    double[] tl = new double[1];
    double th = fasttwosum(xh, ch, tl);
    l[0] = xl + tl[0];
    return th;
  }

  private static double polydd(double xh, double xl, int n, double[][] c, double[] l) {
    int i = n-1;
    double[] cl = new double[1];
    double ch = c[i][0] + l[0];
    cl[0] = ((c[i][0] - ch) + l[0]) + c[i][1];
    while(--i>=0){
      ch = muldd_acc(xh, xl, ch, cl[0], cl);
      double th = ch + c[i][0], tl = (c[i][0] - th) + ch;
      ch = th;
      cl[0] += tl + c[i][1];
    }
    l[0] = cl[0];
    return ch;
  }

  private static final double[][] db = {
    {0x1.b44453e2404e7p-55, 0x1.56a4aa740a5a7p-53, 0x1p-107},
    {0x1.45435c15060dcp-54, 0x1.feec101084f24p-53,-0x1p-107},
    {0x1.ac84c88f979a2p-54, 0x1.508ecb38f52f9p-52, 0x1p-106},
    {0x1.b44453e2404e7p-54, 0x1.56a4aa740a5a7p-52, 0x1p-106},
    {0x1.5f095755008fp-52, 0x1.13b412ea2182dp-50, 0x1p-104},
    {0x1.736d8818a1ef3p-48, 0x1.23b8007cd2448p-46,-0x1p-100},
    {0x1.c7c00d31634a6p-48, 0x1.65f203d6bb18ep-46, 0x1p-100},
    {0x1.df6d9f7748faep-45, 0x1.788ac313fc061p-43, 0x1p-97},
    {0x1.292b56d105932p-44, 0x1.d2cadcfe9f85fp-43,-0x1p-97},
    {0x1.45a65501d94d3p-44, 0x1.ff878736a1bcdp-43, 0x1p-97},
    {0x1.e4a84b3095d13p-43, 0x1.7ca626cfcf41p-41,-0x1p-95},
    {0x1.9eb0b7b0519dap-42, 0x1.45b26ebd51c87p-40, 0x1p-94},
    {0x1.1d0798b3403ebp-41, 0x1.bfb93b8757f7cp-40,-0x1p-94},
    {0x1.05d3aa746e163p-38, 0x1.9b46cfb0f10cap-37, 0x1p-91},
    {0x1.d3f710390db87p-36, 0x1.6f89f6df8258dp-34, 0x1p-88},
    {0x1.9e2371e233d1bp-34, 0x1.45437a2ebe657p-32, 0x1p-86},
    {0x1.386de1f01225ap-32, 0x1.eac33f52580c1p-31, 0x1p-85},
    {0x1.520cc578bf7ccp-30, 0x1.0980f68058c7fp-28,-0x1p-82},
    {0x1.a5bdf2b61615bp-23, 0x1.4b3c41e10a1d4p-21, 0x1p-75},
    {0x1.ed835e8ecc245p-23, 0x1.839ab56b14ffdp-21, 0x1p-75},
    {0x1.5b79e364c2d17p-22, 0x1.10e838704c63dp-20, 0x1p-74},
    {0x1.a96d0004e5b8bp-21, 0x1.4e20edbb1c21bp-19, 0x1p-73},
    {0x1.7b544ccbafe6ap-18, 0x1.29ecae32f9ca3p-16, 0x1p-70},
    {0x1.a2f422a07a3d4p-16, 0x1.490ba1569549fp-14, 0x1p-68},
    {0x1.ee10ecdb9b092p-15, 0x1.8409e333ea2e8p-13,-0x1p-67},
    {0x1.ee3f52b4af0dcp-15, 0x1.842e540b07ac1p-13, 0x1p-67},
    {0x1.1c2dc0a9340efp-13, 0x1.be630d1090fe6p-12, 0x1p-66},
    {0x1.75fdc341dc7a1p-13, 0x1.25bb6997b4387p-11,-0x1p-65},
    {0x1.a572fa6b3d117p-12, 0x1.4b016bd111ed5p-10, -0x1p-64},
    {0x1.a537a6de092a1p-11, 0x1.4ad2f60d5240bp-9, 0x1p-63},
    {0x1.db74ba716abb6p-11, 0x1.756c5a7eec959p-9, 0x1p-63},
    {0x1.5d89dc03193cdp-10, 0x1.12874c9307181p-8, -0x1p-62},
    {0x1.54ae0fa9f24b9p-7, 0x1.0baa21da88501p-5, -0x1p-59},
    {0x1.8582f89424fcep-7, 0x1.321061924ca03p-5, 0x1p-59},
    {0x1.14fc0e0c2dacep-6, 0x1.b37ef5831a537p-5, 0x1p-59},
    {0x1.309ce21202ccap-5, 0x1.e0ac5527e7f0dp-4, 0x1p-58},
    {0x1.bb6715a0b1f81p-5, 0x1.5fa4b5e7ce93fp-3, 0x1p-57},
    {0x1.1e38ee7c71b98p-4, 0x1.c8f62be40c3a1p-3, -0x1p-57},
    {0x1.983c8c74ade5dp-4, 0x1.4b88c3e5b04d0p-2, 0x1p-56},
    {0x1.a334561b8192cp-4, 0x1.5513de069862bp-2, 0x1p-56},
    {0x1.a33741abea942p-4, 0x1.55166a4d7d836p-2, 0x1p-56},
    {0x1.c176d0b4666d3p-4, 0x1.6fb0d2df2ae41p-2, 0x1p-56},
    {0x1.ca50e673ede6bp-3, 0x1.b1ed1cc270a2dp-1, 0x1p-55},
    {0x1.df3c63292f1f3p-3, 0x1.cef531bfb44bfp-1, -0x1p-55},
    {0x1.f551afda4c896p-3, 0x1.ef7dd0e5c6350p-1, 0x1p-55},
    {0x1.8b9be647e02a9p-2, 0x1.5717df9abccd3p+1, 0x1p-53},
    {0x1.b648e5b0804dap-2, 0x1.16265f83e8121p+2, -0x1p-52},
    {0x1.dd9d1ecffc9a0p-2, 0x1.2e342ca2440eep+3, 0x1p-51},
    {0x1.ee846d3680301p-2, 0x1.2a04fa64d35ffp+4, 0x1p-50},
  };

  private static final double[][] T = {
    {0x0p+0, 0x0p+0}, {0x1.ada13ceebab9dp-64, 0x1.927278a3b1162p-5},
    {0x1.87d054f689d7ap-58, 0x1.936bb8c5b2da2p-4}, {0x1.2cfb5a746f62cp-58, 0x1.2fcac73a6064p-3},
    {0x1.ef5d367441946p-61, 0x1.975f5e0553158p-3}, {0x1.5a6d6c3c8b6a7p-57, 0x1.007fa758626aep-2},
    {-0x1.d704d1bfdb6e8p-57, 0x1.36a08355c63dcp-2}, {0x1.78e945dc3913cp-57, 0x1.6e649f7d78649p-2},
    {0x1.08b2fb1366ea9p-56, 0x1.a827999fcef32p-2}, {0x1.41522f15e53c5p-56, 0x1.e450e0d273e7ap-2},
    {-0x1.e564bcd1e635dp-55, 0x1.11ab7190834ecp-1}, {-0x1.10b4421e6a4f8p-55, 0x1.32e1889047ffdp-1},
    {0x1.7a8c52172b675p-55, 0x1.561b82ab7f99p-1}, {-0x1.aa7538e44e996p-55, 0x1.7bb99ed2990cfp-1},
    {-0x1.a6db80fe796fep-56, 0x1.a43002ae4285p-1}, {0x1.8dcad85e60fbep-56, 0x1.d00cbc7384d2ep-1},
    {0x0p+0, 0x1p+0}, {0x1.946cc0b66979fp-54, 0x1.1a73d55278c4bp+0},
    {-0x1.87e5ad9735569p-56, 0x1.37efd8d87607ep+0}, {0x1.6a085e3bc3af6p-55, 0x1.592d11142fa55p+0},
    {0x1.419fa6954928fp-54, 0x1.7f218e25a7461p+0}, {-0x1.b6fb77846d763p-56, 0x1.ab1c35d8a74eap+0},
    {0x1.0fb3e75c7098ep-55, 0x1.def13b73c1406p+0}, {0x1.7ce6cb463c972p-54, 0x1.0ea21d716fbf7p+1},
    {0x1.21165f626cdd5p-53, 0x1.3504f333f9de6p+1}, {0x1.aca947bfb1dccp-56, 0x1.65bc6cc825147p+1},
    {-0x1.b7a14d0d691bep-54, 0x1.a5f59e90600ddp+1}, {-0x1.89fcd637fbf3bp-55, 0x1.ff01305ecd8dcp+1},
    {0x1.10706fed37f0ep-55, 0x1.41bfee2424771p+2}, {-0x1.ae397239c5a0dp-53, 0x1.af73f4ca3310fp+2},
    {0x1.351daea79411dp-51, 0x1.44e6c595afdccp+3}, {-0x1.b6e6b4de0cd24p-56, 0x1.45affed201b55p+4},
  };

  private static final double[] c = {0x1.4abbce625be51p-207, 0x1.466bc6776a9b1p-345,
    0x1.45fff6eb26045p-483, 0x1.4627663861052p-621};

  private static final double[] s2a = {-1, 1};

  private static final double[][] chMain = {
    {0x1.921fb54442d18p-6, 0x1.1a62633145c07p-60}, {0x1.4abbce625be53p-18, -0x1.05511c6842515p-72},
    {0x1.466bc6775aae2p-30, -0x1.6dc0d93fb2ecep-85}, {0x1.45fff9b48e95ep-42, 0x1.b226b250d2cc3p-98},
    {0x1.45f472e3af011p-54, 0x1.90612a0755449p-110},
  };

  private static final double[] clMain = {0x1.45f32f25dab7fp-66, 0x1.45f3030c82af4p-78,
    0x1.464490600a978p-90};

  private static final double[] s2b = {-1, 1};

  private static final double[] c2 = {0x1.4abbce625be53p+3, 0x1.466bc6775aa2ap+5,
    0x1.46000158496c2p+7};

  private static final double[][] chSmall = {
    {0x1.4abbce625be53p+3, -0x1.05511c68476a8p-51}, {0x1.466bc6775aae2p+5, -0x1.6dc0cbddc0e69p-50},
    {0x1.45fff9b48e95ep+7, 0x1.a5047910ae0efp-49},
  };

  private static final double[] clSmall = {0x1.45f472e3aed7dp+9, 0x1.45f33be0e9598p+11};

  // __attribute__((noinline)) is dropped, the JIT decides
  private static double asTanpiDatabase(double x, double f) {
    long ix = Double.doubleToRawLongBits(x);
    long aix = ix & (~0L >>> 1);
    long p = 0;
    double ax = Math.abs(x);
    int e = (int) (aix >>> 52) - 1022;
    if (e >= 0) { // |x|>=0.5, take into account periodic structure of tan
      long t = aix & ~(~0L >>> (12 + e));
      ax -= Double.longBitsToDouble(t);
      p = ((aix | 1L << 52) >>> (52 - e)) & 1;
      if (p != 0) ax = 0.5 - ax;
    }
    double sgn = 1;
    if ((p ^ (ix >>> 63)) != 0) sgn = -1; // select proper sign
    int a = 0, b = db.length - 1, m = (a + b)/2;
    while (a <= b) { // binary search
      if (db[m][0] < ax)
        a = m + 1;
      else if (db[m][0] == ax) {
        f = sgn*db[m][1] + sgn*db[m][2];
        break;
      } else
        b = m - 1;
      m = (a + b)/2;
    }
    return f;
  }

  public static double compute(double x) {
    double ph = 0x1.921fb54442d18p-69, pl = 0x1.1a62633145c07p-123;
    double th, res;
    double[] tl = new double[1];

    long ix = Double.doubleToRawLongBits(x);
    long ax = ix & (~0L >>> 1);
    if (ax >= (0x3f3L << 52)) { // |x| >= 0x1p-12
      if (ax >= (0x42dL << 52)) { // |x| >= 0x1p+46
        if (ax >= (0x7ffL << 52)) { // NaN, Inf
          if (ax > (0x7ffL << 52)) return x + x; // NaN
          // the C source sets errno to EDOM here
          return Double.NaN; // 0.0/0.0 in the C source, x=Inf
        }
        int e = (int) (ax >>> 52), s = e - 1069;
        if (s > 6) return Math.copySign(0.0, x);
        long m = ax, sgn = ix >> 63;
        long iq = (((m ^ sgn) - sgn) << s) & 127;
        if ((iq & 31) == 0) {
          long jq = iq >>> 5;
          if ((jq & 1) != 0) {
            // the C source sets errno to ERANGE here (overflow)
            if ((jq & 2) != 0)
              return Double.NEGATIVE_INFINITY; // -1.0/0.0 in the C source
            else
              return Double.POSITIVE_INFINITY; // 1.0/0.0 in the C source
          } else {
            if (((jq ^ sgn) & 2) != 0)
              return -0.0;
            else
              return 0.0;
          }
        } else {
          double nl, nh;
          if ((iq & 32) != 0) {
            nl = -T[(int) (32 - (iq & 31))][0]; nh = -T[(int) (32 - (iq & 31))][1];
          } else {
            nl = T[(int) (iq & 31)][0]; nh = T[(int) (iq & 31)][1];
          }
          return nh+nl;
        }
      }
      // now 0x1p-12 <= |x| < 0x1p+46, we have 1011 <= e <= 1068
      int e = (int) (ax >>> 52), s = 1068 - e, s1 = e - 1011;
      long m = (ax & (~0L >>> 12)) | (1L << 52),
        ms = (m << s1) >> 63, sgn = ix >> 63;
      // m is the significand, 2^52 <= m < 2^53
      // 0 <= s1 <= 57 is a biased exponent, with s1=0 for 2^-12 <= |x| < 2^-11
      // 0 <= s <= 57 is another biased exponent, with s=0 for 2^45 <= |x| < 2^46
      // ms is the bit of weight 1/2 in x
      long iq = ((m ^ ms) >>> s) & 63;
      iq = (iq + 1) >>> 1;
      ms ^= sgn;
      long sm = (m ^ sgn) - sgn; // sm = sign(x)*m
      long k = sm << (e - 1005); // 6 <= e-1005 <= 63
      // k contains the bits of m of weight <= 2^-7
      double z = k;
      if ((k << 1) == 0) { // x mod 2^-8 = 0
        if (k == 0) { // x mod 2^-7 = 0
          if ((iq & 31) == 0) {
            long jq = sm >> (s + 6);
            if ((jq & 1) != 0) {
              // the C source sets errno to ERANGE here (overflow)
              if ((jq & 2) != 0)
                return Double.NEGATIVE_INFINITY; // -1.0/0.0 in the C source
              else
                return Double.POSITIVE_INFINITY; // 1.0/0.0 in the C source
            } else {
              if (((jq ^ sgn) & 2) != 0)
                return -0.0;
              else
                return 0.0;
            }
          }
          // avoid spurious inexact exception for x=1/4 mod 1/2
          long kq = (m << s1) >>> 58;
          if (kq == 0x10) // |x| = 1/4 mod 1
            return Math.copySign(1.0, x);
          if (kq == 0x30) // |x| = 3/4 mod 1
            return -Math.copySign(1.0, x);
        }
        z = Math.copySign(1.0, x)*z;
      }
      double z2 = z*z, z4 = z2*z2, z3 = z*z2, f = z3*((c[0] + z2*c[1]) + z4*(c[2] + z2*c[3]));
      double eps = z3*0x1p-256 + Math.copySign(0x1p-104, z);
      th = mulddd(ph, pl, z, tl);
      th = fastsumddd(th, tl[0], f, tl);
      if (iq == 32) {
        double ith = -1.0/th;
        tl[0] = (Math.fma(ith, th, 1) + tl[0] * ith) * ith;
        th = ith;
      } else {
        double nl = T[(int) iq][0], nh = T[(int) iq][1];
        nh *= s2a[(int) (ms + 1)];
        nl *= s2a[(int) (ms + 1)];
        double[] ml = new double[1];
        double mh = muldd_acc(th, tl[0], nh, nl, ml);
        double[] dm = new double[1], dn = new double[1];
        mh = fasttwosub(1.0, mh, dm);
        ml[0] = dm[0] - ml[0];
        nh = fasttwosum(nh, th, dn);
        nl += dn[0] + tl[0];
        double imh = 1/mh;
        th = nh*imh;
        tl[0] = Math.fma(nh, imh, -th) + (nl + nh * (Math.fma(-mh, imh, 1) - ml[0] * imh)) * imh;
      }
      eps += eps*(th*th);
      double lb = th + (tl[0] - eps), ub = th + (tl[0] + eps);
      if (lb == ub) return lb;
      z *= 0x1p-63;
      z2 = z*z; double dz2 = Math.fma(z, z, -z2);
      tl[0] = z2 * (clMain[0] + z2 * (clMain[1] + z2 * (clMain[2])));
      th = polydd(z2, dz2, 5, chMain, tl);
      th = mulddd(th, tl[0], z, tl);
      if (iq == 32) {
        double ith = -1.0/th;
        tl[0] = (Math.fma(ith, th, 1) + tl[0] * ith) * ith;
        th = ith;
      } else {
        double nl = T[(int) iq][0], nh = T[(int) iq][1];
        nh *= s2b[(int) (ms + 1)];
        nl *= s2b[(int) (ms + 1)];
        double[] ml = new double[1];
        double mh = muldd_acc(th, tl[0], nh, nl, ml);
        double[] dm = new double[1], dn = new double[1];
        mh = fasttwosub(1.0, mh, dm);
        ml[0] = dm[0] - ml[0];
        nh = fasttwosum(nh, th, dn);
        nl += dn[0] + tl[0];
        double imh = 1/mh;
        th = nh*imh;
        tl[0] = Math.fma(nh, imh, -th) + (nl + nh * (Math.fma(-mh, imh, 1) - ml[0] * imh)) * imh;
      }
      th = fasttwosum(th, tl[0], tl);
      res = th;
    } else { // |x| < 0x1p-12
      if (ax == 0) return x;
      double pi0 = 0x1.921fb54442d18p+1, pi1 = 0x1.1a62633145c07p-53;
      if (ax < (0x3caL << 52)) { // |x| < 0x1p-53
        if (ax < (0x6bL << 52)) { // |x| < 0x1p-916
          int e = (int) (ax >>> 52);
          long sc = (2045L - e) << 52, isc = (1L + e) << 52;
          double scf = Double.longBitsToDouble(sc), iscf = Double.longBitsToDouble(isc);
          double z = x * scf;
          th = mulddd(pi0, pi1, z, tl);
          res = th * iscf;
          if (Math.abs(res) < 0x1p-1022) {
            // the C source sets errno to ERANGE here (underflow) and squares x
            // into an unused volatile to raise the underflow flag, Java has
            // neither errno nor floating-point flags
            double o = Math.copySign(0x1p-1022, x);
            double v0b = (o + res)*scf, v0h = res*scf;
            tl[0] += th - v0h;
            v0b += tl[0];
            return v0b*iscf - o;
          }
        } else { // 0x1p-916 <= |x| < 0x1p-53
          th = mulddd(pi0, pi1, x, tl);
          res = th;
        }
      } else {
        double x2 = x*x, x3 = x*x2;
        double f = x3*(c2[0] + x2*(c2[1] + x2*(c2[2])));
        double[] px1 = new double[1];
        double px0 = mulddd(pi0, pi1, x, px1);
        th = fastsumddd(px0, px1[0], f, tl);
        double eps = x*(x2*0x1.1p-47 + 0x1p-101);
        double lb = th + (tl[0] - eps), ub = th + (tl[0] + eps);
        if (lb == ub) return lb;
        double dx2 = Math.fma(x, x, -x2), dx3 = Math.fma(x2, x, -x3) + dx2*x;
        double[] dv = new double[1];
        tl[0] = x2 * (clSmall[0] + x2 * clSmall[1]);
        th = polydd(x2, dx2, 3, chSmall, tl);
        th = muldd_acc(x3, dx3, th, tl[0], tl);
        th = fasttwosum(px0, th, dv);
        tl[0] = tl[0] + px1[0] + dv[0];
        th = fasttwosum(th, tl[0], tl);
        res = th;
      }
    }
    long ul = Double.doubleToRawLongBits(tl[0]), uh = Double.doubleToRawLongBits(th);
    long er = (ul + 6) & (~0L >>> 12),
         de = ((uh - ul) >>> 52) & 0x7ff;
    if (er <= 12 || de > 102) return asTanpiDatabase(x, res);
    return res;
  }
}
