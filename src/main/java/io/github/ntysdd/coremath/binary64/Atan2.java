/*
 * Correctly rounded arc-tangent of two binary64 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/atan2/atan2.c and tint.h
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atan2 -> Atan2.compute
 *
 * Copyright (c) 2023-2025 CERN, Inria and CNRS.
 * Authors: Paul Zimmermann and Alexei Sibidanov.
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

import io.github.ntysdd.coremath.internal.Tint64;
import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded arc-tangent of two binary64 values, the translation of the
 * CORE-MATH {@code cr_atan2}.
 *
 * <p>{@code atan2.c} includes {@code tint.h}, a header of 192-bit fixed-point
 * helpers. AGENTS.md makes one C file one Java class and lets a translation
 * unit carry the parts of the headers it uses, so the helpers of {@code tint.h}
 * that reach {@code atan2.c} are translated here, in the order of the header:
 * {@code cp_tint}, {@code mul_tint}, {@code tint_zero_p}, the comparison
 * helpers, {@code rshift}, {@code lshift}, {@code add_tint}, {@code tint_fromd},
 * {@code as_ldexp}, {@code tint_tod}, {@code inv_tint}, {@code div_tint} and
 * {@code div_tint_d}. {@code is_normalized}, {@code print_tint} and the
 * {@code MASK53} macro are not translated: nothing in the reference build calls
 * them.
 *
 * <p>{@code tint_t} itself is a struct that several translation units of
 * upstream share, so it becomes {@link Tint64} in {@code internal}, the way
 * {@code dint64_t} and {@code qint64_t} became {@code Dint64} and
 * {@code Qint64}. Then follow the tables, {@code atan2_accurate} with its
 * [29,29] rational approximation, the double-double helpers
 * {@code fasttwosum} and {@code fastsum}, {@code as_atan2_special}, and
 * {@code cr_atan2} as {@code compute}.
 *
 * <p>The deviations from the C files are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code d64u64} becomes {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; the shifts on those patterns are
 *       {@code >>>}.
 *   <li>{@code tint_t} becomes {@link Tint64} and {@code u128} becomes
 *       {@link U128}, with the little-endian member of the byte-order
 *       {@code #if} being the one the reference build compiles.
 *   <li>The whole {@code fenv} machinery is dropped: {@code feholdexcept} and
 *       {@code feupdateenv} in {@code atan2_accurate}, the {@code overflow} and
 *       {@code underflow} flags with the {@code feclearexcept} calls that
 *       restore them, the {@code fetestexcept} that feeds the underflow
 *       determination, and the {@code fegetexceptflag} / {@code fesetexceptflag}
 *       pair around {@code 1/dh} in {@code cr_atan2}. The JVM has no
 *       floating-point environment. Two consequences are worth naming: the
 *       {@code underflow} variable only fed {@code errno}, so it disappears
 *       with the {@code CORE_MATH_SUPPORT_ERRNO} blocks; and the {@code
 *       fegetexceptflag} pair protected a flag nothing can read here, so both
 *       branches of that {@code if} now do {@code rdh = 1/dh}.
 *   <li>{@code goto end} in {@code atan2_accurate} becomes a plain {@code
 *       return}, since the label only carried the environment restore.
 *   <li>The {@code printf} / {@code exit (1)} of {@code tint_tod} becomes a
 *       throw, as the mapping table asks for {@code exit} in a verification
 *       path.
 *   <li>{@code double *} output parameters become one-element {@code double}
 *       arrays, and {@code tint_fromd} writes into the {@link Tint64} it is
 *       called on.
 *   <li>{@code __attribute__((noinline))} and {@code __attribute__((cold))} are
 *       dropped; {@code as_ldexp} takes the portable branch of the
 *       {@code __x86_64__} {@code #if}, which produces the same bits.
 *   <li>{@code #define} constants become fields, and so do the
 *       {@code static const} tables declared inside the functions.
 * </ul>
 */
