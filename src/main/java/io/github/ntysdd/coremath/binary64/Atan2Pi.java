/*
 * Correctly rounded arctangent of y/x for binary64 values, in half-revolution
 * units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/atan2pi/atan2pi.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atan2pi -> Atan2Pi.compute
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

package io.github.ntysdd.coremath.binary64;

import io.github.ntysdd.coremath.internal.Tint64;
import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded {@code atan2(y, x)/pi} of two binary64 values, the
 * translation of the CORE-MATH {@code cr_atan2pi}.
 *
 * <p>Two paths. {@code atan2piFast} works in double-double: it divides the
 * smaller by the larger magnitude with Karp-Markstein's trick, evaluates one of
 * the 65 degree-9 polynomials of {@code Pfast} (the row is the integer part of
 * 64 times the ratio), multiplies by {@code ONE_OVER_PI_H + ONE_OVER_PI_L} and
 * folds the quadrant back with {@code dd_sum1}; the per-row bounds of
 * {@code err_fast} decide whether the result can be rounded.
 * {@code atan2piAccurate} redoes everything in the 192-bit fixed point of
 * {@code Tint64} with a [29,29] rational approximation from the tables
 * {@code P} and {@code Q}, multiplies by the {@code Tint64} constant
 * {@code ONE_OVER_PI}, folds the quadrant with the {@code ONE_HALF} and
 * {@code ONE} constants, and rounds once with {@code tintTod} and its 683-ulp
 * error bound. {@code compute} handles the NaN, infinity and zero cases first
 * and then chooses between the two.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code d64u64} becomes {@link Double#doubleToRawLongBits};
 *       {@code #define MASK} becomes a field, and the shifts of {@code ay},
 *       {@code ax} and {@code uy} are {@code >>>}, as the C values are
 *       {@code uint64_t}.
 *   <li>{@code is_nan} and {@code is_inf} return a {@code boolean}, they are
 *       only tested.
 *   <li>{@code #include "tint.h"} is carried in this class, as upstream does:
 *       the 192-bit helpers and the {@code Tint64} constants are the same ones
 *       {@code Atan2.java} already translated, with the same camelCase names,
 *       and {@code print_tint}, {@code is_normalized} and the {@code exit (1)}
 *       of an unexpected worst case are dropped exactly as they are there.
 *       {@code ONE}, {@code ONE_HALF} and {@code ONE_OVER_PI} are the three
 *       {@code tint_t} constants this file reads.
 *   <li>{@code static const tint_t P[30]} and {@code Q[30]} become
 *       {@code Tint64[]} built with {@code new Tint64 (h, m, l, ex, sgn)}, in
 *       the same order as the C initialisers.
 *   <li>{@code Pfast[65][10]}, {@code Xfast[65]} and {@code err_fast[65]}
 *       become fields; the per-row C comments, which give the sub-interval and
 *       the relative error bound, are kept.
 *   <li>{@code double *} output parameters become one-element {@code double}
 *       arrays: {@code h} and {@code l} of {@code atan2piFast}, of
 *       {@code fast_div}, of {@code fast_two_sum}, of {@code dd_sum1}, of
 *       {@code a_mul}, of {@code s_mul} and of {@code d_mul}, and the
 *       {@code h}, {@code l} and {@code t} of {@code compute}. {@code z},
 *       {@code p} and {@code q} of {@code atan2piAccurate} are the mutable
 *       {@link Tint64} objects that the C code passes as pointers.
 *   <li>{@code __attribute__((noinline))} on {@code atan2pi_accurate} and
 *       {@code __builtin_expect} are dropped.
 *   <li>{@code __builtin_trunc (64.0 * zh)} is {@code (int) (64.0 * zh)}, which
 *       truncates toward zero in the same range {@code 0 <= z <= 1 + 1/64};
 *       {@code __builtin_fma}, {@code __builtin_fabs} and
 *       {@code __builtin_copysign} are {@link Math#fma}, {@link Math#abs} and
 *       {@link Math#copySign}.
 *   <li>The {@code // atan2pi_begin} / {@code // atan2pi_end} markers and the
 *       comments between them are kept, they are what makes the diff against
 *       {@code atan2.c} readable.
 * </ul>
 */
