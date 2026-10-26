/*
 * Correctly rounded power function for binary32 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/pow/powf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_powf -> PowF.compute
 *
 * Copyright (c) 2022-2025 Alexei Sibidanov and Paul Zimmermann
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
 * Correctly rounded power function of two binary32 values, the translation of
 * the CORE-MATH {@code cr_powf}.
 *
 * <p>{@code powf.c} is self-contained: it includes only the C standard headers,
 * so unlike {@code pow.c} it brings no {@code dint.h} or {@code qint.h} code
 * with it. The functions it carries are {@code muldd}, {@code mulddd} and
 * {@code polydd}, the double-double helpers of the second iteration, and
 * {@code cr_powf} itself, with {@code as_powf_accurate2} after it.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} and {@code b64u64_u} become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Double#doubleToRawLongBits}; the {@code uint64_t} fields they
 *       expose are unsigned in the C code, thus the right shifts on them are
 *       {@code >>>}.
 *   <li>{@code double *} output parameters become one-element {@code double}
 *       arrays, including the aliased {@code muldd (xh, xl, ch, cl, &cl)} and
 *       {@code polydd (eh, el, 18, ce, &el)} calls.
 *   <li>{@code FLAG_T}, {@code get_flag}, {@code set_flag} and the
 *       {@code flag} parameter of {@code as_powf_accurate2} are dropped. They
 *       exist only to save and restore the inexact flag around the accurate
 *       path, which the JVM does not expose. For the same reason the
 *       {@code CORE_MATH_SUPPORT_ERRNO} blocks, {@code is_inf} and the
 *       {@code fegetround} call they contain are dropped.
 *   <li>{@code roundeven_finite} is defined but never called in {@code powf.c},
 *       so nothing is translated for it. {@code __builtin_roundf (sqrtf (dm))}
 *       in {@code is_exact} becomes {@code Math.round} of {@code (float)
 *       Math.sqrt (dm)}; the argument is positive and below 2^12 there, so
 *       {@code Math.round} and {@code roundf} agree bit for bit.
 *   <li>{@code (x - x) / (x - x)} becomes {@link Float#NaN} and
 *       {@code 1.0f / 0.0f} becomes {@link Float#POSITIVE_INFINITY}, with a
 *       comment in each place. The two overflowing products
 *       {@code 0x1p127f * 0x1p127f} and {@code 0x1p-126f * 0x1p-126f} are kept
 *       as they are; Java folds them at compile time to the same values that
 *       round-to-nearest produces at run time, {@code +Infinity} and
 *       {@code 0.0f}.
 *   <li>{@code static const} tables declared inside the functions become
 *       fields. The {@code ch} and {@code ce} tables of
 *       {@code as_powf_accurate2} collide with the {@code ce} of
 *       {@code cr_powf}, so the accurate ones get an {@code Acc} suffix.
 *   <li>{@code __attribute__((noinline))} on {@code polydd} is dropped, and so
 *       is the forward declaration of {@code as_powf_accurate2}.
 * </ul>
 */