public final strictfp class Atan2 {
  private Atan2() {
  }

  // #define MASK inside cr_atan2(), 2^63-1 (mask the sign bit)
  private static final long MASK = 0x7fffffffffffffffL;

  /* PI_H+PI_L approximates pi with error bounded by 2^-108.041 */
  private static final double PI_H = 0x1.921fb54442d18p+1;
  private static final double PI_L = 0x1.1a62633145c07p-53;
  /* PI_OVER2_H+PI_OVER2_L approximates pi/2 with error bounded by 2^-109.041 */
  private static final double PI_OVER2_H = 0x1.921fb54442d18p+0;
  private static final double PI_OVER2_L = 0x1.1a62633145c07p-54;
  /* PI_OVER4_H+PI_OVER4_L approximates pi/4 with error bounded by 2^-110.041 */
  private static final double PI_OVER4_H = 0x1.921fb54442d18p-1;
  private static final double PI_OVER4_L = 0x1.1a62633145c07p-55;

  // Copy a tint_t value
  private static void cpTint(Tint64 r, Tint64 a) {
    r._h(a._h());
    r._l(a._l());
    r.ex = a.ex;
    r.sgn = a.sgn;
  }

  // Multiply two tint_t numbers, with error < 10 ulps or 2^-187.67 * |r|
  // Overlap between r and a or b is allowed
  private static void mulTint(Tint64 r, Tint64 a, Tint64 b) {
    r.ex = a.ex + b.ex;
    r.sgn = a.sgn ^ b.sgn;

    long ah = a.h, am = a.m, al = a.l;
    long bh = b.h, bm = b.m, bl = b.l;
    U128 rh = U128.mul(ah, bh), rm1 = U128.mul(ah, bm), rm2 = U128.mul(am, bh);
    U128 rl1 = U128.mul(ah, bl), rl2 = U128.mul(am, bm), rl3 = U128.mul(al, bh);
    long h, l, cm;
    r.h = rh.hi;
    r.m = rh.lo; // cast to low 64 bits
    // accumulate rm1
    r.l = rm1.lo; // cast to low 64 bits
    h = rm1.hi;
    r.m += h;
    r.h += Long.compareUnsigned(r.m, h) < 0 ? 1 : 0; // no overflow possible
    // accumulate rm2
    l = rm2.lo; // cast to low 64 bits
    h = rm2.hi;
    r.l += l;
    cm = Long.compareUnsigned(r.l, l) < 0 ? 1 : 0; // carry at r->m
    r.m += h;
    r.h += Long.compareUnsigned(r.m, h) < 0 ? 1 : 0; // no overflow possible
    // accumulate rl1+rl2+rl3
    rl1 = rl1.shr(64).add(rl2.shr(64)).add(rl3.shr(64));
    l = rl1.lo; // cast to low 64 bits
    cm += rl1.hi;
    r.l += l;
    cm += Long.compareUnsigned(r.l, l) < 0 ? 1 : 0; // carry at r->m
    // accumulate cm
    r.m += cm;
    r.h += Long.compareUnsigned(r.m, cm) < 0 ? 1 : 0;

    /* Note: if one of the operands was zero, then r->h = r->m = r->l = 0,
       and the normalization keeps r=0. */
    if ((r.h >>> 63) == 0) // normalize
    {
      r.h = (r.h << 1) | (r.m >>> 63);
      r.m = (r.m << 1) | (r.l >>> 63);
      r.l = r.l << 1;
      r.ex --;
    }

    /* We ignored the following terms, denoting B=2^64, related to r->l:
       am*bl + al*bm <= 2*(B-1)^2/B^2 = 2 - 4/B + 2/B^2
       al*bl <= (B-1)^2/B^3 = 1/B - 2/B^2 + 1/B^3
       And we truncated rl1+rl2+rl3:
       low(rl1) + low(rl2) + low(rl3) <= 3*(B-1)/B = 3-3/B
       This sums up to: 5 - 6/B + 1/B^3 < 5
       thus the rounding error is bounded by 5 ulps,
       and after normalization by 10 ulps. */
  }

  // Return non-zero if a = 0
  private static int tintZeroP(Tint64 a) {
    return a.h == 0 ? 1 : 0;
  }

  private static int cmp(long a, long b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }

  private static int cmpu64(long a, long b) { return Long.compareUnsigned(a, b); }

  private static int cmpu128(U128 a, U128 b) { return a.compareUnsigned(b); }

  // Compare the absolute values of a and b
  // Return -1 if |a| < |b|
  // Return  0 if |a| = |b|
  // Return +1 if |a| > |b|
  private static int cmpTintAbs(Tint64 a, Tint64 b) {
    if (tintZeroP(a) != 0)
      return tintZeroP(b) != 0 ? 0 : -1;
    if (tintZeroP(b) != 0)
      return +1;
    int c = cmp(a.ex, b.ex);
    if (c != 0)
      return c;
    // now a->ex = b->ex
    c = cmpu128(a._h(), b._h());
    if (c != 0)
      return c;
    return cmpu64(a._l(), b._l());
  }

  // shift right by k bits (only deal with the significand)
  private static void rshift(Tint64 a, Tint64 b, int k) {
    if (k == 0)
    {
      a._h(b._h());
      a._l(b._l());
    }
    else if (k < 64)
    {
      a._h(b._h().shr(k));
      a._l((b._h().lo << (64 - k)) | (b._l() >>> k));
    }
    else if (k == 64)
    {
      a._h(b._h().shr(k));
      a._l(b._h().lo);
    }
    else if (k < 128)
    {
      a._h(b._h().shr(k));
      a._l(b._h().shr(k - 64).lo);
    }
    else if (k < 192)
    {
      a._h(U128.of(0, 0));
      a._l(b._h().shr(k - 64).lo);
    }
    else
    {
      a._h(U128.of(0, 0));
      a._l(0);
    }
  }

  // shift left by k bits (only deal with the significand)
  private static void lshift(Tint64 a, Tint64 b, int k) {
    if (k == 0)
    {
      a._h(b._h());
      a._l(b._l());
    }
    else if (k < 64)
    {
      a._h(b._h().shl(k).or(U128.of(b._l() >>> (64 - k), 0)));
      a._l(b._l() << k);
    }
    else if (k == 64)
    {
      a._h(b._h().shl(k).or(U128.of(b._l(), 0)));
      a._l(0);
    }
    else if (k < 128)
    {
      a._h(b._h().shl(k).or(U128.of(b._l(), 0).shl(k - 64)));
      a._l(0);
    }
    else if (k < 192)
    {
      a._h(U128.of(b._l(), 0).shl(k - 64));
      a._l(0);
    }
    else
    {
      a._h(U128.of(0, 0));
      a._l(0);
    }
  }

  // Add two tint_t values, with error bounded by 2 ulps
  private static void addTint(Tint64 r, Tint64 a, Tint64 b) {
    switch (cmpTintAbs(a, b)) {
    case 0: // |a| = |b|, return 0 or 2*a (exact)
      if ((a.sgn ^ b.sgn) != 0) {
        cpTint(r, ZERO);
        return;
      }
      cpTint(r, a);
      r.ex++;
      return;

    case -1: // |a| < |b|
      {
        // swap operands
        Tint64 tmp = a;
        a = b;
        b = tmp;
        break; // fall through the case |a| > |b|
      }
    }

    // From now on, |a| > |b| thus a->ex >= b->ex
    Tint64 t = new Tint64();
    long sh = a.ex - b.ex;
    rshift(t, b, (int) sh);

    if ((a.sgn ^ b.sgn) != 0) { // opposite signs, it's a subtraction
      t._l(a._l() - t._l());
      t._h(a._h().sub(t._h()).sub(U128.of(
          Long.compareUnsigned(t._l(), a._l()) > 0 ? 1 : 0, 0)));
      long th = t._h().hi;
      long ex =
        th != 0 ? Long.numberOfLeadingZeros(th)
        : ((t._h().hi | t._h().lo) != 0 ? 64 + Long.numberOfLeadingZeros(t._h().lo)
           : 128 + Long.numberOfLeadingZeros(t._l()));
      if (ex <= 1 || sh == 0) {
        /* The maximal error of 1 ulp for the neglected low part of b is shifted
           by ex bits, thus contributes to < 2 ulps. And for sh=0, there is no
           neglected low part of b, thus the subtraction is exact. */
        lshift(r, t, (int) ex);
        r.ex = a.ex - ex;
      }
      else { // ex >= 2 and sh >= 1
        /* Since we had a cancellation of ex bits with the neglected low part
           of b, we'll have a cancellation of at least ex bits if we don't
           neglect the low part of b, thus we can shift left a and b/2^sh by
           ex bits, and subtract. This case (ex >= 2) can only occur when sh=1,
           since for sh>=2 we have (up to the exponent) a >= 1 and b < 0.5,
           thus a-b >= 0.5, and the exponent decrease ex is at most 1. */
        lshift(t, b, (int) (ex - sh));
        lshift(r, a, (int) ex);
        t._l(r._l() - t._l());
        t._h(r._h().sub(t._h()).sub(U128.of(
            Long.compareUnsigned(t._l(), r._l()) > 0 ? 1 : 0, 0)));
        th = t._h().hi;
        long ex1 =
          th != 0 ? Long.numberOfLeadingZeros(th)
          : ((t._h().hi | t._h().lo) != 0 ? 64 + Long.numberOfLeadingZeros(t._h().lo)
             : 128 + Long.numberOfLeadingZeros(t._l()));
        lshift(r, t, (int) ex1);
        r.ex = a.ex - (ex + ex1);
        /* Since we shifted b left in this case, there is no neglected bit of b,
           and the subtraction is exact. */
      }
    }
    else { // same signs, it's an addition
      // save values of ah, al in case r=a
      U128 ah = a._h();
      long al = a._l();
      r._l(al + t._l());
      long cl = Long.compareUnsigned(r._l(), al) < 0 ? 1 : 0;
      r._h(ah.add(t._h()));
      long ch = r._h().compareUnsigned(ah) < 0 ? 1 : 0;
      r._h(r._h().addLow(cl));
      ch += r._h().compareUnsigned(U128.of(cl, 0)) < 0 ? 1 : 0;
      // up to here, the maximal error is < ulp(r) [shifted part of b]
      if (ch != 0) { // can be at most 1
        r.ex = a.ex + 1;
        r._l((r._h().lo << 63) | (r._l() >>> 1));
        r._h(U128.of(0, ch << 63).or(r._h().shr(1)));
        /* the maximal error from the shifted part of b is now < 1/2 ulp(r),
           and in addition the low bit of r->_l that disappeared might give
           1/2 ulp(r), thus the total error is still < ulp(r) */
      }
      else
        r.ex = a.ex;
      // in the addition case, the error is bounded by ulp(r)
    }
    r.sgn = a.sgn;
  }

  // a <- x, assuming x is not NaN, Inf or 0
  // This operation is exact
  private static void tintFromd(Tint64 a, double x) {
    long u = Double.doubleToRawLongBits(x);
    a.sgn = u >>> 63;
    long ax = u & 0x7fffffffffffffffL;
    long e = ax >>> 52;
    if (e != 0) { // normal
      // 1 has e=0x3ff
      a.ex = e - 0x3fe;
      a.h = (1L << 63) | (ax << 11);
    }
    else { // subnormal
      // 2^-1074 has ax=1
      e = Long.numberOfLeadingZeros(ax);
      a.ex = -0x3f2 - e;
      a.h = ax << (int) e;
    }
    a.m = a.l = 0;
  }

  // copied from ../exp/exp.c
  private static double asLdexp(double x, long i) {
    long ix = Double.doubleToRawLongBits(x);
    ix += i << 52;
    return Double.longBitsToDouble(ix);
  }

  // convert a to a double with correct rouding
  // If err=0, we are converting a double value.
  // Otherwise, err is a bound in ulps on the maximal error on a->l
  // y,x are the inputs of atan2 (in case we can't round correctly)
  private static double tintTod(Tint64 a, long err, double y, double x) {
    if (a.ex >= 1025) // overflow: |a| >= 2^1024
      return a.sgn != 0 ? -0x1p1023 - 0x1p1023 : 0x1p1023 + 0x1p1023;
    if (a.ex <= -1074) // underflow: |a| < 2^-1074
    {
      if (a.ex < -1074) // |a| < 2^-1075
        return (a.sgn != 0 ? -0x1p-1074 : 0x1p-1074) * 0.5;
      // 2^-1075 <= |a| < 2^-1074
      int mid = a.h == (1L << 63) && a.m == 0 && a.l == 0 ? 1 : 0;
      // if mid, |a| = 2^-1075
      return (a.sgn != 0 ? -0x1p-1074 : 0x1p-1074) * (mid != 0 ? 0.5 : 0.75);
    }
    long hh = a.h, mm = a.m, ll = a.l;
    int ex = (int) a.ex;
    long low = hh & 0x7ff; // low 11 bits from a->h
    /* We can't determine the correct rounding when:
       (a) a->m = 0 and the low 10 bits of a->h are zero and a->l < err
       (b) a->m = 111...111 and the low 10 bits of a->h are 1 and
           a->l > 2^64 - err */
    if (mm == 0 || ~mm == 0)
      if ((mm == 0 && (low == 0 || low == 0x400)
           && Long.compareUnsigned(ll, err) < 0)
          || (~mm == 0 && (low == 0x3ff || low == 0x7ff)
              && Long.compareUnsigned(~ll, err) < 0))
      {
        throw new IllegalStateException("Unexpected worst-case found: atan2("
            + Double.toHexString(y) + ", " + Double.toHexString(x) + ")");
      }
    if (ex <= -1022) // subnormal case
    {
      int sh = -1021 - ex; // 1 <= sh <= 52
      ll = (mm << (64 - sh)) | (ll >>> sh) | (ll > 0 ? 1 : 0);
      mm = (hh << (64 - sh)) | (mm >>> sh);
      hh = hh >>> sh;
      low = hh & 0x7ff;
      ex += sh;
    }
    double h = hh >>> 11, l; // significant bits from a->h
    /* If err=0, we are converting a double value, thus low=0, and the
       conversion is exact. */
    if (err == 0)
      l = 0;
    else if (low < 0x400)
      l = 0.25; // round to zero
    else if (low > 0x400)
      l = 0.75; // round away
    else // low = 0x400
    {
      if (mm == 0 && ll == 0)
        l = 0.5; // middle case
      else
        l = 0.75; // round away
    }
    double s = S[(int) a.sgn];
    h = Math.fma(l, s, s * h);
    h *= 0x1p-52;
    // now -1021 <= ex <= 1024, thus 2^(ex-1) does not underflow/overflow
    return h * asLdexp(1.0, ex - 1);
  }

  /* Put in r an approximation of 1/A, assuming A is not zero.
     Assuming 1 <= r <= 2, the absolute error is bounded by 2^-103.9.
     In terms of ulp, it is bounded by 2^87.1 * ulp(r). */
  private static void invTint(Tint64 r, Tint64 A) {
    Tint64 q = new Tint64();
    double a = tintTod(A, 0, 0, 0); // exact
    // To simplify the error analysis, we assume 0.5 <= a < 1
    int subnormal = Math.abs(a) < 0x1p-1022 ? 1 : 0;
    if (subnormal != 0)
      a *= 0x1p53;
    tintFromd(r, 1.0 / a); // accurate to about 53 bits
    if (subnormal != 0)
      r.ex += 53;
    /* We have 1 <= r <= 2, with |r - 1/a| < ulp(r) = 2^-52. */
    /* We use Newton's iteration: r1 = r0 + r0*(1-a*r0).
       Let e0 = 1-a*r0 and e1 = 1-a*r1 then we have e1 = e0^2.
       Since a < 1 and |r - 1/a| < 2^-52 we have e0 < 2^-52 thus e1 < 2^-104.
    */
    mulTint(q, A, r);      // approximates a*r
    /* The rounding error in mul_tint is bounded by 10 ulps (on 192 bits),
       and a*r < 2, thus we have |q - ar| < 10*2^-191 < 2^-187.
       This error is multiplied by r below, thus contributes to < 2^-186. */
    q.sgn = 1 - q.sgn;     // -a*r
    addTint(q, ONE, q);   // approximates 1-a*r
    /* The rounding error in add_tint is bounded by 2 ulps (on 192 bits),
       and we have |round(1-a*r)| < 2^-51 thus this is bounded by 2*2^-243
       = 2^-242. This error is multiplied by r below, thus contributes to
       < 2^-241. */
    mulTint(q, r, q);      // approximates r*(1-a*r)
    /* Since |q_in| < 2^-51 and r <= 2, we have |q| < 2^-50, and the rounding
       error on mul_tint is bounded by 10 ulps (on 192 bits), it is bounded
       by 10*2^-242 < 2^-238. */
    addTint(r, r, q);
    /* The rounding error in add_tint is bounded by 2 ulps (on 192 bits),
       and we have |r| <= 2, thus it is bounded by 2*2^-191 = 2^-190. */

    /* The total error on r is bounded by:
     * 2^-104 for the mathematical error
     * 2^-186 + 2^-241 + 2^-238 + 2^-190 < 2^-185 for the rounding errors,
     thus by 2^-103.9.
     Since 1 <= r <= 2, this is bounded by 2^87.1 * ulp(r). */
  }

  /* Put in r an approximation of b/a, assuming a is not zero,
     with relative error bounded by 2^-185.53. */
  private static void divTint(Tint64 r, Tint64 b, Tint64 a) {
    Tint64 Y = new Tint64(), Z = new Tint64();
    // to simplify the error analysis, we assume 0.5 <= a, b < 1
    invTint(Y, a); // |Y - 1/a| < 2^-103.9, with Y <= 2
    mulTint(r, Y, b); // r approximates b/a
    /* The rounding error of mul_tint is at most 10 ulps, thus since r <= 2
       this corresponds to < 2^187: |r - Y*b| < 2^187. Now |Y - 1/a|,
       thus |r - b/a| <= |r - Y*b| + b*|Y-1/a| < 2^187 + 2^-103.9 < 2^-103.89. */
    /* We use Karp-Markstein's trick: r1 = r0 + y*(b-a*r0).
       Let e0 = b-a*r0 and e1 = b-a*r1, we have e1 = e0*(1-a*y).
       Since |Y - 1/a| < 2^-103.9 and a < 1 we have |1-a*y| < 2^-103.9.
       Since |r - b/a| < 2^-103.89 and a < 1 we have |e0| < 2^-103.89.
       This gives |e1| < 2^-103.89 * 2^-103.9 < 2^-207. */
    mulTint(Z, a, r);  // approximates a*r
    /* We have |z| <= 2 and the rounding error is bounded by 10 ulps,
       thus < 2^-187.67. This error is multiplied by Y <= 2 below,
       thus contributes to < 2^-186.67. */
    Z.sgn = 1 - Z.sgn; // -a*r
    addTint(Z, b, Z);  // approximates b-a*r
    /* Since |b-a*r| < 2^-103.89, and |Zin-ar| < 2^-186.67, we have
       |b-Zin| < 2^-103.89 + 2^-186.67 < 2^-103.88, thus since the
       rounding error is at most 2 ulps, it is < 2*2^-295 = 2^-294.
       This error is multiplied by Y <= 2 below, thus contributes to < 2^-293. */
    mulTint(Z, Y, Z);  // approximates y*(b-a*r)
    /* Since Y <= 2 and |Zin| < 2^-103.88, we have |Z| < 2^-102.88.
       The rounding error of mul_tint is thus bounded by 10*ulp(2^-102.88)
       = 10 * 2^-294 < 2^-290.67. */
    addTint(r, r, Z);
    /* Since r <= 2, and the rounding error is bounded by 2 ulps,
       it is bounded by 2 * ulp(1.5) = 2 * 2^-191 = 2^-190. */

    /* The total error is bounded by:
     * 2^-207 for the mathematical error e1
     * 2^-186.67 + 2^-293 + 2^-290.67 + 2^-190
     This gives a bound of 2^-186.53 for 1/2 <= r <= 2,
     thus a relative error < 2^185.53. */
  }

  // same as div_tint, taking doubles, same relative error bound of 2^185.53
  private static void divTintD(Tint64 r, double b, double a) {
    Tint64 A = new Tint64(), B = new Tint64();

    tintFromd(A, a);
    tintFromd(B, b);
    divTint(r, B, A);
  }

  private static final Tint64 ZERO = new Tint64(0, 0, 0, -1076, 0);
  private static final Tint64 ONE = new Tint64(0x8000000000000000L, 0, 0, 1, 0);
  private static final Tint64 PI = new Tint64(
      0xc90fdaa22168c234L, 0xc4c6628b80dc1cd1L, 0x29024e088a67cc74L, 2, 0);
  private static final Tint64 PI2 = new Tint64(
      0xc90fdaa22168c234L, 0xc4c6628b80dc1cd1L, 0x29024e088a67cc74L, 1, 0);
  private static final Tint64[] P = {
   new Tint64(0x82703f8b53112ecaL, 0x71ce6279d467aa45L, 0x19c9b28466b6fc0L, -10, 0),
   new Tint64(0xb3cf74b427d53e03L, 0xee590b839e751890L, 0xf2570b12bcb2381aL, -8, 0),
   new Tint64(0xb08498b17b88c39aL, 0x11e37821d784d8faL, 0x2795b14b84b9d78cL, -6, 0),
   new Tint64(0xfd4baa7bb52b0d83L, 0xb25425beb0a04dc3L, 0x92008d9fb34b68ffL, -5, 0),
   new Tint64(0x96541948b85fb386L, 0xe0b815ace425a859L, 0xbeca17c69bb4f7a0L, -3, 0),
   new Tint64(0x96aaa5d4baffc590L, 0xf23db783b6e4ce55L, 0x2c54e054d3b69f7eL, -2, 0),
   new Tint64(0x83cd41b5fa862b11L, 0xad8f9d6e3eb9d833L, 0x4ef470d801fa03caL, -1, 0),
   new Tint64(0xcbbc7752197cc106L, 0xbc66cfb3edefa6e3L, 0x568b2f9e4feb8910L, -1, 0),
   new Tint64(0x8cf8f7c585767042L, 0xcd92a5b9ac13d8c8L, 0x43bd4a023dbe04dL, 0, 0),
   new Tint64(0xafdc36d6b36139bfL, 0x11df63474e3859d4L, 0x42b30b2c089a2b6cL, 0, 0),
   new Tint64(0xc6f8fb1463822519L, 0x2f9f04cf55c10377L, 0x1c44675c6d483606L, 0, 0),
   new Tint64(0xcce79f10f754e315L, 0x91ce47edcbc8a4eeL, 0xb2a6f721ac656b42L, 0, 0),
   new Tint64(0xc090384dbd77132eL, 0x1dfb4d9a1d595de7L, 0xe506ca899a394f70L, 0, 0),
   new Tint64(0xa556ac9eee25e867L, 0xd84dafa25141d203L, 0xf1c7c2a5156b3ec3L, 0, 0),
   new Tint64(0x81bd2677ddfcbc38L, 0x1df33f27dbb60939L, 0xcb4c2c73286a6c29L, 0, 0),
   new Tint64(0xb9ed12169d72e8d9L, 0xc81d95dcef13c227L, 0x974b95796b93c332L, -1, 0),
   new Tint64(0xf2df6da875b666faL, 0xc29dd3a69c274c65L, 0x83b9260976c8f0c5L, -2, 0),
   new Tint64(0x902d34d4db910ed8L, 0x6f3ecddfd6af0a9fL, 0x8e950d7e70a8e9b2L, -2, 0),
   new Tint64(0x9aefa41961ce1207L, 0xacd0317666753751L, 0x3eaf42a53fcc6534L, -3, 0),
   new Tint64(0x95db9977c5e6e263L, 0x85367a63fab8a720L, 0xfc2b63bd115998d8L, -4, 0),
   new Tint64(0x817cb6d8236d5f4dL, 0x70c7815f28909d9bL, 0xc54e97155d12158L, -5, 0),
   new Tint64(0xc5eec4228c352098L, 0x789e56be1dee9b67L, 0xba25bda81292ccc1L, -7, 0),
   new Tint64(0x840c38ccfcb5dc64L, 0xb55a44095bf7f312L, 0x6668b8792d4ecd08L, -8, 0),
   new Tint64(0x97074af7b286b19eL, 0xc20aac21f3130f1dL, 0x369e9fdbdd48528aL, -10, 0),
   new Tint64(0x9054011c32d4b7a5L, 0xc68d3c5d525f4af4L, 0xeeb3c98615dee188L, -12, 0),
   new Tint64(0xdddb4a2eaab2bb15L, 0x753761a8f0e9aa8dL, 0x3f20620cd3e075bbL, -15, 0),
   new Tint64(0x80ef9a1b3e59abe5L, 0x18e6afe0651fdf49L, 0xd1c0329e6a702d9dL, -17, 0),
   new Tint64(0xca16dd49cd30854cL, 0xbad8ca379401027cL, 0x1fc7f47985486f16L, -21, 0),
   new Tint64(0xa0ba01b8742ba801L, 0xe6dc14a9516d7f04L, 0x1f5303d878905d2fL, -25, 0),
   new Tint64(0x9c0440896ce6515cL, 0xcf3470fa7f10ac3L, 0xa9dc80682c111a9dL, -59, 1),
};
  private static final Tint64[] Q = {
   new Tint64(0x82703f8b53112ecaL, 0x71ce6279d467aa45L, 0x19c9b28466b6fc0L, -10, 0),
   new Tint64(0xb3cf74b427d53e03L, 0xee590b839e751890L, 0xf2570b12bcb238cfL, -8, 0),
   new Tint64(0xb33c44af0d43c9e8L, 0xf4426f84614701dbL, 0x979e49dc5b858442L, -6, 0),
   new Tint64(0x8264d256f0bf4d61L, 0xee21431cc0f29764L, 0xce0cc1b593ac9952L, -4, 0),
   new Tint64(0x9d97c4947687b2eeL, 0xef4087c49dde5229L, 0x742bc6e02bfb6df8L, -3, 0),
   new Tint64(0xa0f888dc7aab949eL, 0xf623f19ac413db23L, 0x854744a164f70ecdL, -2, 0),
   new Tint64(0x8fd9c7172ab0828eL, 0x1a9f72fadafc3dfbL, 0xaf96a180ea297178L, -1, 0),
   new Tint64(0xe38168b78a51ee15L, 0x604a47815c8091ebL, 0x9faaa42312e8eb30L, -1, 0),
   new Tint64(0xa164ccdbb65ee133L, 0xe623433c5e086402L, 0xcb228afd335a245fL, 0, 0),
   new Tint64(0xced0c0cbe44fac2fL, 0xd7c2adfc7c5dda9eL, 0xa22b4a311cf5a5b1L, 0, 0),
   new Tint64(0xf0e6660438c90371L, 0x3dca1a0f58f0d943L, 0x8a92b9de68c4488cL, 0, 0),
   new Tint64(0x8000000000000000L, 0x0L, 0x0L, 1, 0),
   new Tint64(0xf8ea68f9ad849103L, 0xb257e4bfc606967fL, 0x818fa2816928c4aaL, 0, 0),
   new Tint64(0xddc64eeee3506234L, 0x47a33ea40a2b22e4L, 0xa6a2c129ace2470aL, 0, 0),
   new Tint64(0xb52ddfba2d37c8e2L, 0x24551a9b19fa064aL, 0x3d78c68b54bc3850L, 0, 0),
   new Tint64(0x87ac113d26b683a1L, 0x4e97756c38785b0bL, 0xf75f0a4506931ba8L, 0, 0),
   new Tint64(0xba065070b900f3d5L, 0xe49d8f9f238f1eebL, 0x6805d48d67f1dcd9L, -1, 0),
   new Tint64(0xe902d2a4bfc22441L, 0x945cfe6c83a5b828L, 0x519ebb457f8ccca0L, -2, 0),
   new Tint64(0x84e405c915f7769bL, 0xfaaf34c3c51dd39L, 0xa97fa0ae99382c93L, -2, 0),
   new Tint64(0x896da46c7e6cebb3L, 0xe0afc0b6db3dbbf5L, 0x61ccff6e78efff4L, -3, 0),
   new Tint64(0x801a0c1a0dfefc92L, 0xf8e0a97902e08d47L, 0x81898e0b7d8ef0bcL, -4, 0),
   new Tint64(0xd59d088930df07dbL, 0xa8cb00021849fbafL, 0xf80cc91aa0bddf89L, -6, 0),
   new Tint64(0x9db56a6984b77231L, 0xcf7c2074e0480959L, 0x3316ecaa6aa4604eL, -7, 0),
   new Tint64(0xcb7450d9d62d2e68L, 0x9787b4265977d004L, 0xba615cee00a841d7L, -9, 0),
   new Tint64(0xe12a52e02b64b23eL, 0x97c9139a8ced716cL, 0x70a176ad2b5f16d2L, -11, 0),
   new Tint64(0xd057b6d0900ed30dL, 0x5e04bb17eb879a5eL, 0x23ed135b60bea070L, -13, 0),
   new Tint64(0x9b1f1ca22fef7ae5L, 0x19a687f0a4073eeeL, 0x5a9ccbc8c14578bL, -15, 0),
   new Tint64(0xaeba710f053aac3bL, 0x30ea5d6fb116f775L, 0x8a9bcfef5698f8f1L, -18, 0),
   new Tint64(0x84b9ada076cd007dL, 0x13e55006073f76b1L, 0x4cf4259968228f3eL, -21, 0),
   new Tint64(0xcca4b9046786937dL, 0x233c1e691c127c1fL, 0xf493e0b7a3de459aL, -26, 0),
};
  private static final double[][] finf = {{0x1p-55, 0x1.921fb54442d18p-1}, {0x1p-54, 0x1.2d97c7f3321d2p+1}};
  private static final double[] asgn = {0.0, -0.0};
  private static final double[] T2 = {
    0x0p+0, 0x1p-6, 0x1p-5, 0x1.8p-5, 0x1p-4, 0x1.4p-4, 0x1.8p-4, 0x1.cp-4,
    0x1p-3, 0x1.2p-3, 0x1.4p-3, 0x1.6p-3, 0x1.8p-3, 0x1.ap-3, 0x1.cp-3, 0x1.ep-3,
    0x1p-2, 0x1.1p-2, 0x1.2p-2, 0x1.3p-2, 0x1.4p-2, 0x1.5p-2, 0x1.6p-2, 0x1.7p-2,
    0x1.8p-2, 0x1.9p-2, 0x1.ap-2, 0x1.bp-2, 0x1.cp-2, 0x1.dp-2, 0x1.ep-2, 0x1.fp-2,
    0x1p-1, 0x1.08p-1, 0x1.1p-1, 0x1.18p-1, 0x1.2p-1, 0x1.28p-1, 0x1.3p-1, 0x1.38p-1,
    0x1.4p-1, 0x1.48p-1, 0x1.5p-1, 0x1.58p-1, 0x1.6p-1, 0x1.68p-1, 0x1.7p-1, 0x1.78p-1,
    0x1.8p-1, 0x1.88p-1, 0x1.9p-1, 0x1.98p-1, 0x1.ap-1, 0x1.a8p-1, 0x1.bp-1, 0x1.b8p-1,
    0x1.cp-1, 0x1.c8p-1, 0x1.dp-1, 0x1.d8p-1, 0x1.ep-1, 0x1.e8p-1, 0x1.fp-1, 0x1.f8p-1, 0x1p+0};
  private static final double[][] f2 = {
    {0x0p+0, 0x0p+0}, {-0x1.95220c39d4dffp-53, 0x1.fff555bbb73p-7},
    {0x1.2542779d776dep-53, 0x1.ffd55bba976p-6}, {-0x1.6061bbe3de53cp-53, 0x1.7fb818430da4p-5},
    {-0x1.639269b0da47ep-53, 0x1.ff55bb72cfep-5}, {-0x1.4a7663af440f7p-55, 0x1.3f59f0e7c55ap-4},
    {0x1.d1824d59f9e13p-53, 0x1.7ee182602f1p-4}, {0x1.bef71e5340b31p-55, 0x1.be39ebe6f07cp-4},
    {0x1.b8cb225e627dp-53, 0x1.fd5ba9aac2f6p-4}, {0x1.b92de9bac94c2p-53, 0x1.1e1fafb04372p-3},
    {-0x1.d3cb89e62dafdp-54, 0x1.3d6eee8c6627p-3}, {-0x1.882a55960087ap-53, 0x1.5c9811e3ec27p-3},
    {0x1.1347b0b4f881dp-54, 0x1.7b97b4bce5bp-3}, {0x1.873d8079ed0d2p-53, 0x1.9a6a8e96c862p-3},
    {0x1.022f621a5c1cbp-54, 0x1.b90d7529260ap-3}, {0x1.9c648d1534598p-53, 0x1.d77d5df20573p-3},
    {-0x1.4ea9238610a08p-54, 0x1.f5b75f92c80ep-3}, {0x1.2c5c8e721970dp-53, 0x1.09dc597d8636p-2},
    {0x1.30ca4748b1bf9p-57, 0x1.18bf5a30bf178p-2}, {-0x1.20ef9ba6dbf9p-53, 0x1.278372057ef48p-2},
    {0x1.e69c5abb498d2p-53, 0x1.362773707ebc8p-2}, {0x1.a8a86f0ea9311p-54, 0x1.44aa436c2af08p-2},
    {0x1.db5336feef7fp-54, 0x1.530ad9951cd48p-2}, {0x1.9636a3aa3b84p-54, 0x1.614840309cfep-2},
    {0x1.1ce2a8c848b74p-55, 0x1.6f61941e4defp-2}, {-0x1.4b1bbd1ea6db3p-55, 0x1.7d5604b63b3f8p-2},
    {-0x1.4925e8b916e0bp-53, 0x1.8b24d394a1b28p-2}, {0x1.9e6c988fd0a77p-56, 0x1.98cd5454d6b18p-2},
    {-0x1.a49bd836a17p-53, 0x1.a64eec3cc24p-2}, {-0x1.ca3cf09c6b5f8p-53, 0x1.b3a911da65c7p-2},
    {-0x1.cc1ce70934c34p-56, 0x1.c0db4c94ec9fp-2}, {0x1.2e982ddf3872ap-55, 0x1.cde53432c135p-2},
    {-0x1.2ea406ee84d0fp-55, 0x1.dac670561bb5p-2}, {-0x1.de35847c81979p-53, 0x1.e77eb7f175a38p-2},
    {-0x1.a3992dc382a23p-57, 0x1.f40dd0b541418p-2}, {-0x1.b32c949c9d593p-55, 0x1.0039c73c1a40cp-1},
    {-0x1.d5b495f6349e6p-56, 0x1.0657e94db30dp-1}, {-0x1.f34582f6255fep-53, 0x1.0c6145b5b43dcp-1},
    {0x1.ed42511e3f11dp-54, 0x1.1255d9bfbd2a8p-1}, {-0x1.1cef189ff9e7fp-54, 0x1.1835a88be7c14p-1},
    {-0x1.928df287a668fp-58, 0x1.1e00babdefeb4p-1}, {-0x1.e3bde360c7ddbp-53, 0x1.23b71e2cc9e6cp-1},
    {0x1.bd86313ce4fdep-54, 0x1.2958e59308e3p-1}, {-0x1.8e8a85803cc1dp-53, 0x1.2ee628406cbccp-1},
    {-0x1.77ef7641c777fp-54, 0x1.345f01cce37bcp-1}, {0x1.b73ef3389d02fp-53, 0x1.39c391cd41718p-1},
    {0x1.ecf8b492644fp-56, 0x1.3f13fb89e96f4p-1}, {0x1.c1125fd3810c7p-53, 0x1.445065b795b54p-1},
    {0x1.2483350fe548bp-53, 0x1.4978fa3269eep-1}, {0x1.4a33dbeb3796cp-55, 0x1.4e8de5bb6ec04p-1},
    {-0x1.46edd2af69483p-53, 0x1.538f57b89062p-1}, {-0x1.2bcb93b18b52ap-53, 0x1.587d81f732fbcp-1},
    {0x1.0028e4bc5e7cap-57, 0x1.5d58987169b18p-1}, {0x1.ed487acaf1174p-53, 0x1.6220d115d7b8cp-1},
    {-0x1.2dd4dfd7d1777p-53, 0x1.66d663923e088p-1}, {0x1.2bfe3cf3b9d79p-54, 0x1.6b798920b3d98p-1},
    {-0x1.8c34d25aadef6p-56, 0x1.700a7c5784634p-1}, {-0x1.f426acf4d3bdbp-54, 0x1.748978fba8e1p-1},
    {-0x1.afe57dd9ff23p-53, 0x1.78f6bbd5d316p-1}, {-0x1.54fbef0e862abp-54, 0x1.7d528289fa094p-1},
    {0x1.90227758b11bap-54, 0x1.819d0b7158a4cp-1}, {0x1.16b66e7fc8b8cp-53, 0x1.85d69576cc2c4p-1},
    {-0x1.55b9a5e177a1bp-55, 0x1.89ff5ff57f1f8p-1}, {0x1.c27cfaa9f7a14p-53, 0x1.8e17aa99cc05cp-1},
    {0x1.1a62633145c07p-55, 0x1.921fb54442d18p-1}};
  private static final double[][] O = {
    {0,0}, {0x1.921fb54442d18p+0,0x1.1a62633145c07p-54},
    {0,0}, {-0x1.921fb54442d18p+0,-0x1.1a62633145c07p-54},
    {0x1.921fb54442d18p+1,0x1.1a62633145c07p-53}, {0x1.921fb54442d18p+0,0x1.1a62633145c07p-54},
    {-0x1.921fb54442d18p+1,-0x1.1a62633145c07p-53}, {-0x1.921fb54442d18p+0,-0x1.1a62633145c07p-54}};
  private static final double[] b = {-0x1.55555555554d2p-2, 0x1.999999860e1cap-3, -0x1.248ad469844a1p-3};
  private static final double[] b2 =
      {-0x1.5555555555555p-2, 0x1.999999999755ep-3, -0x1.24924883596f8p-3, 0x1.c6f7d73531bc2p-4};
  private static final double[] S = {1.0, -1.0};

  // use a type [29,29] rational approximation of atan(z) for 0 <= z <= 1
  private static double atan2_accurate(double y, double x) {
    double res;
    /* First check when t=y/x is small and exact and x > 0, since for
       |t| <= 0x1.d12ed0af1a27fp-27, atan(t) rounds to t (to nearest). */
    double t = y / x;

    /* If t = y/x did underflow for x > 0, then atan(y/x) will underflow
       too, since the Taylor expansion of atan(z) is z - z^3/3 + o(z^3).
       If |t| < 2^-1022 and is exact, then atan(y/x) underflows, and also
       when |t| = 2^-1022, is exact, and rounding is toward zero. */

    /* If t is exact and underflows, then atan(y/x) rounds to t for x > 0,
       to pi for y > 0 and x < 0, and to -pi for x, y < 0. */
    if (t == 0) {
      if (x > 0) {
        // the C source reports an underflow to errno here
        return t;
      }
      res = (y > 0) ? PI_H + PI_L : -PI_H - PI_L;
      return res;
    }
    double corr = Math.fma(t, x, -y);
    if (corr == 0 && x > 0) // t is exact
      if (Math.abs(t) <= 0x1.d12ed0af1a27fp-27)
      {
        // Warning: if y is in the subnormal range, t might differ from y/x
        /* If |y| >= 2^-969, then since t*x has at most 106 significant bits,
           and t*x ~ y, the lower bit of t*x is >= 2^-1074, thus there is no
           underflow in t*x-y. */
        if (Math.abs(y) >= 0x1p-915) {
          // the C source reports an underflow to errno here
          return Math.fma(t, -0x1p-54, t);
        }
        /* Now |y| < 2^-969, since x >= 2^-1074, then t <= 2^105, thus we can
           scale y and t by 2^105, which will ensure t*x-y does not underflow. */
        corr = Math.fma(t * 0x1p105, x, -y * 0x1p105);
        if (corr == 0) {
          res = Math.fma(t, -0x1p-54, t);
          return res;
        }
      }

    int inv = Math.abs(y) > Math.abs(x) ? 1 : 0;
    Tint64 z = new Tint64(), p = new Tint64(), q = new Tint64();
    if (inv != 0)
      divTintD(z, x, y);
    else
      divTintD(z, y, x);

    /* When |y/x| < 2^-27, x > 0, atan(y/x) rounds to the same value as y/x
       pertubed by a small amount towards zero (here we subtract 2 to z->l).
       But since the Taylor expansion of atan(t) is t - t^3/3 + O(t^5),
       we have a relative error bounded by t^2/2 for t small enough.
       We thus need |y/x| < 2^-96 so that this error is less than 1 ulp. */
    if (inv == 0 && x > 0 && z.ex <= -96)
      {
        z.l -= 2;
        z.m -= Long.compareUnsigned(z.l, 2) < 0 ? 1 : 0;
        z.h -= Long.compareUnsigned(z.m, 1) < 0 ? 1 : 0;
        res = tintTod(z, 1, y, x);
        return res;
      }

    // below when we write y/x it should be read x/y when |x/y| < 1
    // |z - y/x| < 2^-185.53 * |z| (relative error from div_tint_d)
    // the rational approximation is only for z > 0, it is not antisymmetric
    int sz = (int) z.sgn;
    z.sgn = 0;
    cpTint(p, P[29]);
    cpTint(q, Q[29]);
    for (int i = 28; i >= 0; i--)
    {
      mulTint(p, p, z);
      mulTint(q, q, z);
      addTint(p, p, P[i]);
      addTint(q, q, Q[i]);
    }
    // multiply p by z
    mulTint(p, p, z);
    /* The routine errPsplit(e,13) in atan2.sage gives a relative error bound
       of 2^-184.14 for |p - z*P(z)|, for -11 <= e <= 0, which corresponds
       to 2^-12 <= z <= 1. */
    /* The routine errQsplit(e,12) in atan2.sage gives a relative error bound
       of 2^-184.19 for |q - Q(z)|, for -11 <= e <= 0, which corresponds
       to 2^-12 <= z <= 1. */
    // divide p by q
    divTint(z, p, q);
    /* The relative error of div_tint() is <= 2^-185.53, thus we have:
       z*P(z)/Q(z) = atan(z) * (1 + eps0) with |eps0| < 3.99613e-59
       z = y/x * (1 + eps1) with |eps1| < 2^-185.53
       p = z*P(z) * (1 + eps2) with |eps2| < 2^-184.14
       q = Q(z) * (1 + eps3)   with |eps3| < 2^-184.19
       newz = p/q * (1 + eps4) with |eps4| < 2^-185.53
       The equality z = y/x * (1 + eps1) gives
       atan(z) = atan(y/x) + eps1*y/x * 1/(1+theta^2) for theta in (z,y/x).
       Thus |atan(z) - atan(y/x)| <= |eps1*y/x| which yields
       |atan(z) - atan(y/x)|/|atan(y/x)| <= |eps1*y/x|/|atan(y/x)|
       Since t/atan(t) is bounded by 1/atan(1) for 0 <= x <= 1, this yields:
       atan(z) = atan(y/x) * (1 + eps5) with |eps5| <= eps1/atan(1) < 2^-185.18.
       In summary we have:
       newz = atan(y/x)*(1+eps0)*(1+eps2)*(1+eps4)*(1+eps5)/(1+eps3)
       thus:
       newz = atan(y/x)*(1+eps6) with |eps6| < 2^-182.63.
       This corresponds to a maximal error of 2^-182.63*2^192 <= 662 ulps.
    */
    long err = 662; // error bound in case inv=0 and x > 0
    z.sgn = sz; // restore sign
    /* Now z approximates atan(y/x) for inv=0, and atan(x/y) for inv=1,
       with -pi/4 < z < pi/4.
    */
    if (inv != 0)
    {
      // if x/y > 0 thus atan(x/y) > 0 we apply pi/2 - atan(x/y)
      // if x/y < 0 thus atan(x/y) < 0 we apply -pi/2 - atan(x/y)
      if (z.sgn == 0) { // 0 < atan(x/y) < pi/4
        z.sgn = 1;
        addTint(z, PI2, z);
        /* Now pi/4 < z < pi/2. The absolute error on z was bounded by
           2^-182.63*pi/4, the error on PI2 is bounded by 2^-197.96, and
           the add_tint() error is bounded by 2 ulp(pi/2) = 2^-190,
           which yields a total error < 2^-182.63*pi/4 + 2^-197.96 + 2^-190
           < 2^-182.967. Relatively to ulp(pi/4) this is less than 524. */
      }
      else // -pi/4 < atan(x/y) < 0
      {
        addTint(z, PI2, z);
        z.sgn = 1;
        /* Now -pi/2 < z < -pi/4. The same error analysis as above applies,
           thus we get the same bound of 524 ulps. */
      }
      err = 524;
    }
    // now -pi/2 < z < pi/2
    // if x is negative we go to the opposite quadrant
    if (x < 0) {
      if (z.sgn == 0) { // 1st quadrant -> 3rd quadrant (subtract pi)
        z.sgn = 1;
        addTint(z, PI, z);
        z.sgn = 1;
        /* We had 0 < z < pi/2 thus now -pi < z < -pi/2.
           The absolute error on z was bounded by max(2^-182.63*pi/4,2^-182.967)
           = 2^-182.967, that on PI is bounded by 2^-196.96, and the add_tint()
           error is bounded by 2 ulp(pi) = 2^-189, which yields a total error
           < 2^-182.967 + 2^-196.96 + 2^-189 < 2^-182.945.
           Relatively to ulp(pi/2) this is less than 266 ulps. */
      }
      else // 4th quadrant -> 2nd quadrant (add pi)
      {
        addTint(z, PI, z);
        /* If inv=0 we had -pi/4 < z < 0 thus now 3pi/4 < z < pi.
           If inv=1 we had -pi/2 < z < -pi/4 thus now pi/2 < z < 3pi/4.
           The same analysis as above applies, and we get the same bound
           of 266 ulps. */
      }
      err = 266;
    }
    res = tintTod(z, err, y, x);
    return res;
  }

  private static double fasttwosum(double x, double y, double[] e) {
    double s = x + y, z = s - x;
    e[0] = y - z;
    return s;
  }

  private static double fastsum(double xh, double xl, double yh, double yl, double[] e) {
    double[] sl = new double[1];
    double sh = fasttwosum(xh, yh, sl);
    e[0] = (xl + yl) + sl[0];
    return sh;
  }

  private static double asAtan2Special(double y0, double x0) {
    long iy = Double.doubleToRawLongBits(y0), ix = Double.doubleToRawLongBits(x0);
    long aiy = iy << 1, aix = ix << 1;

    if (Long.compareUnsigned(aiy, 0x7ffL << 53) >= 0
        || Long.compareUnsigned(aix, 0x7ffL << 53) >= 0) { // NaN or Inf
      if (Long.compareUnsigned(aiy, 0x7ffL << 53) > 0
          || Long.compareUnsigned(aix, 0x7ffL << 53) > 0)
        // return y0 + x0; // if y or x is sNaN, returns qNaN and raises invalid
        return y0 + x0;
      // Now neither y nor x is NaN, but at least one is +Inf or -Inf
      if (aiy == 0x7ffL << 53 && aix == 0x7ffL << 53) { // both y and x are +/-Inf
        // atan2 (+/-Inf,-Inf) = +/-3pi/4
        // atan2 (+/-Inf,+Inf) = +/-pi/4
        return Math.copySign(finf[(int) (ix >>> 63)][1], y0)
            + Math.copySign(finf[(int) (ix >>> 63)][0], y0);
      }
      // now only one of y and x is +/-Inf
      if (aix == 0x7ffL << 53) {
        if (x0 < 0)
          return Math.copySign(PI_H, y0) + Math.copySign(PI_L, y0);
        // atan2(+/-0,x) = +/-0 for x > 0
        // atan2(+/-y,+Inf) = +/-0 for finite y>0
        return Math.copySign(0, y0);
      }
      // now y = +/-Inf
      // atan2(+/-Inf,x) = +/-pi/2 for finite x
      return Math.copySign(PI_OVER2_H, y0) + Math.copySign(PI_OVER2_L, y0);
    }

    if (aiy == 0 || aix == 0) {
      if (aiy == 0 && aix == 0) {
        if (ix == 0) // atan2(+/-0, +0) = +/-0
          return y0;
        // atan2(+/-0, +0) = +/-pi
        return (iy == 0) ? PI_H + PI_L : -PI_H - PI_L;
      }
      // only one of y and x is zero
      if (aiy == 0) {
        // atan2(+/-0,x) = +/-0 for x>0
        if (x0 > 0) return y0 / x0;
        // atan2(+/-0,x) = +/-pi for x<0
        return ((iy >>> 63) == 0) ? PI_H + PI_L : -PI_H - PI_L;
      }
      // now only x is zero
      // atan2(y,+/-0) = -pi/2 for y<0
      // atan2(y,+/-0) = +pi/2 for y>0
      return (y0 > 0) ? PI_OVER2_H + PI_OVER2_L : -PI_OVER2_H - PI_OVER2_L;
    }
    return 0;
  }

  public static double compute(double y0, double x0) {
    long iy = Double.doubleToRawLongBits(y0), ix = Double.doubleToRawLongBits(x0);
    long aiy = iy & MASK;
    if (aiy == 0 || Long.compareUnsigned(aiy, 0x7ffL << 52) >= 0)
      return asAtan2Special(y0, x0);
    long aix = ix & MASK;
    if (aix == 0 || Long.compareUnsigned(aix, 0x7ffL << 52) >= 0)
      return asAtan2Special(y0, x0);
    double ax = Math.abs(x0), ay = Math.abs(y0);
    double x = Math.max(ax, ay), y = Math.min(ax, ay);
    long sy = iy >>> 63, sx = ix >>> 63;
    long GT = Long.compareUnsigned(aix, aiy) < 0 ? 1 : 0;
    long dxy = (aix - aiy) ^ -GT;
    if (Long.compareUnsigned(dxy, 53L << 52) >= 0) return atan2_accurate(y0, x0);
    double sgn = asgn[(int) (GT ^ sx ^ sy)];
    long kw = sx << 2 | sy << 1 | GT;
    double jj = y / x + (2 + 1 / 128.);
    long jt = ((Double.doubleToRawLongBits(jj) >>> (52 - 7)) & 127);
    double fh = f2[(int) jt][1] * Math.copySign(1, sgn);
    double fl = f2[(int) jt][0] * Math.copySign(1, sgn);
    fh += O[(int) kw][0];
    fl += O[(int) kw][1];
    if (x < 0x1p-920) { x *= 0x1p920; y *= 0x1p920; }
    if (x > 0x1p1022) {
      if (jt != 0) {
        x *= 0x1p-1; y *= 0x1p-1;
      }
    }
    double t0 = T2[(int) jt];
    double zn = Math.fma(-t0, x, y), zd = Math.fma(t0, y, x);
    double z = zn / zd;
    double z2 = z * z;
    z *= Math.copySign(1, sgn);
    double dz = (z * z2) * (b[0] + z2 * (b[1] + z2 * b[2]));
    // this bound is obtained with 1.1e10 random argument pairs in the roundup mode;
    // the obtained maximal difference is increased by 2.5% for safety
    double eps = Math.abs(z) * 0x1.051p-51 + 0x1p-90;
    double[] zl = new double[1], nl = new double[1], flp = new double[1];
    double rh = fasttwosum(fh, z, zl);
    double rl = (fl + dz) + zl[0];
    double lb = rh + (rl - eps), ub = rh + (rl + eps);
    if (lb != ub) {
      double dh = y * t0, dl = Math.fma(y, t0, -dh), e, rdh;
      double[] ep = new double[1];
      dh = fasttwosum(x, dh, ep);
      e = ep[0];
      // avoid spurious underflow in 1/dh
      // the C source saves and restores the underflow flag around this division
      rdh = 1 / dh;
      dl += e;
      double nh = x * t0;
      nl[0] = Math.fma(x, t0, -nh);
      double dt = y - nh, y1 = dt + nh;
      if (y1 == y) {
        nh = fasttwosum(dt, -nl[0], nl);
      } else {
        nh = fasttwosum(dt, (y - y1) - nl[0], nl);
      }
      double zh = nh * rdh;
      z2 = zh * zh;
      double zl2 = rdh * (Math.fma(dh, -zh, nh) + (nl[0] - (nh * rdh) * dl));
      zl2 += zh * z2 * ((b2[0] + z2 * b2[1]) + (z2 * z2) * (b2[2] + z2 * b2[3]));
      zh *= Math.copySign(1, sgn);
      zl2 *= Math.copySign(1, sgn);
      eps = 0x1.4p-50 * (Math.abs(zh) * z2 + 0x1p-51);
      fh = fastsum(fh, fl, zh, zl2, flp);
      fl = flp[0];
      lb = fh + (fl - eps);
      ub = fh + (fl + eps);
      if (lb != ub)
        return atan2_accurate(y0, x0);
    }
    return ub;
  }

}