public final strictfp class Atan2Pi {
  private Atan2Pi() {
  }

  // #define MASK inside cr_atan2pi(), 2^63-1 (mask the sign bit)
  private static final long MASK = 0x7fffffffffffffffL;

  /* ONE_OVER_PI_H+ONE_OVER_PI_L approximates 1/pi with rel. error < 2^-107.871 */
  // #define ONE_OVER_PI_H in atan2pi.c
  private static final double ONE_OVER_PI_H = 0x1.45f306dc9c883p-2;

  // #define ONE_OVER_PI_L in atan2pi.c
  private static final double ONE_OVER_PI_L = -0x1.6b01ec5417056p-56;

  // static const tint_t ONE in tint.h
  private static final Tint64 ONE = new Tint64(
      0x8000000000000000L, 0, 0, 1, 0);

  // static const tint_t ONE_HALF in tint.h
  private static final Tint64 ONE_HALF = new Tint64(
      0x8000000000000000L, 0, 0, 0, 0);

  // static const tint_t ZERO in tint.h
  private static final Tint64 ZERO = new Tint64(0, 0, 0, -1076, 0);

  // the sign table of tint_tod in tint.h
  private static final double[] S = {1.0, -1.0};

  // static const tint_t ONE_OVER_PI in tint.h, 1/pi with rel. error < 2^-198.59
  private static final Tint64 ONE_OVER_PI = new Tint64(
      0xa2f9836e4e441529L, 0xfc2757d1f534ddc0L, 0xdb6295993c439042L, -1, 0);

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

  // assume sign bit was removed
  private static boolean isNan(long u) {
    long e = u >>> 52;
    return e == 0x7ff && u != (e << 52);
  }

  // assume sign bit was removed
  private static boolean isInf(long u) {
    return u == (0x7ffL << 52);
  }

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

  private static final double[][] Pfast = {
    {0,0,1.0,0,0,-0x1.5555555555555p-2,0,0x1.9999999992c29p-3,0,-0x1.249247c670632p-3},
      /* i=0 71.798 */
    {0x1.7fee0184a5c36p-6,-0x1.43d22cd70f533p-60,0x1.ffb80a1e93b34p-1,-0x1.46d03098af947p-55,
     -0x1.7f9416c3bb28ap-6,-0x1.5435ba7ac7b4bp-2,0x1.7ef26a1277a3dp-6,0x1.963bcfff26a3bp-3,
     -0x1.7dfd6148d6a5bp-6,-0x1.201e9fb0fd027p-3}, /* i=1 62.976 */
    {0x1.3fd65f169c9d9p-5,0x1.6fc5a390a776cp-61,0x1.ff384e0187672p-1,-0x1.0ba36e06dfbcap-55,
     -0x1.3f06922fd9dbep-5,-0x1.5238605d11307p-2,0x1.3d91a94508185p-5,0x1.904a9cf742939p-3,
     -0x1.3b6f8b816234cp-5,-0x1.183e25c225415p-3}, /* i=2 63.019 */
    {0x1.bf8ddf139c444p-5,-0x1.8ad3c565de617p-59,0x1.fe792b3ae6e73p-1,0x1.c468eda95f788p-55,
     -0x1.bd5510b2c113bp-5,-0x1.4f40fde893214p-2,0x1.b95b44f7aca2p-5,0x1.877aab2d771dcp-3,
     -0x1.b39bf8a4f8a7bp-5,-0x1.0ca4b8b10bffp-3}, /* i=3 63.055 */
    {0x1.1f86dbf082d59p-4,-0x1.0a6844090e29p-59,0x1.fd7b301722b81p-1,-0x1.b7c4b456d9f1p-58,
     -0x1.1d2c5ee4e9befp-4,-0x1.4b55137eb84cdp-2,0x1.18fa7698d952ep-4,0x1.7bea5f9a87b2bp-3,
     -0x1.12f82c7547b43p-4,-0x1.fb24a1e8f998ep-4}, /* i=4 63.101 */
    {0x1.5f2324fd2d7b2p-4,0x1.89f034812ae7cp-58,0x1.fc3f18b549418p-1,0x1.00afc5c42a07p-59,
     -0x1.5adb99cdd91acp-4,-0x1.467bd93f8f73fp-2,0x1.53443bd1d3a39p-4,0x1.6dc1392d00dffp-3,
     -0x1.487c8d1053674p-4,-0x1.d6b208b39950dp-4}, /* i=5 63.152 */
    {0x1.9e94153cfdcf1p-4,0x1.a2814938690c5p-58,0x1.fac5cdace3776p-1,-0x1.c4388d05d39cp-55,
     -0x1.978c88054ac12p-4,-0x1.40be2665b040ep-2,0x1.8b26b120d0147p-4,0x1.5d2ef98a24997p-3,
     -0x1.79b51526f4a39p-4,-0x1.acb56dcce4cf5p-4}, /* i=6 63.218 */
    {0x1.ddd21701eba6ep-4,0x1.942d2aa4bb587p-58,0x1.f9106266112bap-1,0x1.56a0454b4533ap-56,
     -0x1.d315457777fe6p-4,-0x1.3a26537a90d91p-2,0x1.c045a086b6955p-4,0x1.4a6aa4e4e7f6cp-3,
     -0x1.a6128dbb72b99p-4,-0x1.7e0b919987108p-4}, /* i=7 63.291 */
    {0x1.0e6adccf40882p-3,-0x1.d7826fb4da63bp-57,0x1.f720132978badp-1,0x1.1b41823c3fdc2p-55,
     -0x1.06a70011b812dp-3,-0x1.32c01802fc58p-2,0x1.f24deb38dcd5cp-4,0x1.35b160baffcc4p-3,
     -0x1.cd1f00d45f80dp-4,-0x1.4ba41d5a77389p-4}, /* i=8 63.373 */
    {0x1.2dcbdb2fba1ffp-3,0x1.8e4f6ab5e1a94p-58,0x1.f4f642ee76e94p-1,0x1.138258ed07804p-55,
     -0x1.23089b322f78ep-3,-0x1.2a98645802708p-2,0x1.107b3349ac9d1p-3,0x1.1f453a05a7f64p-3,
     -0x1.ee7f64ad21f5ap-4,-0x1.167aa665241c4p-4}, /* i=9 63.473 */
    {0x1.4d087a9da4f17p-3,0x1.1ec34ee1b5804p-57,0x1.f29478eeb3352p-1,0x1.76602e96f901cp-55,
     -0x1.3e9df6e36e67ep-3,-0x1.21bd38700903dp-2,0x1.260043570b7fbp-3,0x1.076bdbe517f54p-3,
     -0x1.04fa40112f2e7p-3,-0x1.bf1ec6d24067fp-5}, /* i=10 63.584 */
    {0x1.6c1d4898933d9p-3,-0x1.2a337ec184b44p-58,0x1.effc5e06cfb34p-1,-0x1.57d623c96a6dfp-55,
     -0x1.5956f0f53a44dp-3,-0x1.183d78655a04p-2,0x1.399c6a6f83968p-3,0x1.dcda7fd1cfe9fp-4,
     -0x1.0fad8c8089fe1p-3,-0x1.4fc17bcce4393p-5}, /* i=11 63.707 */
    {0x1.8b06ee2879c29p-3,-0x1.11fb58ccc22b4p-57,0x1.ed2fb9ec57f51p-1,-0x1.3bfadbf20c9a6p-55,
     -0x1.7324d58b40c4ap-3,-0x1.0e28bf8b8a6acp-2,0x1.4b3b98ee0d9bp-3,0x1.a924c1a7f814p-4,
     -0x1.1755afe4dcc0bp-3,-0x1.c193045932f6fp-6}, /* i=12 63.836 */
    {0x1.a9c231b403279p-3,0x1.0e2051101d4b8p-57,0x1.ea30704157b4fp-1,0x1.b10a71f45d3b4p-55,
     -0x1.8bfa6e285e227p-3,-0x1.038f32e3900dp-2,0x1.5acf7245187c4p-3,0x1.7447f06cb0afbp-4,
     -0x1.1bfbdd3a7739cp-3,-0x1.d01cb4c7be3a8p-7}, /* i=13 63.985 */
    {0x1.c84bf8a742e6ep-3,-0x1.968989ac4b246p-58,0x1.e7007d8e205eap-1,0x1.1964d0594119ep-55,
     -0x1.a3cc0c751a788p-3,-0x1.f102a76f4427bp-3,0x1.684f3be1ad4f2p-3,0x1.3ed29cc3090d1p-4,
     -0x1.1db5775367695p-3,-0x1.6491e54c130dap-10}, /* i=14 64.151 */
    {0x1.e6a148e96ec4dp-3,0x1.860ad4159da26p-57,0x1.e3a1f429bd423p-1,-0x1.5e7743342406ep-55,
     -0x1.ba8f90ce18a18p-3,-0x1.da1fa63927e48p-3,0x1.73b7b53c7e3c2p-3,0x1.094e467695623p-4,
     -0x1.1ca2e645cb80fp-3,0x1.618b376e792a8p-7}, /* i=15 64.334 */
    {0x1.025fa510665b6p-2,-0x1.675a8ef337f35p-56,0x1.e016f9196b776p-1,0x1.205573d9a13f8p-55,
     -0x1.d03c6cb8472c3p-3,-0x1.c296ce0dbfe5bp-3,0x1.7d0adb41cc5dep-3,0x1.a87ad50b8da88p-5,
     -0x1.18ee465a8217ep-3,0x1.6a8e00f5842fp-6}, /* i=16 64.533 */
    {0x1.1151a362431cap-2,-0x1.4df150a9ed6ecp-56,0x1.dc61c0ef1f116p-1,-0x1.fbd1fd5c1e556p-55,
     -0x1.e4cba1681d227p-3,-0x1.aa89431d9270dp-3,0x1.844f9995dc4e6p-3,0x1.4033ab3b632b9p-5,
     -0x1.12c9e261d1c67p-3,0x1.0a78fc7f68ba7p-5}, /* i=17 64.752 */
    {0x1.2025567e47c96p-2,-0x1.187a733e2fd4ap-57,0x1.d8848caeb6c2ap-1,0x1.20915bb96c5dcp-55,
     -0x1.f837ba96c2702p-3,-0x1.921795d35217dp-3,0x1.89916c80d4eebp-3,0x1.b54d022444b7bp-6,
     -0x1.0a6ea1928dc1cp-3,0x1.57245ecb178c3p-5}, /* i=18 64.994 */
    {0x1.2ed987a823cfep-2,0x1.b8d2b39cce27ap-57,0x1.d481a6c0fd782p-1,0x1.2fb4656cb84c5p-55,
     -0x1.053e62f5c1dd9p-2,-0x1.79617d0ee4253p-3,0x1.8cdff6758c97cp-3,0x1.e271cb5a00f62p-7,
     -0x1.001a6bd96adcap-3,0x1.9aafbde2911bfp-5}, /* i=19 65.249 */
    {0x1.3d6d129271134p-2,0x1.1362914968139p-56,0x1.d05b5ffb0304cp-1,-0x1.faf67bf651fd2p-55,
     -0x1.0dcc23a49e21fp-2,-0x1.60859757088a3p-3,0x1.8e4e8c376e12ep-3,0x1.ac6b469cdc98fp-9,
     -0x1.e81d29d13c3a6p-4,0x1.d4bac43f65c1fp-5}, /* i=20 65.563 */
    {0x1.4bdee586890e7p-2,-0x1.e50865cbcaeecp-57,0x1.cc140cbfae3a7p-1,-0x1.f5190b099884p-60,
     -0x1.15c4952b34cbp-2,-0x1.47a1337fb3d0cp-3,0x1.8df3ba9bbd359p-3,-0x1.f213587a92bcbp-8,
     -0x1.cd1c852733798p-4,0x1.028e79b3fee1ap-4}, /* i=21 65.916 */
    {0x1.5a2e0175e0f4ep-2,0x1.13a6a91a764a8p-56,0x1.c7ae0240b83aep-1,-0x1.84929ee2f31d5p-55,
     -0x1.1d27d8cdb4d8ep-2,-0x1.2ed02114257ebp-3,0x1.8be8c8c875f84p-3,-0x1.23ffdc48e4c12p-6,
     -0x1.afba2bf913442p-4,0x1.15ef7d9554d9bp-4}, /* i=22 66.347 */
    {0x1.685979f5fa6fep-2,-0x1.25dc3c56dd1fp-59,0x1.c32b93e386c7fp-1,0x1.57dc62db0dc4p-56,
     -0x1.23f6b1ca84e6p-2,-0x1.162c88a71566ep-3,0x1.884939a10b1dcp-3,-0x1.c0182366f7a2cp-6,
     -0x1.907b85c4fefcfp-4,0x1.249bf8e75e9cp-4}, /* i=23 66.846 */
    {0x1.7660752817502p-2,-0x1.dd53027530cap-59,0x1.be8f10cd9e1fep-1,-0x1.a7f1300ae90b2p-56,
     -0x1.2a327b0d161d9p-2,-0x1.fb9d9803f90dep-4,0x1.83324ed94b28bp-3,-0x1.2819be404dc45p-5,
     -0x1.6fe1e7394650bp-4,0x1.2ec312a38b33ap-4}, /* i=24 67.512 */
    {0x1.84422b8df95d7p-2,0x1.d765e81bde4d3p-56,0x1.b9dac19ba64d6p-1,-0x1.ed559ae54f8p-59,
     -0x1.2fdd1c390a5c1p-2,-0x1.cb9adc0e99a34p-4,0x1.7cc28fdb88554p-3,-0x1.69f39154faed7p-5,
     -0x1.4e68876b64cd6p-4,0x1.34a4bf4351fdp-4}, /* i=25 68.56 */
    {0x1.91fde7cd0c662p-2,0x1.1073840f922f8p-56,0x1.b510e6454751cp-1,-0x1.785b02600928p-62,
     -0x1.34f8fe3b081f3p-2,-0x1.9c7a03f5e2ed9p-4,0x1.75195662609b2p-3,-0x1.a57a8f2790cf3p-5,
     -0x1.2c82d97c3a38bp-4,0x1.368f01ca8cfddp-4}, /* i=26 71.364 */
    {0x1.9f93066168002p-2,-0x1.c82391f85371ep-56,0x1.b033b42f6e2c9p-1,-0x1.ea710fd840c44p-57,
     -0x1.3988ff8a7633ep-2,-0x1.6e60439212c9fp-4,0x1.6c566252f4071p-3,-0x1.daa48d15a6686p-5,
     -0x1.0a9b468b4c4b3p-4,0x1.34d7ee0aa3766p-4}, /* i=27 68.912 */
    {0x1.ad00f5422058bp-2,0x1.fc52e6394419cp-56,0x1.ab45546de51cfp-1,-0x1.b70e27dffe78p-58,
     -0x1.3d906844a38f8p-2,-0x1.416ed5993cbedp-4,0x1.629976103382ep-3,-0x1.04bd3325f188ep-4,
     -0x1.d2244b5868804p-5,0x1.2fe05131f0c4p-4}, /* i=28 68.001 */
    {0x1.ba473378624a5p-2,0x1.51a3a1ca24953p-56,0x1.a647e2348d9a3p-1,-0x1.4f623cfe87868p-55,
     -0x1.4112de46b4ca4p-2,-0x1.15c3010e3d605p-4,0x1.5801fc2f29caep-3,-0x1.190b04c2a6b8ap-4,
     -0x1.907a583abf58cp-5,0x1.280b0dde3049ep-4}, /* i=29 67.537 */
    {0x1.c76550aad71f9p-2,-0x1.74acc61ee02c1p-56,0x1.a13d6977fc07p-1,-0x1.6c52248551401p-55,
     -0x1.441459682eef9p-2,-0x1.d6ec52b58dec2p-5,0x1.4caeb7248540ap-3,-0x1.2a50380bfa7dep-4,
     -0x1.50ce1c6b4bec7p-5,0x1.1dbd61de8c0bap-4}, /* i=30 67.248 */
    {0x1.d45aec9ec862bp-2,0x1.895ce7f329bb8p-57,0x1.9c27e5bcb52c7p-1,-0x1.92fab46b4bc48p-55,
     -0x1.469917f43c00bp-2,-0x1.853bcf9f19d52p-5,0x1.40bd7b3cdb116p-3,-0x1.38a7c5ad9949ap-4,
     -0x1.139ed999abe43p-5,0x1.115c0be05198fp-4}, /* i=31 67.125 */
    {0x1.e127b6b0744bp-2,-0x1.2acd0fdf7975p-58,0x1.97094113dcc5ap-1,-0x1.42a9795ad7bf2p-55,
     -0x1.48a5937beb903p-2,-0x1.36985a7f2a7e7p-5,0x1.344af2efd8fe9p-3,-0x1.44321a6832a33p-4,
     -0x1.b2ac3a28b25a8p-6,0x1.034919aa579fdp-4}, /* i=32 66.992 */
    {0x1.edcb6d43f8435p-2,-0x1.fc59394a45038p-58,0x1.91e35343c31e5p-1,-0x1.fd869c730b292p-55,
     -0x1.4a3e7617d19c1p-2,-0x1.d63da9b55cdfbp-6,0x1.27726d650bc45p-3,-0x1.4d1415c94be5cp-4,
     -0x1.44909d78793fcp-6,0x1.e7c528f25edadp-5}, /* i=33 66.936 */
    {0x1.fa45dd3029259p-2,-0x1.ca45f1fa0d73dp-56,0x1.8cb7e11a6de8p-1,0x1.0f9692effe0dcp-57,
     -0x1.4b68902b948abp-2,-0x1.45caf821fcd46p-6,0x1.1a4db6d27b1cp-3,-0x1.537613e0921abp-4,
     -0x1.bad8045dcc6dcp-7,0x1.c7018191d5ceep-5}, /* i=34 66.898 */
    {0x1.034b709250488p-1,0x1.8fac2baf4d23cp-56,0x1.87889be7f594bp-1,-0x1.53b0c2ce0703cp-56,
     -0x1.4c28ceba4afa2p-2,-0x1.77f13846db057p-7,0x1.0cf4fa3350aa7p-3,-0x1.57830580a9d19p-4,
     -0x1.f6688bd6287d1p-8,0x1.a4eae2b3ff364p-5}, /* i=35 66.895 */
    {0x1.095f30861a59p-1,-0x1.12092ac4be3c2p-56,0x1.8257211e5c14dp-1,-0x1.e04ad62f66a7cp-56,
     -0x1.4c84325709c22p-2,-0x1.c6d748a0dc543p-9,0x1.fefd5770e2195p-4,-0x1.596799b15125ep-4,
     -0x1.2aa4bf9d8facap-9,0x1.82163e6e4ba59p-5}, /* i=36 66.885 */
    {0x1.0f5e28b67e295p-1,0x1.551bae3a62cp-65,0x1.7d24fa145af59p-1,-0x1.3cc5eac00191cp-55,
     -0x1.4c7fc6b9bdb53p-2,0x1.0e125c729c9ccp-8,0x1.e3fef677f1b61p-4,-0x1.59517a3df2d2fp-4,
     0x1.5a509ce09c43cp-9,0x1.5f093d570a5d7p-5}, /* i=37 66.914 */
    {0x1.154859637646ap-1,-0x1.4b9f306460338p-55,0x1.77f39be88c85ep-1,-0x1.aaf11fb18ce3p-57,
     -0x1.4c209afd6bf07p-2,0x1.724c7fc557881p-7,0x1.c9149be5b80f4p-4,-0x1.576e9c8b006c6p-4,
     0x1.d10ab85893e9fp-8,0x1.3c397326ee689p-5}, /* i=38 67.0 */
    {0x1.1b1dc87904285p-1,-0x1.21c74a82081f8p-57,0x1.72c4678244c5ap-1,0x1.88680c34729b8p-55,
     -0x1.4b6bba8a3ca51p-2,0x1.28136c8a3dec7p-6,0x1.ae607d1bed1bfp-4,-0x1.53eca7421fcc8p-4,
     0x1.6b5bcf0c5a74dp-7,0x1.1a0c6c335db5fp-5}, /* i=39 67.064 */
    {0x1.20de813e823b2p-1,-0x1.79150b1e4ba34p-55,0x1.6d98a9ad6a3fdp-1,-0x1.086a79e3d623cp-55,
     -0x1.4a6626ab4a7bep-2,0x1.905d942e6e222p-6,0x1.940145f3bf389p-4,-0x1.4ef86cd4bd336p-4,
     0x1.df72235053ff2p-7,0x1.f1b09ca16a18fp-6}, /* i=40 67.101 */
    {0x1.268a940696da6p-1,0x1.d1712f5892eap-58,0x1.68719b4ea3592p-1,-0x1.eda97644d9e4p-56,
     -0x1.4914d0ce1509cp-2,0x1.f21d69cbf84c5p-6,0x1.7a122ddea78bbp-4,-0x1.48bd7a5e32406p-4,
     0x1.22a2e0cff193bp-6,0x1.b1c922511e4b9p-6}, /* i=41 67.218 */
    {0x1.2c2215e024466p-1,-0x1.4b0c8f62b19p-59,0x1.635061ad38dap-1,-0x1.80680c9cf6f14p-57,
     -0x1.477c9567a666fp-2,0x1.26b901535afacp-5,0x1.60ab15199ab1dp-4,-0x1.4165ba0e867bp-4,
     0x1.4eb7a8220d45ep-6,0x1.74d4381fbabb3p-6}, /* i=42 67.326 */
    {0x1.31a52048874bep-1,0x1.40d189f2ee18fp-55,0x1.5e360ed021586p-1,0x1.69f2a21c9feb7p-55,
     -0x1.45a23779f86ep-2,0x1.5140026af96e8p-5,0x1.47e0a87340407p-4,-0x1.39192806876a6p-4,
     0x1.744f21ca1f0edp-6,0x1.3b2a773dcf1c3p-6}, /* i=43 67.434 */
    {0x1.3713d0df6c504p-1,-0x1.4f4269e27352p-58,0x1.5923a1ebc184fp-1,0x1.29b1cfdb4f9a3p-55,
     -0x1.438a5cb2e9ce4p-2,0x1.78b86fb7fa92ap-5,0x1.2fc48a4610bc3p-4,-0x1.2ffd9844859a4p-4,
     0x1.93c9299bf0eefp-6,0x1.050c38bb39dc4p-6}, /* i=44 67.52 */
    {0x1.3c6e491c78dc5p-1,-0x1.e13ed2a5d45d7p-55,0x1.541a07de0a269p-1,0x1.b8bebc3ed16dfp-55,
     -0x1.41398a1d19515p-2,0x1.9d3906bbd6c7ep-5,0x1.18657f5b9a323p-4,-0x1.26368c2f18148p-4,
     0x1.ad8bb799ea9adp-6,0x1.a549e420d726ep-7}, /* i=45 67.67 */
    {0x1.41b4ae06fea41p-1,0x1.3d76dfc23f7ccp-57,0x1.4f1a1bb6bcc2cp-1,0x1.c6b642e1e7fb8p-58,
     -0x1.3eb4215a4137ap-2,0x1.beda3230c472bp-5,0x1.01cf9e938129bp-4,-0x1.1be5162614e5p-4,
     0x1.c20034712fc2ep-6,0x1.48195f05a1767p-7}, /* i=46 67.845 */
    {0x1.46e727efe4716p-1,-0x1.39a66128d8d94p-57,0x1.4a24a749c2a47p-1,-0x1.fa101393d28bdp-55,
     -0x1.3bfe5e5e27a17p-2,0x1.ddb5b75f1883fp-5,0x1.d8190485a4ae4p-5,-0x1.1127c985c22e9p-4,
     0x1.d191d19f76cd6p-6,0x1.e52c665933c2p-8}, /* i=47 67.967 */
    {0x1.4c05e22de94e5p-1,-0x1.c0a7876d92182p-55,0x1.453a63c9a656p-1,0x1.7a2109be32f3ep-56,
     -0x1.391c55a0e5b2ep-2,0x1.f9e669adee0aap-5,0x1.ae46f4c987cf1p-5,-0x1.061ab5816a7d9p-4,
     0x1.dcabb523d989dp-6,0x1.496609b765f54p-8}, /* i=48 68.158 */
    {0x1.51110adc5ed81p-1,0x1.23e544f123c74p-56,0x1.405bfa6864f9p-1,-0x1.42079a05f01c2p-56,
     -0x1.3611f2bf1f7f5p-2,0x1.09c3f23e21ce6p-4,0x1.86337da4370fp-5,-0x1.f5aed2999d562p-5,
     0x1.e3b7d41231f7dp-6,0x1.792d968639b3ep-9}, /* i=49 68.32 */
    {0x1.5608d29c70c34p-1,0x1.993d4d48d4c64p-55,0x1.3b8a04fcf28c3p-1,-0x1.6cd902188a2bp-59,
     -0x1.32e2f77ec4f07p-2,0x1.155b259c3bdcbp-4,0x1.5fe541ab2f4bep-5,-0x1.deea00310582dp-5,
     0x1.e71d7fcc13a9ep-6,0x1.f26ee3bf73aa4p-11}, /* i=50 68.482 */
    {0x1.5aed6c5909517p-1,0x1.7316328459e0cp-55,0x1.36c50eabf19f5p-1,0x1.0cc4c93703dep-56,
     -0x1.2f92fb2f12233p-2,0x1.1fc707cc3a4f1p-4,0x1.3b5f771b1f717p-5,-0x1.c81067056654dp-5,
     0x1.e74056742f532p-6,-0x1.903173f4bde0fp-11}, /* i=51 68.704 */
    {0x1.5fbf0d0d5cc4ap-1,-0x1.b4b798f4fbecp-58,0x1.320d949243ad8p-1,-0x1.ce85c7f0fa608p-55,
     -0x1.2c256a5abec3bp-2,0x1.2915dee25a8dcp-4,0x1.18a2454c6953ep-5,-0x1.b146e66e27042p-5,
     0x1.e47fdb0d64c7fp-6,-0x1.2a1b98c2588d3p-9}, /* i=52 68.86 */
    {0x1.647deb8e20b9p-1,-0x1.ec8dab63b69ep-58,0x1.2d6406703b033p-1,0x1.708a19f93d965p-55,
     -0x1.289d86c39acffp-2,0x1.3155e71da961p-4,0x1.ef563c48c8564p-6,-0x1.9aae01264d404p-5,
     0x1.df36b4f1dc171p-6,-0x1.d706f26a461aep-9}, /* i=53 69.101 */
    {0x1.692a40556fb6ap-1,0x1.d94deb733f4d1p-55,0x1.28c8c75459603p-1,0x1.b1a4041ee5c3dp-55,
     -0x1.24fe679f37471p-2,0x1.38953e931c5d6p-4,0x1.b0ea25ecadcccp-6,-0x1.84621a8d1bd9dp-5,
     0x1.d7b9f5da3a6a7p-6,-0x1.363048a9835a4p-8}, /* i=54 69.272 */
    {0x1.6dc44551553afp-1,-0x1.bf77d69309b9p-58,0x1.243c2e44a7335p-1,-0x1.67984277b50a8p-57,
     -0x1.214afa0ca68dbp-2,0x1.3ee1d36c0ca2cp-4,0x1.75f248d7f0eb6p-6,-0x1.6e7bb80f6dcf9p-5,
     0x1.ce59773e3cab1p-6,-0x1.75e26f9f73c56p-8}, /* i=55 69.465 */
    {0x1.724c35b4fae7bp-1,0x1.94999582417c8p-58,0x1.1fbe86e5ce35dp-1,0x1.1004ba833cb2dp-55,
     -0x1.1d8601bbd70fbp-2,0x1.4449548f48afp-4,0x1.3e5d1a74014bap-6,-0x1.590fc513ec22ep-5,
     0x1.c35f24540e3d9p-6,-0x1.ab6d2714ad386p-8}, /* i=56 69.685 */
    {0x1.76c24dcc6c6cp-1,0x1.19537fcda06bdp-55,0x1.1b50121f3f28cp-1,-0x1.5870c87915b8p-59,
     -0x1.19b219bf87a48p-2,0x1.48d9248a59eb2p-4,0x1.0a160eeaeecdbp-6,-0x1.442fd816a4747p-5,
     0x1.b70f3c89b3e69p-6,-0x1.d7a2d1bed21d1p-8}, /* i=57 70.196 */
    {0x1.7b26cad2e50fep-1,-0x1.ce7feba9dfb12p-55,0x1.16f106bbc577ap-1,-0x1.d1017b25ea348p-56,
     -0x1.15d1b58355b63p-2,0x1.4c9e4e8fdd57ep-4,0x1.b20c320c71d8bp-7,-0x1.2fea77c1c2d16p-5,
     0x1.a9a815ca7ddc1p-6,-0x1.fb541bbd413c8p-8}, /* i=58 70.555 */
    {0x1.7f79eacb97898p-1,0x1.fd5d89e91d0ddp-55,0x1.12a1920604825p-1,-0x1.ccfd3cccb4938p-58,
     -0x1.11e721dfe6ba7p-2,0x1.4fa57d5fcbb8dp-4,0x1.56283e9b7e061p-7,-0x1.1c4b5f1374fd3p-5,
     0x1.9b6293a72088cp-6,-0x1.0ba5a5b74340ep-7}, /* i=59 70.763 */
    {0x1.83bbec5cdee22p-1,0x1.3109d8a8e65ap-57,0x1.0e61d86071468p-1,-0x1.0ce73a0629f3ap-56,
     -0x1.0df48647af38dp-2,0x1.51faf3ef252bdp-4,0x1.004acd4a116d1p-7,-0x1.095bbfc1aab01p-5,
     0x1.8c72288c10abfp-6,-0x1.1625a26153ddbp-7}, /* i=60 71.122 */
    {0x1.87ed0eadc5a2ap-1,0x1.0af644a40cdf6p-56,0x1.0a31f5d8701b3p-1,-0x1.69be1c6d2f945p-55,
     -0x1.09fbe60757b84p-2,0x1.53aa87a589b39p-4,0x1.6077cad635afep-8,-0x1.ee45047defbd8p-6,
     0x1.7d0507433a359p-6,-0x1.1d87365b117c3p-7}, /* i=61 71.921 */
    {0x1.8c0d9145cf49dp-1,0x1.bea3f44a38f5p-55,0x1.0611feb45139ap-1,0x1.e4854f5cf6588p-56,
     -0x1.05ff21953a316p-2,0x1.54bf9c08c1d9dp-4,0x1.9706e47ec6983p-9,-0x1.cb4905caf6008p-6,
     0x1.6d445cd02559ap-6,-0x1.2222e5ddeb73p-7}, /* i=62 72.227 */
    {0x1.901db3eeef187p-1,0x1.686643d96bf0fp-55,0x1.0201fffbf7f8p-1,0x1.0002f3990d4cp-61,
     -0x1.01fff7ebe8004p-2,0x1.55451fb001307p-4,0x1.0509ffc9bc308p-10,-0x1.a9c997dbab5a8p-6,
     0x1.5d54a31b98f7dp-6,-0x1.244be978edbbcp-7}, /* i=63 73.347 */
    {0x1.941db699968ffp-1,-0x1.de4c371fb2ddap-55,0x1.fc03fff80ffp-2,0x1.f8e5851a6dep-62,
     -0x1.fc000fd82fff8p-3,0x1.55458a5aabd48p-4,-0x1.f613ff970b10ap-11,-0x1.89c997e69f2ap-6,
     0x1.4d5602132646dp-6,-0x1.244fb6e93a605p-7}, /* i=64 73.751 */
  };

  private static final double[] Xfast = {0, 0x1.8p-6, 0x1.4p-5, 0x1.cp-5, 0x1.2p-4, 0x1.6p-4,
    0x1.ap-4, 0x1.ep-4, 0x1.1p-3, 0x1.3p-3, 0x1.5p-3, 0x1.7p-3, 0x1.9p-3, 0x1.bp-3, 0x1.dp-3,
    0x1.fp-3, 0x1.08p-2, 0x1.18p-2, 0x1.28p-2, 0x1.38p-2, 0x1.48p-2, 0x1.58p-2, 0x1.68p-2,
    0x1.78p-2, 0x1.88p-2, 0x1.98p-2, 0x1.a8p-2, 0x1.b8p-2, 0x1.c8p-2, 0x1.d8p-2, 0x1.e8p-2,
    0x1.f8p-2, 0x1.04p-1, 0x1.0cp-1, 0x1.14p-1, 0x1.1cp-1, 0x1.24p-1, 0x1.2cp-1, 0x1.34p-1,
    0x1.3cp-1, 0x1.44p-1, 0x1.4cp-1, 0x1.54p-1, 0x1.5cp-1, 0x1.64p-1, 0x1.6cp-1, 0x1.74p-1,
    0x1.7cp-1, 0x1.84p-1, 0x1.8cp-1, 0x1.94p-1, 0x1.9cp-1, 0x1.a4p-1, 0x1.acp-1, 0x1.b4p-1,
    0x1.bcp-1, 0x1.c4p-1, 0x1.ccp-1, 0x1.d4p-1, 0x1.dcp-1, 0x1.e4p-1, 0x1.ecp-1, 0x1.f4p-1,
    0x1.fcp-1, 0x1.02p+0};

  private static final double[] err_fast = {0x1.aep-65, 0x1.19p-62, 0x1.ap-63, 0x1.61p-63,
    0x1.4dp-63, 0x1.3p-63, 0x1.18p-63, 0x1.03p-63, 0x1.ffp-64, 0x1.d5p-64, 0x1.adp-64, 0x1.87p-64,
    0x1.63p-64, 0x1.4p-64, 0x1.1dp-64, 0x1.f8p-65, 0x1.bap-65, 0x1.8p-65, 0x1.4bp-65, 0x1.53p-65,
    0x1.21p-65, 0x1.e8p-66, 0x1.95p-66, 0x1.5p-66, 0x1.11p-66, 0x1.b4p-67, 0x1.5bp-67, 0x1.89p-67,
    0x1.bcp-67, 0x1.e4p-67, 0x1.02p-66, 0x1.07p-66, 0x1.0fp-66, 0x1.1p-66, 0x1.11p-66, 0x1.0dp-66,
    0x1.0bp-66, 0x1.06p-66, 0x1.f5p-67, 0x1.e5p-67, 0x1.d9p-67, 0x1.c2p-67, 0x1.adp-67, 0x1.9ap-67,
    0x1.8bp-67, 0x1.76p-67, 0x1.5fp-67, 0x1.5p-67, 0x1.3cp-67, 0x1.2dp-67, 0x1.1fp-67, 0x1.0fp-67,
    0x1.04p-67, 0x1.edp-68, 0x1.dbp-68, 0x1.c9p-68, 0x1.b7p-68, 0x1.9ap-68, 0x1.8ap-68, 0x1.8p-68,
    0x1.74p-68, 0x1.63p-68, 0x1.5dp-68, 0x1.52p-68, 0x1.53p-69};

  // use a type [29,29] rational approximation of atan(z) for 0 <= z <= 1
  // __attribute__((noinline)) is dropped, the JIT decides
  private static double atan2piAccurate(double y, double x) {
    double absy = Math.abs(y), absx = Math.abs(x);
    boolean inv = absy > absx;
    if (absy == absx)
      return (y > 0) ?
        ((x > 0) ? 0.25 : 0.75)      // 1st and 2nd quadrant
        : ((x < 0) ? -0.75 : -0.25); // 3rd and 4th quadrant
    Tint64 z = new Tint64(), p = new Tint64(), q = new Tint64();
    if (inv) // case |y| > |x|
    {
      // atan2pi_begin
      // avoid spurious underflow in div_tint_d() below
      long vx = Double.doubleToRawLongBits(x), vy = Double.doubleToRawLongBits(y);
      int ex = (int) ((vx >>> 52) & 0x7ff), ey = (int) ((vy >>> 52) & 0x7ff);
      /* For |y/x| large, atan2pi(y,x) is close to 1/2 or -1/2. More precisely,
         for z > 0x1.45f306dc9c882p+53, atanpi(z) rounds to 1/2 to nearest.
         When x < 0, since atan2pi(y,x) > 1/2 for y>0, we get 1/2 for even a
         smaller value of |y/x|.
         If y is normal, then 2^(ey-1023) <= |y| < 2^(ey-1022), and whatever
         x is normal or subnormal, |x| < 2^(ex-1022), thus
         |y/x| > 2^(ey-ex-1) thus if ey - ex > 54, |y/x| > 2^54.
         If y is subnormal, then x is subnormal too since we are in the case
         |y| > |x|, thus ey = ex, and the test below never holds. */
      if (ey - ex > 54) // |y/x| > 2^54
      {
        if (x > 0)
          return (y > 0) ? 0.5 - 0x1p-55 : -0.5 + 0x1p-55;
        else
          return (y > 0) ? 0.5 + 0x1p-54 : -0.5 - 0x1p-54;
      }
      // atan2pi_end
      divTintD(z, x, y);
    }
    else // |x| >= |y|
    {
      // atan2pi_begin
      if (x < 0) {
        // avoid spurious underflow in div_tint_d() below
        long vx = Double.doubleToRawLongBits(x), vy = Double.doubleToRawLongBits(y);
        int ex = (int) ((vx >>> 52) & 0x7ff), ey = (int) ((vy >>> 52) & 0x7ff);
        /* For |y/x| small and x < 0, atan2pi(y,x) is close to 1 or -1.
           More precisely,
           for z <= 0x1.921fb54442d18p-53, 1-atanpi(z) rounds to 1 to nearest.
           If x is normal, then 2^(ex-1023) <= |x| < 2^(ex-1022), and whatever
           y is normal or subnormal, |y| < 2^(ey-1022), thus
           |y/x| < 2^(ey-ex+1) thus if ey - ex < -54, |y/x| < 2^-54.
           If x is subnormal, then y is subnormal too since we are in the case
           |x| >= |y|, thus ex = ey, and the test below never holds. */
        if (ey - ex < -54) // |y/x| < 2^-54
          return (y > 0) ? 1.0 - 0x1p-54 : -1.0 + 0x1p-54;
      }
      // atan2pi_end
      divTintD(z, y, x);
    }
    // below when we write y/x it should be read x/y when |x/y| < 1
    // |z - y/x| < 2^-185.53 * |z| (relative error from div_tint_d)
    // the rational approximation is only for z > 0, it is not antisymmetric
    long sz = z.sgn;
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
    /* The relative error of div_tint() is <= 2^-185.53, thus the maximal
       error of newz with respect to atan(y/x) is 2^-182.63, which corresponds
       to at most 662 ulps of the 192-bit fixed point. */
    // atan2pi_begin
    // multiply by 1/pi
    mulTint(z, z, ONE_OVER_PI);
    /* z_out = atan(y/x)/pi * (1+eps9) with |eps9| < 2^-182.586, which
       corresponds to a maximal error of 2^-182.586*2^192 <= 683 ulps. */
    long err = 683; // error bound in case inv=0 and x > 0, atan2pi_specific
    z.sgn = sz; // restore sign
    /* Now z approximates atanpi(y/x) for inv=0, and atanpi(x/y) for inv=1,
       with -1/4 < z < 1/4.
    */
    if (inv)
    {
      // if x/y > 0 thus atan(x/y) > 0 we apply 1/2 - atanpi(x/y)
      // if x/y < 0 thus atan(x/y) < 0 we apply -1/2 - atanpi(x/y)
      if (z.sgn == 0) { // 0 < atanpi(x/y) < 1/4
        z.sgn = 1;
        addTint(z, ONE_HALF, z);
        /* Now 1/4 < z < 1/2, with a total error < 2^-184.569, which is less
           than 346 ulps of 1/4. */
      }
      else // -1/4 < atanpi(x/y) < 0
      {
        addTint(z, ONE_HALF, z);
        z.sgn = 1;
        /* Now -1/2 < z < -1/4, with the same bound of 346 ulps. */
      }
      err = 346;
    }
    // now -1/2 < z < 1/2
    // if x is negative we go to the opposite quadrant
    if (x < 0) {
      if (z.sgn == 0) { // 1st quadrant -> 3rd quadrant (subtract 1)
        z.sgn = 1;
        addTint(z, ONE, z);
        z.sgn = 1;
        /* We had 0 < z < 1/2 thus now -1 < z < -1/2, with a total error
           < 2^-184.535, which is less than 177 ulps of 1/2. */
      }
      else // 4th quadrant -> 2nd quadrant (add pi)
      {
        addTint(z, ONE, z);
        /* We had -1/2 < z < 0 thus now 1/2 < z < 1, with the same bound of
           177 ulps. */
      }
      err = 177;
    }
    // atan2pi_end
    return tintTod(z, err, y, x);
  }

  /* Fast division: return h + l approximating bh/ah with relative error
     < 2^-98.41 (copied and simplified from tan.c).
     Ensure |l| < 2^-48.999 for 1 <= |ah|, |bh| < 2. */
  private static void fast_div(double[] h, double[] l, double bh, double ah) {
    /* We use here Karp-Markstein's trick for division. */
    double y = 1.0 / ah;
    h[0] = bh * y;
    double eh = Math.fma(ah, -h[0], bh);
    l[0] = y * eh;
  }

  private static void fast_two_sum(double[] hi, double[] lo, double a, double b) {
    double e;

    hi[0] = a + b;
    e = hi[0] - a; /* exact */
    lo[0] = b - e; /* exact */
  }

  // same as dd_sum() from atan2.c, for al=0
  // assumes |ah| >= |bh|
  private static void dd_sum1(double[] h, double[] l, double ah, double bh, double bl) {
    fast_two_sum(h, l, ah, bh);
    l[0] += bl;
  }

  // Multiply exactly a and b, such that *hi + *lo = a * b.
  private static void a_mul(double[] hi, double[] lo, double a, double b) {
    hi[0] = a * b;
    lo[0] = Math.fma(a, b, -hi[0]);
  }

  // Multiply a double with a double double : a * (bh + bl)
  private static void s_mul(double[] hi, double[] lo, double a, double bh,
                            double bl) {
    a_mul(hi, lo, a, bh); /* exact */
    lo[0] = Math.fma(a, bl, lo[0]);
  }

  // Returns (ah + al) * (bh + bl) - (al * bl)
  // We can ignore al * bl when assuming al <= ulp(ah) and bl <= ulp(bh)
  private static void d_mul(double[] hi, double[] lo, double ah, double al,
                            double bh, double bl) {
    double[] s = new double[1], t = new double[1];

    a_mul(hi, s, ah, bh);
    t[0] = Math.fma(al, bh, s[0]);
    lo[0] = Math.fma(ah, bl, t[0]);
  }

  /* Fast path: return err such that |h + l - atan2pi(y,x)| < err*h.
     Assumes 2^-969 < |x|, |y| <= 2^1022 and 2^-969 <= |y/x| <= 2^969
     (conditions needed for fast_div). */
  private static double atan2piFast(double[] h, double[] l, double y, double x) {
    long vy = Double.doubleToRawLongBits(y), vx = Double.doubleToRawLongBits(x);
    long ay = vy << 1, ax = vx << 1;
    boolean inv = Long.compareUnsigned(ay, ax) > 0;
    long negx = vx >>> 63, negz = (vy ^ vx) >>> 63;
    if (inv) { double t = y; y = x; x = t; }
    // now |y| <= |x|
    double[] zh = new double[1], zl = new double[1];
    if (ax == ay) { // avoid spurious inexact
      if (y == x)
        h[0] = (x > 0) ? 0.25 : -0.75;
      else
        h[0] = (x > 0) ? -0.25 : 0.75;
      l[0] = 0;
      return 0; // exact
    }
    fast_div(zh, zl, y, x);
    // for |zh| < 2^e, we have |zl| < 2^(e-48.999)
    /* since |y| <= |x| and fast_div ensures |zl| < 2^-47.999 with relative
       error < 2^-98.41, we have |zh+zl| < 1 + 2^-98.41, thus
       |zh| < 1 + 2^-98.41 - 2^-47.999 < 1 + 1/64, thus i <= 64 below */

    // zh + zl = y/x * (1 + eps1) with |eps1| < 2^-98.41
    // the rational approximation is only for z > 0, it is not antisymmetric
    double sz = 1.0;
    if (zh[0] < 0) { zh[0] = -zh[0]; zl[0] = -zl[0]; sz = -1.0; }

    // now approximate atan(zh+zl) for 0 <= zh+zl < 1
    int i = (int) (64.0 * zh[0]); // 0 <= i <= 64
    double[] p = Pfast[i];
    zh[0] -= Xfast[i];
    double P9 = 0x1.c6e5d41706f0dp-4; // degree-9 coefficient for i=0
    double z = zh[0] + zl[0];
    double[] t = new double[1];
    h[0] = (i == 0) ? Math.fma(P9, z * z, p[9]) : p[9];
    for (int j = 6; j >= 2; j--)
      h[0] = Math.fma(h[0], z, p[2+j]); // degree j
    // degree 1: h + l <- h * (zh + zl) + p[2] + p[3]
    s_mul(h, l, h[0], zh[0], zl[0]);
    fast_two_sum(h, t, p[2], h[0]);
    l[0] += t[0] + p[3];
    // degree 0: h + l <- (h + l) * (zh + zl) + p[0] + p[1]
    d_mul(h, l, h[0], l[0], zh[0], zl[0]);
    // for i=0 we have p[0]=0, but fast_two_sum works in that case
    fast_two_sum(h, t, p[0], h[0]);
    l[0] += t[0] + p[1];

    // atan2pi_begin
    // divide by pi
    d_mul(h, l, h[0], l[0], ONE_OVER_PI_H, ONE_OVER_PI_L);
    /* We had h_in + l_in = atan(y/x) * (1 + eps) with |eps| < eps[i],
       ONE_OVER_PI_H+ONE_OVER_PI_L = 1/pi * (1 + eps2) with |eps2| < 2^-107.871,
       and the d_mul() relative error is bounded by 2^-98.616, thus
       h+l = atan(y/x)/pi * (1+eps'[i]) with
       eps'[i] < (1+eps[i])*(1+2^-107.871)*(1+2^-98.616)-1. */
    // atan2pi_end

    // restore sign
    h[0] *= sz;
    l[0] *= sz;

    // atan2pi_begin
    // now -1/4 < h+l < 1/4
    if (inv)
    {
      if (negz == 0)
        /* if y/x > 0 thus atanpi(y/x) > 0 we apply 1/2 - atanpi(y/x)
           and the result will be in (1/4, 1/2); the relative error stays
           bounded by eps'[i] + 2^-99.912 */
        dd_sum1(h, l, 0.5, -h[0], -l[0]);
      else
        /* if y/x < 0 thus atanpi(y/x) < 0 we apply -1/2 - atanpi(y/x)
           and the result will be in (-1/2,-1/4) with the same bound */
        dd_sum1(h, l, -0.5, -h[0], -l[0]);
    }
    // now -1/2 <= h+l <= 1/2 with error bounded by eps'[i] + 2^-99.912
    if (negx != 0)
    {
      if (negz == 0)
        /* 1st quadrant -> 3rd quadrant (subtract 1), and the result
           will be in (-1,-1/2), with relative error eps'[i] + 2^-98.912 */
        dd_sum1(h, l, -1.0, h[0], l[0]);
      else
        /* 4th quadrant -> 2nd quadrant (add 1), and the result will be
           in (1/2,1) with the same bound */
        dd_sum1(h, l, 1.0, h[0], l[0]);
    }
    // atan2pi_end
    return err_fast[i];
  }

  // atan(y/x)
  public static double compute(double y, double x) {
    long uy = Double.doubleToRawLongBits(y), ux = Double.doubleToRawLongBits(x);
    long ay = uy & MASK, ax = ux & MASK;
    int ey = (int) (ay >>> 52), ex = (int) (ax >>> 52);

    if (ey == 0x7ff || ex == 0x7ff) // NaN or Inf
    {
      if (isNan(ay) || isNan(ax))
        return y + x; // if y or x is sNaN, returns qNaN and raises invalid
      // Now neither y nor x is NaN, but at least one is +Inf or -Inf
      if (isInf(ay) && isInf(ax)) // both y and x are +/-Inf
      {
        // atan2pi (+/-Inf,-Inf) = +/-3/4
        if (x < 0)
          return (y > 0) ? 0.75 : -0.75; // atan2pi_specific
        // atan2pi (+/-Inf,+Inf) = +/-1/4
        return (y > 0) ? 0.25 : -0.25;   // atan2pi_specific
      }
      // now only one of y and x is +/-Inf
      if (isInf(ax))
      {
        if (x < 0)
          return (uy >>> 63) != 0 ? -1.0 : 1.0; // atan2pi_specific
        // atan2pi(+/-0,x) = +/-0 for x > 0
        // atan2pi(+/-y,+Inf) = +/-0 for finite y>0
        return Math.copySign(0.0, y);
      }
      // now y = +/-Inf
      // atan2pi(+/-Inf,x) = +/-1/2 for finite x
      return (y > 0) ? 0.5 : -0.5; // atan2pi_specific
    }

    if (ay == 0 || ax == 0)
    {
      if (ay == 0 && ax == 0)
      {
        if (ux == 0) // atan2pi(+/-0, +0) = +/-0
          return y;
        // atan2pi(+/-0, +0) = +/-1
        return (uy == 0) ? 1 : -1; // atan2pi_specific
      }
      // only one of y and x is zero
      if (ay == 0)
      {
        // atan2pi(+/-0,x) = +/-0 for x>0
        if (x > 0) return y;
        // atan2pi(+/-0,x) = +/-1 for x<0
        return (uy == 0) ? 1.0 : -1.0;
      }
      // now only x is zero
      // atan2pi(y,+/-0) = -1/2 for y<0
      // atan2pi(y,+/-0) = +1/2 for y>0
      return (y > 0) ? 0.5 : -0.5; // atan2pi_specific
    }

    // now both y and x are neither NaN, nor +/-Inf, nor +/-0

    /* atan2pi_fast requires 2^-969 <= |x|, |y| <= 2^1022
       and 2^-969 <= |y/x| < 2^969 */
    // 2^-969 <= |x| <= 2^1022 translates into 54 <= ex <= 2044
    /* |ey - ex| <= 341 avoids spurious underflow in the fast path,
       since we multiply a term of order z by another of order z^2,
       where z = min(|y/x|,|x/y|) */
    if (54 <= ex && ex <= 2044 && 54 <= ey && ey <= 2044 &&
                        -341 <= ey - ex && ey - ex <= 341)
    {
      double[] h = new double[1], l = new double[1];
      double err;
      err = atan2piFast(h, l, y, x);
      double left =  h[0] + Math.fma(h[0], -err, l[0]);
      double right = h[0] + Math.fma(h[0], +err, l[0]);
      if (left == right)
        return left;
    }

    return atan2piAccurate(y, x);
  }
}