public final strictfp class PowF {
  private PowF() {
  }

  private static int isSignalingf(float x) {
    int u = Float.floatToRawIntBits(x);
    /* To keep the following comparison simple, toggle the quiet/signaling bit,
       so that it is set for sNaNs.  This is inverse to IEEE 754-2008 (as well as
       common practice for IEEE 754-1985).  */
    u ^= 0x00400000;
    /* We have to compare for greater (instead of greater or equal), because x's
       significand being all-zero designates infinity not NaN.  */
    return (u & 0x7fffffff) > 0x7fc00000 ? 1 : 0;
  }

  private static double muldd(double xh, double xl, double ch, double cl, double[] l) {
    double ahlh = ch * xl, alhh = cl * xh, ahhh = ch * xh, ahhl = Math.fma(ch, xh, -ahhh);
    ahhl += alhh + ahlh;
    ch = ahhh + ahhl;
    l[0] = (ahhh - ch) + ahhl;
    return ch;
  }

  private static double mulddd(double xh, double xl, double ch, double[] l) {
    double ahlh = ch * xl, ahhh = ch * xh, ahhl = Math.fma(ch, xh, -ahhh);
    ahhl += ahlh;
    ch = ahhh + ahhl;
    l[0] = (ahhh - ch) + ahhl;
    return ch;
  }

  private static double polydd(double xh, double xl, int n, double[][] c, double[] l) {
    int i = n - 1;
    double ch = c[i][0];
    double[] cl = new double[1];
    cl[0] = c[i][1];
    while (--i >= 0) {
      ch = muldd(xh, xl, ch, cl[0], cl);
      double th = ch + c[i][0], tl = (c[i][0] - th) + ch;
      ch = th;
      cl[0] += tl + c[i][1];
    }
    l[0] = cl[0];
    return ch;
  }

  private static int isint(float y0) {
    int wy = Float.floatToRawIntBits(y0);
    int ey = ((wy >>> 23) & 0xff) - 127, s = ey + 9;
    if (ey >= 0) {
      if (s >= 32)
        return 1;
      return (wy << s) == 0 ? 1 : 0;
    }
    if ((wy << 1) == 0)
      return 1;
    return 0;
  }

  private static int isodd(float y0) {
    int wy = Float.floatToRawIntBits(y0);
    int ey = ((wy >>> 23) & 0xff) - 127, s = ey + 9, odd = 0;
    if (ey >= 0) {
      if (s < 32 && (wy << s) == 0)
        odd = (wy >>> (32 - s)) & 1;
      if (s == 32)
        odd = wy & 1;
    }
    return odd;
  }

  private static final int[] xmax =
  { 0, 0xffffff, 4095, 255, 63, 27, 15, 9,
                                   7, 5, 5, 3, 3, 3, 3, 3};

  private static final double[] ix =
  {
    0x1p+0, 0x1.f07c1f07cp-1, 0x1.e1e1e1e1ep-1, 0x1.d41d41d42p-1,
    0x1.c71c71c72p-1, 0x1.bacf914c2p-1, 0x1.af286bca2p-1, 0x1.a41a41a42p-1,
    0x1.99999999ap-1, 0x1.8f9c18f9cp-1, 0x1.861861862p-1, 0x1.7d05f417dp-1,
    0x1.745d1745dp-1, 0x1.6c16c16c1p-1, 0x1.642c8590bp-1, 0x1.5c9882b93p-1,
    0x1.555555555p-1, 0x1.4e5e0a72fp-1, 0x1.47ae147aep-1, 0x1.414141414p-1,
    0x1.3b13b13b1p-1, 0x1.3521cfb2bp-1, 0x1.2f684bda1p-1, 0x1.29e4129e4p-1,
    0x1.249249249p-1, 0x1.1f7047dc1p-1, 0x1.1a7b9611ap-1, 0x1.15b1e5f75p-1,
    0x1.111111111p-1, 0x1.0c9714fbdp-1, 0x1.084210842p-1, 0x1.041041041p-1, 0x1p-1
  };

  private static final double[][] lix =
  {
    {0x0p+0, 0x0p+0}, {-0x1.6cp-5, 0x1.4b229b87f3f89p-15},
    {-0x1.66p-4, -0x1.fb7d654235799p-15}, {-0x1.08p-3, -0x1.8b119b2c9c87bp-12},
    {-0x1.5cp-3, -0x1.a39fa6533294dp-19}, {-0x1.acp-3, -0x1.ebc5b663dd4b8p-12},
    {-0x1.fcp-3, 0x1.f4a37fe0fa46fp-14}, {-0x1.24p-2, -0x1.01eac33103e6bp-12},
    {-0x1.4ap-2, 0x1.61ed0d15725ep-12}, {-0x1.6ep-2, -0x1.10e6ceb499ba9p-13},
    {-0x1.92p-2, 0x1.115db8ada837dp-12}, {-0x1.b4p-2, -0x1.fafdce266d7aep-12},
    {-0x1.d6p-2, -0x1.d4f80cd19906fp-12}, {-0x1.f8p-2, 0x1.5ea5ccd0a7396p-12},
    {0x1.e8p-2, -0x1.0500d67fe62ebp-13}, {0x1.c8p-2, 0x1.9dc2d41aa4626p-14},
    {0x1.a8p-2, 0x1.ff2e2ff321344p-11}, {0x1.8ap-2, 0x1.130157f4c3a3ep-11},
    {0x1.6cp-2, 0x1.61ed0cad929ccp-11}, {0x1.5p-2, -0x1.2089a632d7949p-11},
    {0x1.32p-2, 0x1.7fdc6dfb2d21ap-11}, {0x1.16p-2, 0x1.380a6c36088f3p-11},
    {0x1.f6p-3, -0x1.3ab7dc7ba81acp-18}, {0x1.cp-3, -0x1.cc2c0061ef1a2p-14},
    {0x1.8ap-3, 0x1.130157c97bbep-12}, {0x1.56p-3, 0x1.ee14ff34c4128p-14},
    {0x1.22p-3, 0x1.b5b854c4fde69p-12}, {0x1.ep-4, 0x1.635d1df7cb0b5p-13},
    {0x1.7ep-4, -0x1.3f6d2636c101ep-13}, {0x1.1cp-4, -0x1.33567f1b193a4p-14},
    {0x1.78p-5, -0x1.8d66c5313a71dp-14}, {0x1.74p-6, 0x1.f7430ee200ep-17}, {0x0p+0, 0x0p+0}
  };

  private static final double[] c =
    {0x1.71547652b82fep+0, -0x1.71547652b82fep-1, 0x1.ec709dc3a2d0bp-2, -0x1.71547652bc4a9p-2,
     0x1.2776c441b72ep-2, -0x1.ec709bdf453ecp-3, 0x1.a6406efd4b877p-3, -0x1.717d824a520f7p-3};

  private static final double[] ce =
    {0x1.62e42fefa398bp-5, 0x1.ebfbdff84555ap-11, 0x1.c6b08d4ad86d3p-17,
     0x1.3b2ad1b1716a2p-23, 0x1.5d7472718ce9dp-30, 0x1.4a1d7f457ac56p-37};

  private static final double[] tb =
    {0x1p+0, 0x1.0b5586cf9890fp+0, 0x1.172b83c7d517bp+0, 0x1.2387a6e756238p+0,
     0x1.306fe0a31b715p+0, 0x1.3dea64c123422p+0, 0x1.4bfdad5362a27p+0, 0x1.5ab07dd485429p+0,
     0x1.6a09e667f3bcdp+0, 0x1.7a11473eb0187p+0, 0x1.8ace5422aa0dbp+0, 0x1.9c49182a3f09p+0,
     0x1.ae89f995ad3adp+0, 0x1.c199bdd85529cp+0, 0x1.d5818dcfba487p+0, 0x1.ea4afa2a490dap+0};

  private static final double[] o =
  {1, 2};

  private static final double[][] chAcc =
    {{0x1.71547652b82fep+1, 0x1.777d0ffda2b89p-55}, {0x1.ec709dc3a03fdp-1, 0x1.d27f04ff73b3ap-55},
     {0x1.2776c50ef9bfep-1, 0x1.e4b514251d0ecp-55}, {0x1.a61762a7aded9p-2, 0x1.de632dc7f6998p-57},
     {0x1.484b13d7c02aep-2, 0x1.a320ec342ddb3p-56}, {0x1.0c9a84993fd48p-2, -0x1.e6425ce9a74a4p-57},
     {0x1.c68f568d8beafp-3, -0x1.03a175487feabp-57}, {0x1.89f3b14657dfbp-3, 0x1.f04a3acf0bcf7p-57},
     {0x1.5b9ad2f2d12ap-3, -0x1.68fdff6815a6fp-58}, {0x1.3702165b88acbp-3, 0x1.45b052ace6c8ep-60},
     {0x1.1998f60f2f005p-3, -0x1.79a94f62fb524p-57}, {0x1.f9bc428e30809p-4, -0x1.51f063387e47p-59},
     {0x1.1ac0ab871296ap-3, 0x1.2ba6a2e1a625bp-57}
    };

  private static final double[][] ceAcc =
    {{0x1p+0, 0x1.f7d70599926c4p-98}, {0x1.62e42fefa39efp-1, 0x1.abc9e3b39856bp-56},
     {0x1.ebfbdff82c58fp-3, -0x1.5e43a540c283dp-57}, {0x1.c6b08d704a0cp-5, -0x1.d3316277451e6p-59},
     {0x1.3b2ab6fba4e77p-7, 0x1.4e66003ba7f85p-62}, {0x1.5d87fe78a6731p-10, 0x1.07183d46a9697p-66},
     {0x1.430912f86c787p-13, 0x1.bc81afca4c93p-67}, {0x1.ffcbfc588b0c7p-17, -0x1.e63f6f0116f4cp-71},
     {0x1.62c0223a5c826p-20, -0x1.30542d98ea4a5p-74}, {0x1.b5253d395e7c6p-24, -0x1.9285a132ce05ep-80},
     {0x1.e4cf5158b7b01p-28, -0x1.9ac1facae1b88p-83}, {0x1.e8cac7351a7a8p-32, -0x1.4fb82adebd76bp-91},
     {0x1.c3bd65182746dp-36, 0x1.84ad0689d30ep-91}, {0x1.8161931d765c3p-40, -0x1.254c6535279cep-95},
     {0x1.314943a26c9e2p-44, -0x1.f4f2fdc14fb82p-98}, {0x1.c36e53b459602p-49, 0x1.f0d06a5a63c41p-103},
     {0x1.397637b3876a4p-53, -0x1.5632c551ae458p-107}, {0x1.98fbfefdddb51p-58, -0x1.fd134923d52b4p-115}};

  // return non-zero if x^y is exact (and exactly representable as a float)
  private static int isExact(float x, float y) {
    /* All cases such that x^y might be exact are:
       (a) |x| = 1
       (b) y integer, 0 <= y <= 15
           (where 15 is the largest integer k such that 3^k fits in 24 bits)
       (c) y<0: x=1 or (x=2^e and |y|=n*2^-k with 2^k dividing e)
       (d) y>0: y=n*2^f with -4 <= f <= -1 and 1 <= n <= 15
       In cases (b)-(d), the low 16 bits of the encoding of y are zero,
       thus we use that for an early exit test.
       (For case (c), x=0x1p+1 and y=-0x1.2ap+7, only 16 low bits of the
       encoding of y are zero.) */

    int v = Float.floatToRawIntBits(x), w = Float.floatToRawIntBits(y);
    if ((v << 1) != 0x7f000000 && // |x| <> 1
        (w << (32 - 16)) != 0)
      return 0;

    if ((v << 1) == 0x7f000000) // |x| = 1
      return 1;

    // xmax[y] for 1<=y<=15 is the largest odd m such that m^y fits in 24 bits
    if (y >= 0 && isint(y) != 0) {
      /* let x = m*2^e with m an odd integer, x^y is exact when
         - y = 0 or y = 1
         - m = 1 or -1 and -149 <= e*y < 128
         - if |x| is not a power of 2, 2 <= y <= 15 and
           m^y should fit in 24 bits
      */
      int m = v & 0x7fffff; // low 23 bits of significand
      int e = ((v << 1) >>> 24) - 0x96;
      if (e >= -149)
        m |= 0x800000;
      else // subnormal numbers
        e++;
      int t = Integer.numberOfTrailingZeros(m);
      m = m >>> t;
      e += t;
      /* For normal numbers, we have x = m*2^e. */
      if (y == 0 || y == 1)
        return 1;
      if (m == 1)
        return -149 <= y * e && y * e < 128 ? 1 : 0;
      // now for y < 0 or 15 < y it cannot be exact
      if (y < 0 || 15 < y)
        return 0;
      // now 2 <= y <= 15
      int y_int = (int) y;
      if (Integer.compareUnsigned(m, xmax[y_int]) > 0)
        return 0;
      // |x^y| = m^y * 2^(e*y)
      long my = (m * m) & 0xffffffffL; // uint32_t arithmetic in the C source
      for (int i = 2; i < y_int; i++)
        my = my * m;
      // my = m^y
      t = 32 - Integer.numberOfLeadingZeros(m);
      // 2^(t-1) <= m^y < 2^t thus 2^(e*y + t - 1) <= |x^y| < 2^(e*y + t)
      int ez = e * y_int + t;
      if (ez <= -149 || 128 < ez)
        return 0;
      // since m is odd, x^y is an odd multiple of 2^(e*y)
      return e * y_int >= -149 ? 1 : 0;
    }

    int n = w & 0x7fffff;
    int f = ((w << 1) >>> 24) - 0x96;
    if (f >= -149)
      n |= 0x800000;
    else // subnormal numbers
      f++;
    int t = Integer.numberOfTrailingZeros(n);
    n = n >>> t;
    f += t;
    // |y| = n*2^f with n odd

    int m = v & 0x7fffff;
    int e = ((v << 1) >>> 24) - 0x96;
    if (e >= -149)
      m |= 0x800000;
    else // subnormal numbers
      e++;
    t = Integer.numberOfTrailingZeros(m);
    m = m >>> t;
    e += t;
    // |x| = m*2^e with m odd

    /* if y < 0, the only cases where x^y might be exact are:
     * if y = -n*2^f with f >= 0
     * if y = -n*2^f with f < 0, if x = 2^e with 2^(-f) dividing e
     */
    if (y < 0) {
      int ez;
      if (m != 1)
        return 0;
      // now x = 2^e
      if (f >= 0)
        ez = ((e >= 0) ? -(e << f) : (-e << f)) * n;
      else {
        // y = -n*2^f thus k = -f
        // now e <> 0
        t = Integer.numberOfTrailingZeros(e);
        if (-f > t)
          return 0; // 2^k does not divide e
        ez = (-e >> (-f)) * n;
      }
      return -149 <= ez && ez < 128 ? 1 : 0;
    }

    /* now y > 0, y is not a integer, y = n*2^f with n odd and f < 0.
       Since x^(n*2^f) = (x^(2^f))^n, and n is odd, necessarily
       x is an exact (2^k)th power with k=-f.
       This implies x is a square. Since x = m*2^e with m odd,
       necessarily m is a square, and e is even. */
    while (f++ != 0) {
      // try to extract a square from m*2^e
      if ((e & 1) != 0)
        return 0;
      e = e / 2;
      float dm = (float) m;
      float s = (float) Math.round((float) Math.sqrt(dm));
      if (s * s != dm)
        return 0;
      /* The above call of sqrtf() might set the inexact flag, but in case
         it happens, m is not a square, thus x^y cannot be exact. */
      m = (int) s;
    }

    // Now |x^y| = (m*2^e)^n with m, n odd integers
    // now for 15 < n it cannot be exact, unless m=1
    if (m > 1) {
      if (15 < n)
        return 0;
      // now n <= 15
      if (Integer.compareUnsigned(m, xmax[n]) > 0)
        return 0;
    }
    // |x^y| = m^n * 2^(e*n) with m odd
    int my = m, n0 = n;
    while (n0-- > 1)
      my = my * m;
    // |x^y| = my * 2^(e*n)
    t = 32 - Integer.numberOfLeadingZeros(my); // number of significant bits of m^n
    /* x^y is an odd multiple of 2^(e*n) thus we should have e*n >= -149,
       we also have 2^(t-1) <= m^n thus 2^(e*n+t-1) <= |x^y| < 2^(e*n+t)
       and we need e*n+t <= 128 */
    return -149 <= e * n && e * n + t <= 128 ? 1 : 0;
  }

  public static float compute(float x0, float y0) {
    double x = x0, y = y0;
    long tx = Double.doubleToRawLongBits(x), ty = Double.doubleToRawLongBits(y);
    int xsgn = 0;
    if ((tx << 1) == 0x3ffL << 53) { // |x|=1
      if (tx >>> 63 != 0) { // x=-1
        if (Long.compareUnsigned(ty << 1, 0x7ffL << 53) > 0)
          return y0 + y0; // y=nan
        if (isint(y0) != 0)
          return (isodd(y0) != 0) ? x0 : -x0;
        // NaN /  (-1)^y for non-integer y, should raise 'Invalid operation' exception.
        return Float.NaN; // the C source builds it as (x - x) / (x - x)
      }
      return isSignalingf(y0) != 0 ? x0 + y0 : x0; // 1^y = 1 except for y = sNaN
    }
    if ((ty << 1) == 0)
      return isSignalingf(x0) != 0 ? x0 + y0 : 1.0f; // x^0 = 1 except for x = sNaN
    if (ty == 0x3ffL << 52)
      return isSignalingf(x0) != 0 ? x0 + y0 : x0; // x^1 = x except for x = sNaN
    if (Long.compareUnsigned(ty << 1, 0x7ffL << 53) >= 0) { // y=Inf/NaN
      // the case |x|=1 was already checked above
      if (Long.compareUnsigned(tx << 1, 0x7ffL << 53) > 0)
        return x0 + y0; // x=NaN
      if (Long.compareUnsigned(ty << 1, 0x7ffL << 53) == 0) {
        if ((Long.compareUnsigned(tx << 1, 0x3ffL << 53) < 0) ^ (ty >>> 63 != 0)) {
          return 0;
        } else {
          return Float.POSITIVE_INFINITY;
        }
      }
      return x0 + y0;
    }
    if (Long.compareUnsigned(tx, 0x7ffL << 52) >= 0) { // x is Inf, NaN or less than 0
      if ((tx << 1) == 0x7ffL << 53) { // x is +Inf or -Inf
        if (isodd(y0) == 0)
          x0 = Math.abs(x0);
        if (ty >>> 63 != 0)
          return 1 / x0;
        else
          return x0;
      }
      if (Long.compareUnsigned(tx << 1, 0x7ffL << 53) > 0)
        return x0 + x0; // x is NaN
      if (Long.compareUnsigned(tx, 0x7ffL << 52) > 0) { // x <= 0
        xsgn = 1;
        if (isint(y0) == 0 && x != 0) {
          // NaN, should raise 'Invalid operation' exception.
          return Float.NaN; // the C source builds it as (x - x) / (x - x)
        }
      }
    }
    if ((tx << 1) == 0) { // x=+0 or -0
      if (ty >>> 63 != 0) { // y < 0
        if (isodd(y0) != 0) {
          return 1.0f / Math.copySign(0.0f, x0);
        } else {
          return Float.POSITIVE_INFINITY;
        }
      } else { // y > 0
        if (isodd(y0) != 0)
          return Math.copySign(1.0f, x0) * 0.0f;
        else
          return 0.0f;
      }
    }
    long m = tx & (~0L >>> 12);
    int e = (int) ((tx >>> 52) & 0x7ff) - 0x3ff;
    int j = (int) ((m + (1L << (52 - 6))) >>> (52 - 5)), k = j > 13 ? 1 : 0;
    e += k;
    double xd = Double.longBitsToDouble(m | (0x3ffL << 52));
    double z = Math.fma(xd, ix[j], -1.0);
    double z2 = z * z, z4 = z2 * z2;
    double c6 = c[6] + z * c[7];
    double c4 = c[4] + z * c[5];
    double c2 = c[2] + z * c[3];
    double c0 = c[0] + z * c[1];
    c0 += z2 * c2;
    c4 += z2 * c6;
    c0 += z4 * c4;
    double l = z * c0 - lix[j][1];
    y *= 16;
    double zt = (e - lix[j][0]) * y;
    z = l * y + zt;
    if (z > 2048) {
      if (isodd(y0) != 0)
        return Math.copySign(0x1p127f, x0) * 0x1p127f;
      else {
        return 0x1p127f * 0x1p127f;
      }
    }
    if (z < -2400) {
      if (isodd(y0) != 0)
        return Math.copySign(0x1p-126f, x0) * 0x1p-126f;
      else
        return 0x1p-126f * 0x1p-126f;
    }
    if (Math.abs(z) < 0x1p-26)
      return (float) (1.0 + z);
    double ia = Math.floor(z), h = Math.fma(l, y, zt - ia);
    long il = (long) ia, jl = il & 0xf, el = il - jl;
    el >>= 4;
    double s = tb[(int) jl];
    s *= Double.longBitsToDouble((el + 0x3ffL) << 52);
    double h2 = h * h;
    c0 = ce[0] + h * ce[1];
    c2 = ce[2] + h * ce[3];
    c4 = ce[4] + h * ce[5];
    c0 += h2 * (c2 + h2 * c4);
    double w = s * h;
    double rr = s + w * c0;
    // with off=467, fails for x,y=0x1.fd12b4p-1,-0x1.d0b058p+13 and RNDZ
    long off = 468;
    if (((Double.doubleToRawLongBits(rr) + off) & 0xfffffffL) <= 2 * off)
      return asPowfAccurate2(x0, y0, isExact(x0, y0));
    if (xsgn != 0 && isodd(y0) != 0)
      rr = -rr;
    float res = (float) rr;
    return res;
  }

  private static float asPowfAccurate2(float x0, float y0, int isExact) {
    double x = x0, y = y0;
    long t = Double.doubleToRawLongBits(x);
    int e = (int) ((t >>> 52) & 0x7ff) - 0x3ff, xsgn = (int) (t >>> 63);
    t &= ~0L >>> 12;
    int k = Long.compareUnsigned(t, 0x6a09e667f3bcdL) > 0 ? 1 : 0;
    e += k;
    t |= 0x3ffL << 52;
    x = Double.longBitsToDouble(t);
    double xm = x - o[k], xp = x + o[k], zh = xm / xp;
    double[] zl = new double[1];
    double[] z2l = new double[1];
    zl[0] = Math.fma(zh, -xp, xm) / xp;
    double z2h = muldd(zh, zl[0], zh, zl[0], z2l);
    z2h = polydd(z2h, z2l[0], 13, chAcc, z2l);
    zh = muldd(zh, zl[0], z2h, z2l[0], zl);
    zh = mulddd(zh, zl[0], y, zl);
    double ey = e * y;
    double eh = ey + zh;
    double[] el = new double[1];
    el[0] = ((ey - eh) + zh) + zl[0];
    double ee = Math.rint(eh);
    eh -= ee;
    eh = polydd(eh, el[0], 18, ceAcc, el);
    double r = Double.longBitsToDouble((0x3ffL + (long) ee) << 52);
    double ll = el[0];
    long lh = Double.doubleToRawLongBits(eh);
    if ((isExact == 0 && (lh & 0xfffffffL) == 0)
        || (isExact != 0 && ((lh + 1) & 0xfffffffL) <= 2)) {
      if (Math.abs(ll) > 0x1p-91) {
        if (el[0] < 0) {
          lh--;
          eh = Double.longBitsToDouble(lh);
        } else {
          lh++;
          eh = Double.longBitsToDouble(lh);
        }
      }
    }
    eh *= r;
    if (xsgn != 0 && isodd(y0) != 0)
      eh = -eh;
    float res = (float) eh;
    return res;
  }

}
