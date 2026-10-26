/*
 * Correctly rounded power function of two binary64 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/pow/pow.c, src/binary64/pow/pow.h,
 *             src/binary64/pow/dint.h, src/binary64/pow/qint.h
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_pow -> Pow.compute
 *
 * Copyright (c) 2022-2025 CERN and Inria
 * Authors: Tom Hubrecht and Paul Zimmermann
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

import io.github.ntysdd.coremath.internal.Dint64;
import io.github.ntysdd.coremath.internal.Qint64;
import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded power function of two binary64 values, the translation of
 * the CORE-MATH {@code cr_pow}.
 *
 * <p>{@code pow.c} includes {@code pow.h}, which includes {@code dint.h} and
 * {@code qint.h}. AGENTS.md makes one C file one Java class, and the header
 * parts a translation unit uses are carried by that unit, so the helpers and
 * tables of the three headers that {@code pow.c} reaches are translated into
 * this class. {@code pow.c} compiles with the default {@code POW_ITERATION} of
 * 15, that is with all four phases enabled: {@code ENABLE_FP},
 * {@code ENABLE_ZIV2}, {@code ENABLE_EXACT} and {@code ENABLE_ZIV3}. The
 * {@code #if} alternatives are dropped and the four blocks are translated
 * inline.
 *
 * <p>The class is laid out in the order of the C sources: the {@code dint}
 * helpers and constants of {@code dint.h}, then the {@code qint} helpers and
 * constants of {@code qint.h}, then the double-double helpers and the
 * conversions of {@code pow.h}, then the polynomials and the log and exp
 * approximations of {@code pow.c}, then {@code exact_pow}, {@code is_exact} and
 * {@code cr_pow}. Within each group the order of the C text is kept.
 *
 * <p>The deviations from the C files are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code dint64_t} becomes {@link Dint64}, {@code qint64_t} becomes
 *       {@link Qint64} and {@code unsigned __int128} becomes {@link U128}; the
 *       little-endian member of the byte-order {@code #if} is the one the
 *       reference build compiles. {@code cmp} and {@code cmp_dint_abs} compare
 *       signed exponents, {@code cmpu} and {@code cmpu128} compare unsigned
 *       significands and go through {@link Long#compareUnsigned} and
 *       {@link U128#compareUnsigned}.
 *   <li>{@code u128 *} output parameters become one-element {@link U128}
 *       arrays, {@code double *} and {@code int64_t *} output parameters become
 *       one-element arrays of the corresponding type.
 *   <li>{@code FLAG_T}, {@code get_flag}, {@code set_flag} and the {@code flag}
 *       local of {@code cr_pow} are dropped. They save and restore the inexact
 *       flag around the phases, which the JVM does not expose. For the same
 *       reason every {@code CORE_MATH_SUPPORT_ERRNO} block is dropped, and so is
 *       the {@code fegetround()} switch in {@code dint_tod_subnormal},
 *       {@code dint_tod}, {@code subnormalize_qint} and {@code cr_pow}: only the
 *       {@code FE_TONEAREST} case is kept, with a comment naming the modes that
 *       were dropped.
 *   <li>{@code goto end} in {@code dint_tod_subnormal} becomes an {@code else},
 *       Java having no {@code goto}; the two paths only meet at the tail of the
 *       function.
 *   <li>{@code roundeven_finite} becomes {@link Math#rint}, the
 *       {@code __builtin_roundeven} of the reference build. The
 *       {@code __builtin_round (sqrt (dm))} of {@code is_exact} becomes
 *       {@link Math#round}, whose argument is a positive integer-valued double
 *       below 2^53 there.
 *   <li>{@code printf} and {@code exit (1)} in the unreachable tail of
 *       {@code cr_pow} become a throw, as the mapping table asks for
 *       {@code exit} in a verification path.
 *   <li>{@code static const} tables declared inside the functions become
 *       fields, with the C names. {@code mul_dint_2} and {@code mul_qint_33} are
 *       not translated: no path of the reference build calls them.
 * </ul>
 */
public final strictfp class Pow {
  private Pow() {
  }

  // Add two 128-bit integers and return 1 if a carry occurred
  private static int addu128(U128 a, U128 b, U128[] r) {
    r[0] = a.add(b);
    // Return the carry
    return r[0].compareUnsigned(a) < 0 ? 1 : 0;
  }

  // Subtract two 128-bit integers and return 1 if a borrow occurred
  private static int subu128(U128 a, U128 b, U128[] r) {
    r[0] = a.sub(b);
    // Return the borrow
    return r[0].compareUnsigned(a) > 0 ? 1 : 0;
  }

  private static int cmp(long a, long b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }

  private static int cmpu(long a, long b) { return Long.compareUnsigned(a, b); }

  private static int cmpu128(U128 a, U128 b) { return a.compareUnsigned(b); }

  // Copy a dint64_t value
  private static void cpDint(Dint64 r, Dint64 a) {
    r.ex = a.ex;
    r.r(a.r());
    r.sgn = a.sgn;
  }

  // Return non-zero if a = 0
  private static int dintZeroP(Dint64 a) {
    return a.hi == 0 ? 1 : 0;
  }

  // Compare the absolute values of a and b
  // Return -1 if |a| < |b|
  // Return  0 if |a| = |b|
  // Return +1 if |a| > |b|
  private static int cmpDintAbs(Dint64 a, Dint64 b) {
    if (dintZeroP(a) != 0)
      return dintZeroP(b) != 0 ? 0 : -1;
    if (dintZeroP(b) != 0)
      return +1;
    int c1 = cmp(a.ex, b.ex);
    return c1 != 0 ? c1 : cmpu128(a.r(), b.r());
  }

  private static int cmpDint11(Dint64 a, Dint64 b) {
    int c1 = cmp(a.ex, b.ex);
    return c1 != 0 ? c1 : cmpu(a.hi, b.hi);
  }

  // Add two dint64_t values, with error bounded by 2 ulps (ulp_128)
  // (more precisely 1 ulp when a and b have same sign, 2 ulps otherwise)
  // Moreover, when Sterbenz theorem applies, i.e., |b| <= |a| <= 2|b|
  // and a,b are of different signs, there is no error, i.e., r = a-b.
  private static void addDint(Dint64 r, Dint64 a, Dint64 b) {
    if ((a.hi | a.lo) == 0) {
      cpDint(r, b);
      return;
    }

    switch (cmpDintAbs(a, b)) {
    case 0:
      if ((a.sgn ^ b.sgn) != 0) {
        cpDint(r, ZERO);
        return;
      }

      cpDint(r, a);
      r.ex++;
      return;

    case -1: // |A| < |B|
      {
        // swap operands
        Dint64 tmp = a;
        a = b;
        b = tmp;
        break; // fall through the case |A| > |B|
      }
    }

    // From now on, |A| > |B| thus a->ex >= b->ex

    U128 A = a.r(), B = b.r();
    long k = a.ex - b.ex;

    if (k > 0) {
      /* Warning: the right shift x >> k is only defined for 0 <= k < n
         where n is the bit-width of x. See for example
         https://developer.arm.com/documentation/den0024/a/The-A64-instruction-set/Data-processing-instructions/Shift-operations
         where it is said that k is interpreted modulo n. */
      B = Long.compareUnsigned(k, 128) < 0 ? B.shr((int) k) : U128.of(0, 0);
    }

    U128 C;
    long sgn = a.sgn;

    r.ex = a.ex; /* tentative exponent for the result */

    if ((a.sgn ^ b.sgn) != 0) {
      /* a and b have different signs C = A + (-B)
         Sterbenz case |a|/2 <= |b| <= |a| can occur only when:
         * k=0: then B is not truncated, and C is exact below
         * k=1 and ex>0 below: then we ensure C is exact
       */
      C = A.sub(B);
      long ch = C.hi;
      /* We can't have C=0 here since we excluded the case |A| = |B|,
         thus __builtin_clzll(C) is well-defined below. */
      long ex = ch != 0 ? Long.numberOfLeadingZeros(ch)
                        : 64 + Long.numberOfLeadingZeros(C.lo);
      /* The error from the truncated part of B (1 ulp) is multiplied by 2^ex,
         thus by 2 ulps when ex <= 1. */
      if (ex > 0) {
        if (k == 1) /* Sterbenz case */
          C = A.shl((int) ex).sub(b.r().shl((int) (ex - 1)));
        else
          C = A.shl((int) ex).sub(B.shl((int) ex));
        /* If C0 is the previous value of C, we have:
           (C0-1)*2^ex < A*2^ex-B*2^ex <= C0*2^ex
           since some neglected bits from B might appear which contribute
           a value less than ulp(C0)=1.
           As a consequence since 2^(127-ex) <= C0 < 2^(128-ex), because C0 had
           ex leading zero bits, we have 2^127-2^ex <= A*2^ex-B*2^ex < 2^128.
           Thus the value of C, which is truncated to 128 bits, is the right
           one (as if no truncation); moreover in some rare cases we need to
           shift by 1 bit to the left. */
        r.ex -= ex;
        ex = Long.numberOfLeadingZeros(C.hi);
        /* Fall through with the code for ex = 0. */
      }
      C = C.shl((int) ex);
      r.ex -= ex;
      /* The neglected part of B is bounded by 2 ulp(C) when ex=0, 1 ulp
         when ex > 0 but ex=0 at the end, and by 2*ulp(C) when ex > 0 and there
         is an extra shift at the end (in that case necessarily ex=1). */
    } else {
      C = A.add(B);
      if (C.compareUnsigned(A) < 0) {
        C = U128.of(0, 1L << 63).or(C.shr(1));
        r.ex++;
      }
    }

    /* In the addition case, we loose the truncated part of B, which
       contributes to at most 1 ulp. If there is an exponent shift, we
       might also loose the least significant bit of C, which counts as
       1/2 ulp, but the truncated part of B is now less than 1/2 ulp too,
       thus in all cases the error is less than 1 ulp(r). */

    r.sgn = sgn;
    r.r(C);
  }

  // same as add_dint, but assumes the lower limbs and a and b are zero
  // error is bounded by 2 ulps (ulp_64)
  private static void addDint11(Dint64 r, Dint64 a, Dint64 b) {
    if (a.hi == 0) {
      cpDint(r, b);
      return;
    }

    if (b.hi == 0) {
      cpDint(r, a);
      return;
    }

    switch (cmpDint11(a, b)) {
    case 0:
      if ((a.sgn ^ b.sgn) != 0) {
        cpDint(r, ZERO);
        return;
      }

      cpDint(r, a);
      r.ex++;
      return;

    case -1: // |A| < |B|
      {
        // swap operands
        Dint64 tmp = a;
        a = b;
        b = tmp;
        break; // fall through the case |A| > |B|
      }
    }

    // From now on, |A| > |B| thus a->ex >= b->ex

    long A = a.hi, B = b.hi;

    if (a.ex > b.ex) {
      /* Warning: the right shift x >> k is only defined for 0 <= k < n
         where n is the bit-width of x. See for example
         https://developer.arm.com/documentation/den0024/a/The-A64-instruction-set/Data-processing-instructions/Shift-operations
         where it is said that k is interpreted modulo n. */
      long k = a.ex - b.ex;
      B = Long.compareUnsigned(k, 64) < 0 ? B >>> (int) k : 0;
    }

    long C;
    long sgn = a.sgn;

    r.ex = a.ex; /* tentative exponent for the result */

    if ((a.sgn ^ b.sgn) != 0) {
      // a and b have different signs C = A + (-B)
      C = A - B;
      /* we can't have C=0 here since we excluded the case |A| = |B|,
         thus __builtin_clzll(C) is well-defined below */
      long ex = Long.numberOfLeadingZeros(C);
      /* The error from the truncated part of B (1 ulp) is multiplied by 2^ex.
         Thus for ex <= 2, we get an error bounded by 4 ulps in the final result.
         For ex >= 3, we pre-shift the operands. */
      if (ex > 0) {
        C = (A << (int) ex) - (B << (int) ex);
        /* If C0 is the previous value of C, we have:
           (C0-1)*2^ex < A*2^ex-B*2^ex <= C0*2^ex
           since here some neglected bits from B might appear which contribute
           a value less than ulp(C0)=1.
           As a consequence since 2^(63-ex) <= C0 < 2^(64-ex), because C0 had
           ex leading zero bits, we have 2^63-2^ex <= A*2^ex-B*2^ex < 2^64.
           Thus the value of C, which is truncated to 64 bits, is the right
           one (as if no truncation); moreover in some rare cases we need to
           shift by 1 bit to the left. */
        r.ex -= ex;
        ex = Long.numberOfLeadingZeros(C);
        /* Fall through with the code for ex = 0. */
      }
      C = C << (int) ex;
      r.ex -= ex;
      /* The neglected part of B is bounded by ulp(C) when ex=0, or when
         ex > 0 but the ex=0 at the end, and by 2*ulp(C) when ex>0 and there
         is an extra shift at the end (in that case necessarily ex=1). */
    } else {
      C = A + B;
      if (Long.compareUnsigned(C, A) < 0) {
        C = (1L << 63) | (C >>> 1);
        r.ex++;
      }
    }

    /* In the addition case, we loose the truncated part of B, which
       contributes to at most 1 ulp. If there is an exponent shift, we
       might also loose the least significant bit of C, which counts as
       1/2 ulp, but the truncated part of B is now less than 1/2 ulp too,
       thus in all cases the error is less than 1 ulp(r). */

    r.sgn = sgn;
    r.hi = C;
  }

  // Multiply two dint64_t numbers, with error bounded by 6 ulps
  // on the 128-bit floating-point numbers.
  // Overlap between r and a is allowed
  private static void mulDint(Dint64 r, Dint64 a, Dint64 b) {
    long bh = b.hi, bl = b.lo;

    /* compute the two middle terms */
    U128 m1 = U128.mul(a.hi, bl);
    U128 m2 = U128.mul(a.lo, bh);

    /* put the 128-bit product of the high terms in r */
    r.r(U128.mul(a.hi, bh));

    /* there can be no overflow in the following addition since r <= (B-1)^2
       with B=2^64, (m1>>64) <= B-1 and (m2>>64) <= B-1, thus the sum is
       bounded by (B-1)^2+2*(B-1) = B^2-1 */
    r.r(r.r().add(m1.shr(64)).add(m2.shr(64)));

    // Ensure that r->hi starts with a 1
    long ex = r.hi >>> 63;
    r.r(r.r().shl((int) (1 - ex)));

    // Exponent and sign
    r.ex = a.ex + b.ex + ex;
    r.sgn = a.sgn ^ b.sgn;

    /* The ignored part can be as large as 3 ulps before the shift (one
       for the low part of a->hi * bl, one for the low part of a->lo * bh,
       and one for the neglected a->lo * bl term). After the shift this can
       be as large as 6 ulps. */
  }

  // Multiply two dint64_t numbers, assuming the low part of b is zero
  // with error bounded by 2 ulps
  private static void mulDint21(Dint64 r, Dint64 a, Dint64 b) {
    long bh = b.hi;
    U128 hi = U128.mul(a.hi, bh);
    U128 lo = U128.mul(a.lo, bh);

    /* put the 128-bit product of the high terms in r */
    r.r(hi);

    /* add the middle term */
    r.r(r.r().add(lo.shr(64)));

    // Ensure that r->hi starts with a 1
    long ex = r.hi >>> 63;
    r.r(r.r().shl((int) (1 - ex)));

    // Exponent and sign
    r.ex = a.ex + b.ex + ex;
    r.sgn = a.sgn ^ b.sgn;

    /* The ignored part can be as large as 1 ulp before the shift (truncated
       part of lo). After the shift this can be as large as 2 ulps. */
  }

  /* Same as mul_dint_21, but assumes the low part of a and b is zero.
     This operation is exact. */
  private static void mulDint11(Dint64 r, Dint64 a, Dint64 b) {
    /* put the 128-bit product of the high terms in r */
    r.r(U128.mul(a.hi, b.hi));

    // Ensure that r->hi starts with a 1
    long ex = r.hi >>> 63;
    r.r(r.r().shl((int) (1 - ex)));

    // Exponent and sign
    r.ex = a.ex + b.ex + ex;
    r.sgn = a.sgn ^ b.sgn;
  }

  // Multiply an integer with a dint64_t variable, with error < 1 ulp
  // r and b should not overlap
  private static void mulDintInt64(Dint64 r, Dint64 a, long b) {
    if (b == 0) {
      cpDint(r, ZERO);
      return;
    }

    long c = b < 0 ? -b : b;
    r.sgn = b < 0 ? (a.sgn == 0 ? 1 : 0) : a.sgn;
    r.ex = a.ex + 64;

    r.r(U128.mul(a.hi, c));

    // Warning: if c=1, we might have r->hi=0
    int m = r.hi != 0 ? Long.numberOfLeadingZeros(r.hi) : 64;
    r.r(r.r().shl(m));
    r.ex -= m;

    // Will pose issues if b is too large but for now we assume it never happens
    // TODO: FIXME
    U128 l = U128.mul(a.lo, c);
    /* We have to shift l by 64 bits to the right to align with hi*c,
       and by m bits to the left to align with t.r << m. Since hi*c < 2^(128-m)
       and hi >= 2^63, we know that c < 2^(65-m) thus
       l*2^(m-1) < 2^64*2^(65-m)*2^(m-1) = 2^128, and l << (m - 1) will
       not overflow. */
    l = l.shl(m - 1).shr(63);

    r.r(r.r().add(l));
    if (r.r().compareUnsigned(l) < 0) {
      r.r(U128.of(0, 1L << 63).or(r.r().shr(1)));
      r.ex++;
    }

    /* The ignored part of a->lo*c is at most 1 ulp(r), even in the overflow
       case "r->r < l", since before the right shift, the error was at most
       1 ulp, thus 1/2 ulp after the shift, and the ignored least significant
       bit of r->r which is discarded counts also as 1/2 ulp. */
  }

  // Convert a non-zero double to the corresponding dint64_t value
  private static void dintFromd(Dint64 a, double b) {
    fastExtract(a, b);

    /* |b| = 2^(ex-52)*hi */

    int t = Long.numberOfLeadingZeros(a.hi);

    a.sgn = b < 0.0 ? 1 : 0;
    a.hi = a.hi << t;
    a.ex = a.ex - (t > 11 ? t - 12 : 0);
    /* b = 2^ex*hi/2^63 where 1 <= hi/2^63 < 2 */
    a.lo = 0;
  }

  private static final long[] xmax = { 0, 0xffffffffffffffffL,
                                   94906265, 208063, 9741, 1551, 455, 189, 97,
                                   59, 39, 27, 21, 15, 13, 11, 9, 7, 7, 5, 5,
                                   5, 5, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3 };

  private static final double[] _INVERSE = {
    0x1.69p+0, 0x1.67p+0, 0x1.65p+0, 0x1.63p+0, 0x1.61p+0, 0x1.5fp+0, 0x1.5ep+0,
    0x1.5cp+0, 0x1.5ap+0, 0x1.58p+0, 0x1.56p+0, 0x1.54p+0, 0x1.53p+0, 0x1.51p+0,
    0x1.4fp+0, 0x1.4ep+0, 0x1.4cp+0, 0x1.4ap+0, 0x1.48p+0, 0x1.47p+0, 0x1.45p+0,
    0x1.44p+0, 0x1.42p+0, 0x1.4p+0, 0x1.3fp+0, 0x1.3dp+0, 0x1.3cp+0, 0x1.3ap+0,
    0x1.39p+0, 0x1.37p+0, 0x1.36p+0, 0x1.34p+0, 0x1.33p+0, 0x1.32p+0, 0x1.3p+0,
    0x1.2fp+0, 0x1.2dp+0, 0x1.2cp+0, 0x1.2bp+0, 0x1.29p+0, 0x1.28p+0, 0x1.27p+0,
    0x1.25p+0, 0x1.24p+0, 0x1.23p+0, 0x1.21p+0, 0x1.2p+0, 0x1.1fp+0, 0x1.1ep+0,
    0x1.1cp+0, 0x1.1bp+0, 0x1.1ap+0, 0x1.19p+0, 0x1.17p+0, 0x1.16p+0, 0x1.15p+0,
    0x1.14p+0, 0x1.13p+0, 0x1.12p+0, 0x1.1p+0, 0x1.0fp+0, 0x1.0ep+0, 0x1.0dp+0,
    0x1.0cp+0, 0x1.0bp+0, 0x1.0ap+0, 0x1.09p+0, 0x1.08p+0, 0x1.07p+0, 0x1.06p+0,
    0x1.05p+0, 0x1.04p+0, 0x1.03p+0, 0x1.02p+0, 0x1.00p+0, 0x1.00p+0, 0x1.fdp-1,
    0x1.fbp-1, 0x1.f9p-1, 0x1.f7p-1, 0x1.f5p-1, 0x1.f3p-1, 0x1.f1p-1, 0x1.fp-1,
    0x1.eep-1, 0x1.ecp-1, 0x1.eap-1, 0x1.e8p-1, 0x1.e6p-1, 0x1.e5p-1, 0x1.e3p-1,
    0x1.e1p-1, 0x1.dfp-1, 0x1.ddp-1, 0x1.dcp-1, 0x1.dap-1, 0x1.d8p-1, 0x1.d7p-1,
    0x1.d5p-1, 0x1.d3p-1, 0x1.d2p-1, 0x1.dp-1, 0x1.cep-1, 0x1.cdp-1, 0x1.cbp-1,
    0x1.c9p-1, 0x1.c8p-1, 0x1.c6p-1, 0x1.c5p-1, 0x1.c3p-1, 0x1.c2p-1, 0x1.cp-1,
    0x1.bfp-1, 0x1.bdp-1, 0x1.bcp-1, 0x1.bap-1, 0x1.b9p-1, 0x1.b7p-1, 0x1.b6p-1,
    0x1.b4p-1, 0x1.b3p-1, 0x1.b1p-1, 0x1.bp-1, 0x1.aep-1, 0x1.adp-1, 0x1.acp-1,
    0x1.aap-1, 0x1.a9p-1, 0x1.a7p-1, 0x1.a6p-1, 0x1.a5p-1, 0x1.a3p-1, 0x1.a2p-1,
    0x1.a1p-1, 0x1.9fp-1, 0x1.9ep-1, 0x1.9dp-1, 0x1.9cp-1, 0x1.9ap-1, 0x1.99p-1,
    0x1.98p-1, 0x1.96p-1, 0x1.95p-1, 0x1.94p-1, 0x1.93p-1, 0x1.91p-1, 0x1.9p-1,
    0x1.8fp-1, 0x1.8ep-1, 0x1.8dp-1, 0x1.8bp-1, 0x1.8ap-1, 0x1.89p-1, 0x1.88p-1,
    0x1.87p-1, 0x1.86p-1, 0x1.84p-1, 0x1.83p-1, 0x1.82p-1, 0x1.81p-1, 0x1.8p-1,
    0x1.7fp-1, 0x1.7ep-1, 0x1.7cp-1, 0x1.7bp-1, 0x1.7ap-1, 0x1.79p-1, 0x1.78p-1,
    0x1.77p-1, 0x1.76p-1, 0x1.75p-1, 0x1.74p-1, 0x1.73p-1, 0x1.72p-1, 0x1.71p-1,
    0x1.7p-1, 0x1.6fp-1, 0x1.6ep-1, 0x1.6dp-1, 0x1.6cp-1, 0x1.6bp-1, 0x1.6ap-1,
};

  private static final double[][] _LOG_INV = {
    {-0x1.5ff3070a79p-2, -0x1.e9e439f105039p-45},
    {-0x1.5a42ab0f4dp-2, 0x1.e63af2df7ba69p-50},
    {-0x1.548a2c3addp-2, -0x1.3167e63081cf7p-45},
    {-0x1.4ec97326p-2, -0x1.34d7aaf04d104p-45},
    {-0x1.4900680401p-2, 0x1.8bccffe1a0f8cp-44},
    {-0x1.432ef2a04fp-2, 0x1.fb129931715adp-44},
    {-0x1.404308686ap-2, -0x1.f8ef43049f7d3p-44},
    {-0x1.3a64c55694p-2, -0x1.7a71cbcd735dp-44},
    {-0x1.347dd9a988p-2, 0x1.5594dd4c58092p-45},
    {-0x1.2e8e2bae12p-2, 0x1.67b1e99b72bd8p-45},
    {-0x1.2895a13de8p-2, -0x1.a8d7ad24c13fp-44},
    {-0x1.22941fbcf8p-2, 0x1.a6976f5eb0963p-44},
    {-0x1.1f8ff9e48ap-2, -0x1.7946c040cbe77p-45},
    {-0x1.1980d2dd42p-2, -0x1.b7b3a7a361c9ap-45},
    {-0x1.136870293bp-2, 0x1.d3e8499d67123p-44},
    {-0x1.1058bf9ae5p-2, 0x1.4ab9d817d52cdp-44},
    {-0x1.0a324e2739p-2, -0x1.c6bee7ef4030ep-47},
    {-0x1.0402594b4dp-2, -0x1.036b89ef42d7fp-48},
    {-0x1.fb9186d5e4p-3, 0x1.d572aab993c87p-47},
    {-0x1.f550a564b8p-3, 0x1.323e3a09202fep-45},
    {-0x1.e8c0252aa6p-3, 0x1.6805b80e8e6ffp-45},
    {-0x1.e27076e2bp-3, 0x1.a342c2af0003cp-44},
    {-0x1.d5c216b4fcp-3, 0x1.1ba91bbca681bp-45},
    {-0x1.c8ff7c79aap-3, 0x1.7794f689f8434p-45},
    {-0x1.c2968558c2p-3, 0x1.cfd73dee38a4p-45},
    {-0x1.b5b519e8fcp-3, 0x1.4b722ec011f31p-44},
    {-0x1.af3c94e80cp-3, 0x1.a4e633fcd9066p-52},
    {-0x1.a23bc1fe2cp-3, 0x1.539cd91dc9f0bp-44},
    {-0x1.9bb362e7ep-3, 0x1.1f2a8a1ce0ffcp-45},
    {-0x1.8e928de886p-3, -0x1.a8154b13d72d5p-44},
    {-0x1.87fa06520cp-3, -0x1.22120401202fcp-44},
    {-0x1.7ab890210ep-3, 0x1.bdb9072534a58p-45},
    {-0x1.740f8f5404p-3, 0x1.0b66c99018aa1p-44},
    {-0x1.6d60fe719ep-3, 0x1.bc6e557134767p-44},
    {-0x1.5ff3070a7ap-3, 0x1.8586f183bebf2p-44},
    {-0x1.59338d9982p-3, -0x1.0ba68b7555d4ap-48},
    {-0x1.4ba36f39a6p-3, 0x1.4354bb3f219e5p-44},
    {-0x1.44d2b6ccb8p-3, 0x1.70cc16135783cp-46},
    {-0x1.3dfc2b0eccp-3, -0x1.8a72a62b8c13fp-45},
    {-0x1.303d718e48p-3, 0x1.680b5ce3ecb05p-50},
    {-0x1.29552f82p-3, 0x1.5b967f4471dfcp-44},
    {-0x1.2266f190a6p-3, 0x1.4d20ab840e7f6p-45},
    {-0x1.1478584674p-3, -0x1.563451027c75p-46},
    {-0x1.0d77e7cd08p-3, -0x1.cb2cd2ee2f482p-44},
    {-0x1.0671512ca6p-3, 0x1.a47579cdc0a3dp-45},
    {-0x1.f0a30c0118p-4, 0x1.d599e83368e91p-44},
    {-0x1.e27076e2bp-4, 0x1.a342c2af0003cp-45},
    {-0x1.d4313d66ccp-4, 0x1.9454379135713p-45},
    {-0x1.c5e548f5bcp-4, -0x1.d0c57585fbe06p-46},
    {-0x1.a926d3a4acp-4, -0x1.563650bd22a9cp-44},
    {-0x1.9ab4246204p-4, 0x1.8a64826787061p-45},
    {-0x1.8c345d6318p-4, -0x1.b20f5acb42a66p-44},
    {-0x1.7da766d7bp-4, -0x1.2cc844480c89bp-44},
    {-0x1.60658a9374p-4, -0x1.0c3b1dee9c4f8p-44},
    {-0x1.51b073f06p-4, -0x1.83f69278e686ap-44},
    {-0x1.42edcbea64p-4, -0x1.bc0eeea7c9acdp-46},
    {-0x1.341d7961bcp-4, -0x1.1d0929983761p-44},
    {-0x1.253f62f0ap-4, -0x1.416f8fb69a701p-44},
    {-0x1.16536eea38p-4, 0x1.47c5e768fa309p-46},
    {-0x1.f0a30c0118p-5, 0x1.d599e83368e91p-45},
    {-0x1.d276b8adbp-5, -0x1.6a423c78a64bp-46},
    {-0x1.b42dd71198p-5, 0x1.c827ae5d6704cp-46},
    {-0x1.95c830ec9p-5, 0x1.c148297c5feb8p-45},
    {-0x1.77458f633p-5, 0x1.181dce586af09p-44},
    {-0x1.58a5bafc9p-5, 0x1.b2b739570ad39p-45},
    {-0x1.39e87b9fe8p-5, -0x1.eafd480ad9015p-44},
    {-0x1.1b0d98924p-5, 0x1.3401e9ae889bbp-44},
    {-0x1.f829b0e78p-6, -0x1.980267c7e09e4p-45},
    {-0x1.b9fc027bp-6, 0x1.b9a010ae6922ap-44},
    {-0x1.7b91b07d6p-6, 0x1.3b955b602ace4p-44},
    {-0x1.3cea44347p-6, 0x1.6a2c432d6a40bp-44},
    {-0x1.fc0a8b0fcp-7, -0x1.f1e7cf6d3a69cp-50},
    {-0x1.7dc475f82p-7, 0x1.eb1245b5da1f5p-44},
    {-0x1.fe02a6b1p-8, -0x1.9e23f0dda40e4p-46},
    {0, 0},
    {0, 0},
    {0x1.812121458p-8, 0x1.ad50382973f27p-46},
    {0x1.41929f968p-7, 0x1.977c755d01368p-46},
    {0x1.c317384c8p-7, -0x1.41f33fcefb9fep-44},
    {0x1.228fb1feap-6, 0x1.713e3284991fep-45},
    {0x1.63d617869p-6, 0x1.7abf389596542p-47},
    {0x1.a55f548c6p-6, -0x1.de0709f2d03c9p-45},
    {0x1.e72bf2814p-6, -0x1.8d75149774d47p-45},
    {0x1.0415d89e78p-5, -0x1.dddc7f461c516p-44},
    {0x1.252f32f8dp-5, 0x1.83e9ae021b67bp-45},
    {0x1.466aed42ep-5, -0x1.c167375bdfd28p-45},
    {0x1.67c94f2d48p-5, 0x1.dac20827cca0cp-44},
    {0x1.894aa149f8p-5, 0x1.9a19a8be97661p-44},
    {0x1.aaef2d0fbp-5, 0x1.0fc1a353bb42ep-45},
    {0x1.bbcebfc69p-5, -0x1.7bf868c317c2ap-46},
    {0x1.dda8adc68p-5, -0x1.1b1ac64d9e42fp-45},
    {0x1.ffa6911ab8p-5, 0x1.3008c98381a8fp-45},
    {0x1.10e45b3cbp-4, -0x1.7cf69284a3465p-44},
    {0x1.2207b5c784p-4, 0x1.49d8cfc10c7bfp-44},
    {0x1.2aa04a447p-4, 0x1.7a48ba8b1cb41p-44},
    {0x1.3bdf5a7d2p-4, -0x1.19bd0ad125895p-44},
    {0x1.4d3115d208p-4, -0x1.53a2582f4e1efp-48},
    {0x1.55e10050ep-4, 0x1.c1d740c53c72ep-47},
    {0x1.674f089364p-4, 0x1.a79994c9d3302p-44},
    {0x1.78d02263d8p-4, 0x1.69b5794b69fb7p-47},
    {0x1.8197e2f41p-4, -0x1.c0fe460d20041p-44},
    {0x1.9335e5d594p-4, 0x1.3115c3abd47dap-45},
    {0x1.a4e7640b1cp-4, -0x1.e42b6b94407c8p-47},
    {0x1.adc77ee5bp-4, -0x1.573b209c31904p-44},
    {0x1.bf968769fcp-4, 0x1.4218c8d824283p-45},
    {0x1.d179788218p-4, 0x1.36433b5efbeedp-44},
    {0x1.da72763844p-4, 0x1.a89401fa71733p-46},
    {0x1.ec739830ap-4, 0x1.11fcba80cdd1p-44},
    {0x1.f57bc7d9p-4, 0x1.76a6c9ea8b04ep-46},
    {0x1.03cdc0a51ep-3, 0x1.81a9cf169fc5cp-44},
    {0x1.08598b59e4p-3, -0x1.7e5dd7009902cp-45},
    {0x1.1178e8227ep-3, 0x1.1ef78ce2d07f2p-45},
    {0x1.160c8024b2p-3, 0x1.ec2d2a9009e3dp-45},
    {0x1.1f3b925f26p-3, -0x1.5f74e9b083633p-46},
    {0x1.23d712a49cp-3, 0x1.00d238fd3df5cp-46},
    {0x1.2d1610c868p-3, 0x1.39d6ccb81b4a1p-47},
    {0x1.31b994d3a4p-3, 0x1.f098ee3a5081p-44},
    {0x1.3b08b6758p-3, -0x1.aade8f29320fbp-44},
    {0x1.3fb45a5992p-3, 0x1.19713c0cae559p-44},
    {0x1.4913d8333cp-3, -0x1.53e43558124c4p-44},
    {0x1.4dc7b897bcp-3, 0x1.c79b60ae1ff0fp-47},
    {0x1.5737cc9018p-3, 0x1.9baa7a6b887f6p-44},
    {0x1.5bf406b544p-3, -0x1.27023eb68981cp-46},
    {0x1.6574ebe8c2p-3, -0x1.98c1d34f0f462p-44},
    {0x1.6a399dabbep-3, -0x1.8f934e66a15a6p-44},
    {0x1.6f0128b756p-3, 0x1.577390d31ef0fp-44},
    {0x1.7898d85444p-3, 0x1.8e67be3dbaf3fp-44},
    {0x1.7d6903caf6p-3, -0x1.4c06b17c301d7p-45},
    {0x1.871213750ep-3, 0x1.328eb42f9af75p-44},
    {0x1.8beafeb39p-3, -0x1.73d54aae92cd1p-47},
    {0x1.90c6db9fccp-3, -0x1.935f57718d7cap-46},
    {0x1.9a8778debap-3, 0x1.470fa3efec39p-44},
    {0x1.9f6c40708ap-3, -0x1.337d94bcd3f43p-44},
    {0x1.a454082e6ap-3, 0x1.60a77c81f7171p-44},
    {0x1.ae2ca6f672p-3, 0x1.7a8d5ae54f55p-44},
    {0x1.b31d8575bcp-3, 0x1.c794e562a63cbp-44},
    {0x1.b811730b82p-3, 0x1.e90683b9cd768p-46},
    {0x1.bd087383bep-3, -0x1.d4bc4595412b6p-45},
    {0x1.c6ffbc6fp-3, 0x1.ee138d3a69d43p-44},
    {0x1.cc000c9db4p-3, -0x1.d6d585d57aff9p-46},
    {0x1.d1037f2656p-3, -0x1.84a7e75b6f6e4p-47},
    {0x1.db13db0d48p-3, 0x1.2806a847527e6p-44},
    {0x1.e020cc6236p-3, -0x1.52b00adb91424p-45},
    {0x1.e530effe72p-3, -0x1.fdbdbb13f7c18p-44},
    {0x1.ea4449f04ap-3, 0x1.5e91663732a36p-44},
    {0x1.f474b134ep-3, -0x1.bae49f1df7b5ep-44},
    {0x1.f991c6cb3cp-3, -0x1.90d04cd7cc834p-44},
    {0x1.feb2233eap-3, 0x1.f3418de00938bp-45},
    {0x1.01eae5626cp-2, 0x1.a43dcfade85aep-44},
    {0x1.047e60cde8p-2, 0x1.dbdf10d397f3cp-45},
    {0x1.09aa572e6cp-2, 0x1.b50a1e1734342p-44},
    {0x1.0c42d67616p-2, 0x1.7188b163ceae9p-45},
    {0x1.0edd060b78p-2, 0x1.019b52d8435f5p-47},
    {0x1.1178e8227ep-2, 0x1.1ef78ce2d07f2p-44},
    {0x1.14167ef367p-2, 0x1.e0c07824daaf5p-44},
    {0x1.16b5ccbadp-2, -0x1.23299042d74bfp-44},
    {0x1.1bf99635a7p-2, -0x1.1ac89575c2125p-44},
    {0x1.1e9e16788ap-2, -0x1.82eaed3c8b65ep-44},
    {0x1.214456d0ecp-2, -0x1.caf0428b728a3p-44},
    {0x1.23ec5991ecp-2, -0x1.6dbe448a2e522p-44},
    {0x1.269621134ep-2, -0x1.1b61f10522625p-44},
    {0x1.2941afb187p-2, -0x1.210c2b730e28bp-44},
    {0x1.2bef07cdc9p-2, 0x1.a9cfa4a5004f4p-45},
    {0x1.314f1e1d36p-2, -0x1.8e27ad3213cb8p-45},
    {0x1.3401e12aedp-2, -0x1.17c73556e291dp-44},
    {0x1.36b6776be1p-2, 0x1.16ecdb0f177c8p-46},
    {0x1.396ce359bcp-2, -0x1.5839c5663663dp-47},
    {0x1.3c25277333p-2, 0x1.83b54b606bd5cp-46},
    {0x1.3edf463c17p-2, -0x1.f067c297f2c3fp-44},
    {0x1.419b423d5fp-2, -0x1.ce379226de3ecp-44},
    {0x1.44591e053ap-2, -0x1.6e95892923d88p-47},
    {0x1.4718dc271cp-2, 0x1.06c18fb4c14c5p-44},
    {0x1.49da7f3bccp-2, 0x1.07b334daf4b9ap-44},
    {0x1.4c9e09e173p-2, -0x1.e20891b0ad8a4p-45},
    {0x1.4f637ebbaap-2, -0x1.fc158cb3124b9p-44},
    {0x1.522ae0738ap-2, 0x1.ebe708164c759p-45},
    {0x1.54f431b7bep-2, 0x1.a8954c0910952p-46},
    {0x1.57bf753c8dp-2, 0x1.fadedee5d40efp-46},
    {0x1.5a8cadbbeep-2, -0x1.7c79b0af7ecf8p-48},
    {0x1.5d5bddf596p-2, -0x1.a0b2a08a465dcp-47},
    {0x1.602d08af09p-2, 0x1.ebe9176df3f65p-46},
    {0x1.630030b3abp-2, -0x1.db623e731aep-45},
};

  private static final double[][] T1 = {
    {              0x1p+0,                 0x0p+0},
    {0x1.02c9a3e778061p+0, -0x1.19083535b085dp-56},
    {0x1.059b0d3158574p+0,  0x1.d73e2a475b465p-55},
    {0x1.0874518759bc8p+0,  0x1.186be4bb284ffp-57},
    {0x1.0b5586cf9890fp+0,  0x1.8a62e4adc610bp-54},
    {0x1.0e3ec32d3d1a2p+0,  0x1.03a1727c57b53p-59},
    {0x1.11301d0125b51p+0, -0x1.6c51039449b3ap-54},
    { 0x1.1429aaea92dep+0, -0x1.32fbf9af1369ep-54},
    {0x1.172b83c7d517bp+0, -0x1.19041b9d78a76p-55},
    {0x1.1a35beb6fcb75p+0,  0x1.e5b4c7b4968e4p-55},
    {0x1.1d4873168b9aap+0,  0x1.e016e00a2643cp-54},
    {0x1.2063b88628cd6p+0,  0x1.dc775814a8495p-55},
    {0x1.2387a6e756238p+0,  0x1.9b07eb6c70573p-54},
    {0x1.26b4565e27cddp+0,  0x1.2bd339940e9d9p-55},
    {0x1.29e9df51fdee1p+0,  0x1.612e8afad1255p-55},
    {0x1.2d285a6e4030bp+0,  0x1.0024754db41d5p-54},
    {0x1.306fe0a31b715p+0,  0x1.6f46ad23182e4p-55},
    {0x1.33c08b26416ffp+0,  0x1.32721843659a6p-54},
    {0x1.371a7373aa9cbp+0, -0x1.63aeabf42eae2p-54},
    {0x1.3a7db34e59ff7p+0, -0x1.5e436d661f5e3p-56},
    {0x1.3dea64c123422p+0,  0x1.ada0911f09ebcp-55},
    {0x1.4160a21f72e2ap+0, -0x1.ef3691c309278p-58},
    {0x1.44e086061892dp+0,   0x1.89b7a04ef80dp-59},
    { 0x1.486a2b5c13cdp+0,   0x1.3c1a3b69062fp-56},
    {0x1.4bfdad5362a27p+0,  0x1.d4397afec42e2p-56},
    {0x1.4f9b2769d2ca7p+0, -0x1.4b309d25957e3p-54},
    {0x1.5342b569d4f82p+0, -0x1.07abe1db13cadp-55},
    {0x1.56f4736b527dap+0,  0x1.9bb2c011d93adp-54},
    {0x1.5ab07dd485429p+0,  0x1.6324c054647adp-54},
    {0x1.5e76f15ad2148p+0,  0x1.ba6f93080e65ep-54},
    {0x1.6247eb03a5585p+0, -0x1.383c17e40b497p-54},
    {0x1.6623882552225p+0, -0x1.bb60987591c34p-54},
    {0x1.6a09e667f3bcdp+0, -0x1.bdd3413b26456p-54},
    {0x1.6dfb23c651a2fp+0, -0x1.bbe3a683c88abp-57},
    {0x1.71f75e8ec5f74p+0, -0x1.16e4786887a99p-55},
    {0x1.75feb564267c9p+0, -0x1.0245957316dd3p-54},
    {0x1.7a11473eb0187p+0, -0x1.41577ee04992fp-55},
    {0x1.7e2f336cf4e62p+0,  0x1.05d02ba15797ep-56},
    {0x1.82589994cce13p+0, -0x1.d4c1dd41532d8p-54},
    {0x1.868d99b4492edp+0, -0x1.fc6f89bd4f6bap-54},
    {0x1.8ace5422aa0dbp+0,  0x1.6e9f156864b27p-54},
    {0x1.8f1ae99157736p+0,  0x1.5cc13a2e3976cp-55},
    {0x1.93737b0cdc5e5p+0, -0x1.75fc781b57ebcp-57},
    { 0x1.97d829fde4e5p+0, -0x1.d185b7c1b85d1p-54},
    { 0x1.9c49182a3f09p+0,  0x1.c7c46b071f2bep-56},
    {0x1.a0c667b5de565p+0, -0x1.359495d1cd533p-54},
    {0x1.a5503b23e255dp+0, -0x1.d2f6edb8d41e1p-54},
    {0x1.a9e6b5579fdbfp+0,  0x1.0fac90ef7fd31p-54},
    {0x1.ae89f995ad3adp+0,  0x1.7a1cd345dcc81p-54},
    {0x1.b33a2b84f15fbp+0, -0x1.2805e3084d708p-57},
    {0x1.b7f76f2fb5e47p+0, -0x1.5584f7e54ac3bp-56},
    {0x1.bcc1e904bc1d2p+0,  0x1.23dd07a2d9e84p-55},
    {0x1.c199bdd85529cp+0,  0x1.11065895048ddp-55},
    {0x1.c67f12e57d14bp+0,  0x1.2884dff483cadp-54},
    {0x1.cb720dcef9069p+0,  0x1.503cbd1e949dbp-56},
    {0x1.d072d4a07897cp+0, -0x1.cbc3743797a9cp-54},
    {0x1.d5818dcfba487p+0,  0x1.2ed02d75b3707p-55},
    {0x1.da9e603db3285p+0,  0x1.c2300696db532p-54},
    {0x1.dfc97337b9b5fp+0, -0x1.1a5cd4f184b5cp-54},
    {0x1.e502ee78b3ff6p+0,  0x1.39e8980a9cc8fp-55},
    {0x1.ea4afa2a490dap+0, -0x1.e9c23179c2893p-54},
    {0x1.efa1bee615a27p+0,   0x1.dc7f486a4b6bp-54},
    { 0x1.f50765b6e454p+0,  0x1.9d3e12dd8a18bp-54},
    {0x1.fa7c1819e90d8p+0,  0x1.74853f3a5931ep-55},
};

  private static final double[][] T2 = {
    {              0x1p+0,                 0x0p+0},
    {0x1.000b175effdc7p+0,  0x1.ae8e38c59c72ap-54},
    {0x1.00162f3904052p+0, -0x1.7b5d0d58ea8f4p-58},
    {0x1.0021478e11ce6p+0,  0x1.4115cb6b16a8ep-54},
    {0x1.002c605e2e8cfp+0, -0x1.d7c96f201bb2fp-55},
    {0x1.003779a95f959p+0,  0x1.84711d4c35e9fp-54},
    {0x1.0042936faa3d8p+0, -0x1.0484245243777p-55},
    { 0x1.004dadb113dap+0, -0x1.4b237da2025f9p-54},
    {0x1.0058c86da1c0ap+0, -0x1.5e00e62d6b30dp-56},
    {0x1.0063e3a559473p+0,  0x1.a1d6cedbb9481p-54},
    {0x1.006eff583fc3dp+0, -0x1.4acf197a00142p-54},
    {0x1.007a1b865a8cap+0, -0x1.eaf2ea42391a5p-57},
    {0x1.0085382faef83p+0,  0x1.da93f90835f75p-56},
    {0x1.00905554425d4p+0, -0x1.6a79084ab093cp-55},
    {0x1.009b72f41a12bp+0,  0x1.86364f8fbe8f8p-54},
    {0x1.00a6910f3b6fdp+0, -0x1.82e8e14e3110ep-55},
    {0x1.00b1afa5abcbfp+0, -0x1.4f6b2a7609f71p-55},
    {0x1.00bcceb7707ecp+0, -0x1.e1a258ea8f71bp-56},
    {0x1.00c7ee448ee02p+0,  0x1.4362ca5bc26f1p-56},
    {0x1.00d30e4d0c483p+0,  0x1.095a56c919d02p-54},
    {0x1.00de2ed0ee0f5p+0, -0x1.406ac4e81a645p-57},
    { 0x1.00e94fd0398ep+0,  0x1.b5a6902767e09p-54},
    {0x1.00f4714af41d3p+0, -0x1.91b2060859321p-54},
    {0x1.00ff93412315cp+0,  0x1.427068ab22306p-55},
    {0x1.010ab5b2cbd11p+0,  0x1.c1d0660524e08p-54},
    {0x1.0115d89ff3a8bp+0, -0x1.e7bdfb3204be8p-54},
    {0x1.0120fc089ff63p+0,  0x1.843aa8b9cbbc6p-55},
    {0x1.012c1fecd613bp+0, -0x1.34104ee7edae9p-56},
    {0x1.0137444c9b5b5p+0, -0x1.2b6aeb6176892p-56},
    {0x1.01426927f5278p+0,  0x1.a8cd33b8a1bb3p-56},
    {0x1.014d8e7ee8d2fp+0,  0x1.2edc08e5da99ap-56},
    {0x1.0158b4517bb88p+0,  0x1.57ba2dc7e0c73p-55},
    {0x1.0163da9fb3335p+0,  0x1.b61299ab8cdb7p-54},
    {0x1.016f0169949edp+0, -0x1.90565902c5f44p-54},
    {0x1.017a28af25567p+0,  0x1.70fc41c5c2d53p-55},
    {0x1.018550706ab62p+0,  0x1.4b9a6e145d76cp-54},
    {0x1.019078ad6a19fp+0, -0x1.008eff5142bf9p-56},
    {0x1.019ba16628de2p+0, -0x1.77669f033c7dep-54},
    {0x1.01a6ca9aac5f3p+0, -0x1.09bb78eeead0ap-54},
    {0x1.01b1f44af9f9ep+0,  0x1.371231477ece5p-54},
    {0x1.01bd1e77170b4p+0,  0x1.5e7626621eb5bp-56},
    {0x1.01c8491f08f08p+0, -0x1.bc72b100828a5p-54},
    { 0x1.01d37442d507p+0, -0x1.ce39cbbab8bbep-57},
    {0x1.01de9fe280ac8p+0,  0x1.16996709da2e2p-55},
    {0x1.01e9cbfe113efp+0, -0x1.c11f5239bf535p-55},
    {0x1.01f4f8958c1c6p+0,  0x1.e1d4eb5edc6b3p-55},
    {0x1.020025a8f6a35p+0, -0x1.afb99946ee3fp-54},
    {0x1.020b533856324p+0, -0x1.8f06d8a148a32p-54},
    {0x1.02168143b0281p+0, -0x1.2bf310fc54eb6p-55},
    {0x1.0221afcb09e3ep+0, -0x1.c95a035eb4175p-54},
    {0x1.022cdece68c4fp+0, -0x1.491793e46834dp-54},
    {0x1.02380e4dd22adp+0, -0x1.3e8d0d9c49091p-56},
    {0x1.02433e494b755p+0, -0x1.314aa16278aa3p-54},
    {0x1.024e6ec0da046p+0,  0x1.48daf888e9651p-55},
    {0x1.02599fb483385p+0,  0x1.56dc8046821f4p-55},
    {0x1.0264d1244c719p+0,  0x1.45b42356b9d47p-54},
    {0x1.027003103b10ep+0, -0x1.082ef51b61d7ep-56},
    {0x1.027b357854772p+0,  0x1.2106ed0920a34p-56},
    {0x1.0286685c9e059p+0, -0x1.fd4cf26ea5d0fp-54},
    {0x1.02919bbd1d1d8p+0, -0x1.09f8775e78084p-54},
    {0x1.029ccf99d720ap+0,  0x1.64cbba902ca27p-58},
    {0x1.02a803f2d170dp+0,  0x1.4383ef231d207p-54},
    {0x1.02b338c811703p+0,  0x1.4a47a505b3a47p-54},
    {0x1.02be6e199c811p+0,  0x1.e47120223467fp-54},
};

  private static final double[] P_1 = {0x1.5555555555558p-2,  /* degree 3 */
                             -0x1.0000000000003p-2, /* degree 4 */
                             0x1.999999981f535p-3,  /* degree 5 */
                             -0x1.55555553d1eb4p-3, /* degree 6 */
                             0x1.2494526fd4a06p-3,  /* degree 7 */
                             -0x1.0001f0c80e8cep-3, /* degree 8 */
};

  private static final double[] Q_1 = {0x1p0,                 /* degree 0 */
                             0x1p0,                 /* degree 1 */
                             0x1p-1,                /* degree 2 */
                             0x1.5555555997996p-3,  /* degree 3 */
                             0x1.5555555849d8dp-5   /* degree 4 */
};

  private static final double[] err = { 0x1.27p-64, /* 2^-63.797 < 0x1.27p-64 */
                                0x1.57p-58, /* 2^-57.579 < 0x1.57p-58 */
  };

  private static final Dint64 M_ONE = new Dint64(0x8000000000000000L, 0x0L, 0, 0x1L);

  private static final Dint64 LOG2 = new Dint64(0xb17217f7d1cf79abL, 0xc9e3b39803f2f6afL, -1, 0x0L);

  private static final Dint64 LOG2_INV = new Dint64(0xb8aa3b295c17f0bcL, 0x0L, 12, 0x0L);

  private static final Dint64 ZERO = new Dint64(0x0L, 0x0L, -1076, 0x0L);

  private static final Dint64[] _INVERSE_2_1 = {
    new Dint64(0xb500000000000000L, 0x0L, 0, 0x0L), /* i=90 */
    new Dint64(0xb300000000000000L, 0x0L, 0, 0x0L), /* i=91 */
    new Dint64(0xb100000000000000L, 0x0L, 0, 0x0L), /* i=92 */
    new Dint64(0xaf00000000000000L, 0x0L, 0, 0x0L), /* i=93 */
    new Dint64(0xad80000000000000L, 0x0L, 0, 0x0L), /* i=94 */
    new Dint64(0xab80000000000000L, 0x0L, 0, 0x0L), /* i=95 */
    new Dint64(0xaa00000000000000L, 0x0L, 0, 0x0L), /* i=96 */
    new Dint64(0xa800000000000000L, 0x0L, 0, 0x0L), /* i=97 */
    new Dint64(0xa680000000000000L, 0x0L, 0, 0x0L), /* i=98 */
    new Dint64(0xa480000000000000L, 0x0L, 0, 0x0L), /* i=99 */
    new Dint64(0xa300000000000000L, 0x0L, 0, 0x0L), /* i=100 */
    new Dint64(0xa180000000000000L, 0x0L, 0, 0x0L), /* i=101 */
    new Dint64(0xa000000000000000L, 0x0L, 0, 0x0L), /* i=102 */
    new Dint64(0x9e80000000000000L, 0x0L, 0, 0x0L), /* i=103 */
    new Dint64(0x9d00000000000000L, 0x0L, 0, 0x0L), /* i=104 */
    new Dint64(0x9b80000000000000L, 0x0L, 0, 0x0L), /* i=105 */
    new Dint64(0x9a00000000000000L, 0x0L, 0, 0x0L), /* i=106 */
    new Dint64(0x9880000000000000L, 0x0L, 0, 0x0L), /* i=107 */
    new Dint64(0x9700000000000000L, 0x0L, 0, 0x0L), /* i=108 */
    new Dint64(0x9580000000000000L, 0x0L, 0, 0x0L), /* i=109 */
    new Dint64(0x9480000000000000L, 0x0L, 0, 0x0L), /* i=110 */
    new Dint64(0x9300000000000000L, 0x0L, 0, 0x0L), /* i=111 */
    new Dint64(0x9180000000000000L, 0x0L, 0, 0x0L), /* i=112 */
    new Dint64(0x9080000000000000L, 0x0L, 0, 0x0L), /* i=113 */
    new Dint64(0x8f00000000000000L, 0x0L, 0, 0x0L), /* i=114 */
    new Dint64(0x8e00000000000000L, 0x0L, 0, 0x0L), /* i=115 */
    new Dint64(0x8c80000000000000L, 0x0L, 0, 0x0L), /* i=116 */
    new Dint64(0x8b80000000000000L, 0x0L, 0, 0x0L), /* i=117 */
    new Dint64(0x8a80000000000000L, 0x0L, 0, 0x0L), /* i=118 */
    new Dint64(0x8900000000000000L, 0x0L, 0, 0x0L), /* i=119 */
    new Dint64(0x8800000000000000L, 0x0L, 0, 0x0L), /* i=120 */
    new Dint64(0x8700000000000000L, 0x0L, 0, 0x0L), /* i=121 */
    new Dint64(0x8580000000000000L, 0x0L, 0, 0x0L), /* i=122 */
    new Dint64(0x8480000000000000L, 0x0L, 0, 0x0L), /* i=123 */
    new Dint64(0x8380000000000000L, 0x0L, 0, 0x0L), /* i=124 */
    new Dint64(0x8280000000000000L, 0x0L, 0, 0x0L), /* i=125 */
    new Dint64(0x8180000000000000L, 0x0L, 0, 0x0L), /* i=126 */
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L), /* i=127 */
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L), /* i=128 */
    new Dint64(0xfd00000000000000L, 0x0L, -1, 0x0L), /* i=129 */
    new Dint64(0xfb00000000000000L, 0x0L, -1, 0x0L), /* i=130 */
    new Dint64(0xf900000000000000L, 0x0L, -1, 0x0L), /* i=131 */
    new Dint64(0xf780000000000000L, 0x0L, -1, 0x0L), /* i=132 */
    new Dint64(0xf580000000000000L, 0x0L, -1, 0x0L), /* i=133 */
    new Dint64(0xf380000000000000L, 0x0L, -1, 0x0L), /* i=134 */
    new Dint64(0xf200000000000000L, 0x0L, -1, 0x0L), /* i=135 */
    new Dint64(0xf000000000000000L, 0x0L, -1, 0x0L), /* i=136 */
    new Dint64(0xee80000000000000L, 0x0L, -1, 0x0L), /* i=137 */
    new Dint64(0xec80000000000000L, 0x0L, -1, 0x0L), /* i=138 */
    new Dint64(0xeb00000000000000L, 0x0L, -1, 0x0L), /* i=139 */
    new Dint64(0xe900000000000000L, 0x0L, -1, 0x0L), /* i=140 */
    new Dint64(0xe780000000000000L, 0x0L, -1, 0x0L), /* i=141 */
    new Dint64(0xe600000000000000L, 0x0L, -1, 0x0L), /* i=142 */
    new Dint64(0xe480000000000000L, 0x0L, -1, 0x0L), /* i=143 */
    new Dint64(0xe300000000000000L, 0x0L, -1, 0x0L), /* i=144 */
    new Dint64(0xe100000000000000L, 0x0L, -1, 0x0L), /* i=145 */
    new Dint64(0xdf80000000000000L, 0x0L, -1, 0x0L), /* i=146 */
    new Dint64(0xde00000000000000L, 0x0L, -1, 0x0L), /* i=147 */
    new Dint64(0xdc80000000000000L, 0x0L, -1, 0x0L), /* i=148 */
    new Dint64(0xdb00000000000000L, 0x0L, -1, 0x0L), /* i=149 */
    new Dint64(0xd980000000000000L, 0x0L, -1, 0x0L), /* i=150 */
    new Dint64(0xd880000000000000L, 0x0L, -1, 0x0L), /* i=151 */
    new Dint64(0xd700000000000000L, 0x0L, -1, 0x0L), /* i=152 */
    new Dint64(0xd580000000000000L, 0x0L, -1, 0x0L), /* i=153 */
    new Dint64(0xd400000000000000L, 0x0L, -1, 0x0L), /* i=154 */
    new Dint64(0xd280000000000000L, 0x0L, -1, 0x0L), /* i=155 */
    new Dint64(0xd180000000000000L, 0x0L, -1, 0x0L), /* i=156 */
    new Dint64(0xd000000000000000L, 0x0L, -1, 0x0L), /* i=157 */
    new Dint64(0xce80000000000000L, 0x0L, -1, 0x0L), /* i=158 */
    new Dint64(0xcd80000000000000L, 0x0L, -1, 0x0L), /* i=159 */
    new Dint64(0xcc00000000000000L, 0x0L, -1, 0x0L), /* i=160 */
    new Dint64(0xcb00000000000000L, 0x0L, -1, 0x0L), /* i=161 */
    new Dint64(0xc980000000000000L, 0x0L, -1, 0x0L), /* i=162 */
    new Dint64(0xc880000000000000L, 0x0L, -1, 0x0L), /* i=163 */
    new Dint64(0xc700000000000000L, 0x0L, -1, 0x0L), /* i=164 */
    new Dint64(0xc600000000000000L, 0x0L, -1, 0x0L), /* i=165 */
    new Dint64(0xc500000000000000L, 0x0L, -1, 0x0L), /* i=166 */
    new Dint64(0xc380000000000000L, 0x0L, -1, 0x0L), /* i=167 */
    new Dint64(0xc280000000000000L, 0x0L, -1, 0x0L), /* i=168 */
    new Dint64(0xc180000000000000L, 0x0L, -1, 0x0L), /* i=169 */
    new Dint64(0xc000000000000000L, 0x0L, -1, 0x0L), /* i=170 */
    new Dint64(0xbf00000000000000L, 0x0L, -1, 0x0L), /* i=171 */
    new Dint64(0xbe00000000000000L, 0x0L, -1, 0x0L), /* i=172 */
    new Dint64(0xbd00000000000000L, 0x0L, -1, 0x0L), /* i=173 */
    new Dint64(0xbc00000000000000L, 0x0L, -1, 0x0L), /* i=174 */
    new Dint64(0xba80000000000000L, 0x0L, -1, 0x0L), /* i=175 */
    new Dint64(0xb980000000000000L, 0x0L, -1, 0x0L), /* i=176 */
    new Dint64(0xb880000000000000L, 0x0L, -1, 0x0L), /* i=177 */
    new Dint64(0xb780000000000000L, 0x0L, -1, 0x0L), /* i=178 */
    new Dint64(0xb680000000000000L, 0x0L, -1, 0x0L), /* i=179 */
    new Dint64(0xb580000000000000L, 0x0L, -1, 0x0L), /* i=180 */
    new Dint64(0xb480000000000000L, 0x0L, -1, 0x0L), /* i=181 */
};

  private static final Dint64[] _INVERSE_2_2 = {
    new Dint64(0x8100000000000000L, 0x0L, 0, 0x0L), /* j=8128 */
    new Dint64(0x80fc000000000000L, 0x0L, 0, 0x0L), /* j=8129 */
    new Dint64(0x80f8000000000000L, 0x0L, 0, 0x0L), /* j=8130 */
    new Dint64(0x80f4000000000000L, 0x0L, 0, 0x0L), /* j=8131 */
    new Dint64(0x80f0000000000000L, 0x0L, 0, 0x0L), /* j=8132 */
    new Dint64(0x80ec000000000000L, 0x0L, 0, 0x0L), /* j=8133 */
    new Dint64(0x80e8000000000000L, 0x0L, 0, 0x0L), /* j=8134 */
    new Dint64(0x80e4000000000000L, 0x0L, 0, 0x0L), /* j=8135 */
    new Dint64(0x80e0000000000000L, 0x0L, 0, 0x0L), /* j=8136 */
    new Dint64(0x80dc000000000000L, 0x0L, 0, 0x0L), /* j=8137 */
    new Dint64(0x80d8000000000000L, 0x0L, 0, 0x0L), /* j=8138 */
    new Dint64(0x80d4000000000000L, 0x0L, 0, 0x0L), /* j=8139 */
    new Dint64(0x80d0000000000000L, 0x0L, 0, 0x0L), /* j=8140 */
    new Dint64(0x80cc000000000000L, 0x0L, 0, 0x0L), /* j=8141 */
    new Dint64(0x80c8000000000000L, 0x0L, 0, 0x0L), /* j=8142 */
    new Dint64(0x80c4000000000000L, 0x0L, 0, 0x0L), /* j=8143 */
    new Dint64(0x80c0000000000000L, 0x0L, 0, 0x0L), /* j=8144 */
    new Dint64(0x80bc000000000000L, 0x0L, 0, 0x0L), /* j=8145 */
    new Dint64(0x80b8000000000000L, 0x0L, 0, 0x0L), /* j=8146 */
    new Dint64(0x80b4000000000000L, 0x0L, 0, 0x0L), /* j=8147 */
    new Dint64(0x80b0000000000000L, 0x0L, 0, 0x0L), /* j=8148 */
    new Dint64(0x80ac000000000000L, 0x0L, 0, 0x0L), /* j=8149 */
    new Dint64(0x80a8000000000000L, 0x0L, 0, 0x0L), /* j=8150 */
    new Dint64(0x80a4000000000000L, 0x0L, 0, 0x0L), /* j=8151 */
    new Dint64(0x80a0000000000000L, 0x0L, 0, 0x0L), /* j=8152 */
    new Dint64(0x809c000000000000L, 0x0L, 0, 0x0L), /* j=8153 */
    new Dint64(0x8098000000000000L, 0x0L, 0, 0x0L), /* j=8154 */
    new Dint64(0x8094000000000000L, 0x0L, 0, 0x0L), /* j=8155 */
    new Dint64(0x8090000000000000L, 0x0L, 0, 0x0L), /* j=8156 */
    new Dint64(0x808c000000000000L, 0x0L, 0, 0x0L), /* j=8157 */
    new Dint64(0x8088000000000000L, 0x0L, 0, 0x0L), /* j=8158 */
    new Dint64(0x8084000000000000L, 0x0L, 0, 0x0L), /* j=8159 */
    new Dint64(0x8080000000000000L, 0x0L, 0, 0x0L), /* j=8160 */
    new Dint64(0x807c000000000000L, 0x0L, 0, 0x0L), /* j=8161 */
    new Dint64(0x8078000000000000L, 0x0L, 0, 0x0L), /* j=8162 */
    new Dint64(0x8074000000000000L, 0x0L, 0, 0x0L), /* j=8163 */
    new Dint64(0x8070000000000000L, 0x0L, 0, 0x0L), /* j=8164 */
    new Dint64(0x806c000000000000L, 0x0L, 0, 0x0L), /* j=8165 */
    new Dint64(0x8068000000000000L, 0x0L, 0, 0x0L), /* j=8166 */
    new Dint64(0x8064000000000000L, 0x0L, 0, 0x0L), /* j=8167 */
    new Dint64(0x8060000000000000L, 0x0L, 0, 0x0L), /* j=8168 */
    new Dint64(0x805c000000000000L, 0x0L, 0, 0x0L), /* j=8169 */
    new Dint64(0x8058000000000000L, 0x0L, 0, 0x0L), /* j=8170 */
    new Dint64(0x8054000000000000L, 0x0L, 0, 0x0L), /* j=8171 */
    new Dint64(0x8050000000000000L, 0x0L, 0, 0x0L), /* j=8172 */
    new Dint64(0x804c000000000000L, 0x0L, 0, 0x0L), /* j=8173 */
    new Dint64(0x8048000000000000L, 0x0L, 0, 0x0L), /* j=8174 */
    new Dint64(0x8044000000000000L, 0x0L, 0, 0x0L), /* j=8175 */
    new Dint64(0x8040000000000000L, 0x0L, 0, 0x0L), /* j=8176 */
    new Dint64(0x803c000000000000L, 0x0L, 0, 0x0L), /* j=8177 */
    new Dint64(0x8038000000000000L, 0x0L, 0, 0x0L), /* j=8178 */
    new Dint64(0x8034000000000000L, 0x0L, 0, 0x0L), /* j=8179 */
    new Dint64(0x8030000000000000L, 0x0L, 0, 0x0L), /* j=8180 */
    new Dint64(0x802c000000000000L, 0x0L, 0, 0x0L), /* j=8181 */
    new Dint64(0x8028000000000000L, 0x0L, 0, 0x0L), /* j=8182 */
    new Dint64(0x8024000000000000L, 0x0L, 0, 0x0L), /* j=8183 */
    new Dint64(0x8020000000000000L, 0x0L, 0, 0x0L), /* j=8184 */
    new Dint64(0x801c000000000000L, 0x0L, 0, 0x0L), /* j=8185 */
    new Dint64(0x8018000000000000L, 0x0L, 0, 0x0L), /* j=8186 */
    new Dint64(0x8014000000000000L, 0x0L, 0, 0x0L), /* j=8187 */
    new Dint64(0x8010000000000000L, 0x0L, 0, 0x0L), /* j=8188 */
    new Dint64(0x800c000000000000L, 0x0L, 0, 0x0L), /* j=8189 */
    new Dint64(0x8008000000000000L, 0x0L, 0, 0x0L), /* j=8190 */
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L), /* j=8191 */
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L), /* j=8192 */
    new Dint64(0xfff4000000000000L, 0x0L, -1, 0x0L), /* j=8193 */
    new Dint64(0xffec000000000000L, 0x0L, -1, 0x0L), /* j=8194 */
    new Dint64(0xffe4000000000000L, 0x0L, -1, 0x0L), /* j=8195 */
    new Dint64(0xffdc000000000000L, 0x0L, -1, 0x0L), /* j=8196 */
    new Dint64(0xffd4000000000000L, 0x0L, -1, 0x0L), /* j=8197 */
    new Dint64(0xffcc000000000000L, 0x0L, -1, 0x0L), /* j=8198 */
    new Dint64(0xffc4000000000000L, 0x0L, -1, 0x0L), /* j=8199 */
    new Dint64(0xffbc000000000000L, 0x0L, -1, 0x0L), /* j=8200 */
    new Dint64(0xffb4000000000000L, 0x0L, -1, 0x0L), /* j=8201 */
    new Dint64(0xffac000000000000L, 0x0L, -1, 0x0L), /* j=8202 */
    new Dint64(0xffa4000000000000L, 0x0L, -1, 0x0L), /* j=8203 */
    new Dint64(0xff9c000000000000L, 0x0L, -1, 0x0L), /* j=8204 */
    new Dint64(0xff94000000000000L, 0x0L, -1, 0x0L), /* j=8205 */
    new Dint64(0xff8c000000000000L, 0x0L, -1, 0x0L), /* j=8206 */
    new Dint64(0xff84000000000000L, 0x0L, -1, 0x0L), /* j=8207 */
    new Dint64(0xff7c000000000000L, 0x0L, -1, 0x0L), /* j=8208 */
    new Dint64(0xff74000000000000L, 0x0L, -1, 0x0L), /* j=8209 */
    new Dint64(0xff6c000000000000L, 0x0L, -1, 0x0L), /* j=8210 */
    new Dint64(0xff64000000000000L, 0x0L, -1, 0x0L), /* j=8211 */
    new Dint64(0xff5c000000000000L, 0x0L, -1, 0x0L), /* j=8212 */
    new Dint64(0xff54000000000000L, 0x0L, -1, 0x0L), /* j=8213 */
    new Dint64(0xff4c000000000000L, 0x0L, -1, 0x0L), /* j=8214 */
    new Dint64(0xff44000000000000L, 0x0L, -1, 0x0L), /* j=8215 */
    new Dint64(0xff3c000000000000L, 0x0L, -1, 0x0L), /* j=8216 */
    new Dint64(0xff34000000000000L, 0x0L, -1, 0x0L), /* j=8217 */
    new Dint64(0xff2c000000000000L, 0x0L, -1, 0x0L), /* j=8218 */
    new Dint64(0xff24000000000000L, 0x0L, -1, 0x0L), /* j=8219 */
    new Dint64(0xff1c000000000000L, 0x0L, -1, 0x0L), /* j=8220 */
    new Dint64(0xff14000000000000L, 0x0L, -1, 0x0L), /* j=8221 */
    new Dint64(0xff0c000000000000L, 0x0L, -1, 0x0L), /* j=8222 */
    new Dint64(0xff04000000000000L, 0x0L, -1, 0x0L), /* j=8223 */
    new Dint64(0xfefc000000000000L, 0x0L, -1, 0x0L), /* j=8224 */
    new Dint64(0xfef4000000000000L, 0x0L, -1, 0x0L), /* j=8225 */
    new Dint64(0xfeec000000000000L, 0x0L, -1, 0x0L), /* j=8226 */
    new Dint64(0xfee4000000000000L, 0x0L, -1, 0x0L), /* j=8227 */
    new Dint64(0xfedc000000000000L, 0x0L, -1, 0x0L), /* j=8228 */
    new Dint64(0xfed4000000000000L, 0x0L, -1, 0x0L), /* j=8229 */
    new Dint64(0xfecc000000000000L, 0x0L, -1, 0x0L), /* j=8230 */
    new Dint64(0xfec4000000000000L, 0x0L, -1, 0x0L), /* j=8231 */
    new Dint64(0xfebc000000000000L, 0x0L, -1, 0x0L), /* j=8232 */
    new Dint64(0xfeb4000000000000L, 0x0L, -1, 0x0L), /* j=8233 */
    new Dint64(0xfeac000000000000L, 0x0L, -1, 0x0L), /* j=8234 */
    new Dint64(0xfea4000000000000L, 0x0L, -1, 0x0L), /* j=8235 */
    new Dint64(0xfe9c000000000000L, 0x0L, -1, 0x0L), /* j=8236 */
    new Dint64(0xfe98000000000000L, 0x0L, -1, 0x0L), /* j=8237 */
    new Dint64(0xfe90000000000000L, 0x0L, -1, 0x0L), /* j=8238 */
    new Dint64(0xfe88000000000000L, 0x0L, -1, 0x0L), /* j=8239 */
    new Dint64(0xfe80000000000000L, 0x0L, -1, 0x0L), /* j=8240 */
    new Dint64(0xfe78000000000000L, 0x0L, -1, 0x0L), /* j=8241 */
    new Dint64(0xfe70000000000000L, 0x0L, -1, 0x0L), /* j=8242 */
    new Dint64(0xfe68000000000000L, 0x0L, -1, 0x0L), /* j=8243 */
    new Dint64(0xfe60000000000000L, 0x0L, -1, 0x0L), /* j=8244 */
    new Dint64(0xfe58000000000000L, 0x0L, -1, 0x0L), /* j=8245 */
    new Dint64(0xfe50000000000000L, 0x0L, -1, 0x0L), /* j=8246 */
    new Dint64(0xfe48000000000000L, 0x0L, -1, 0x0L), /* j=8247 */
    new Dint64(0xfe40000000000000L, 0x0L, -1, 0x0L), /* j=8248 */
    new Dint64(0xfe38000000000000L, 0x0L, -1, 0x0L), /* j=8249 */
    new Dint64(0xfe30000000000000L, 0x0L, -1, 0x0L), /* j=8250 */
    new Dint64(0xfe28000000000000L, 0x0L, -1, 0x0L), /* j=8251 */
    new Dint64(0xfe20000000000000L, 0x0L, -1, 0x0L), /* j=8252 */
    new Dint64(0xfe18000000000000L, 0x0L, -1, 0x0L), /* j=8253 */
    new Dint64(0xfe10000000000000L, 0x0L, -1, 0x0L), /* j=8254 */
    new Dint64(0xfe08000000000000L, 0x0L, -1, 0x0L), /* j=8255 */
    new Dint64(0xfe00000000000000L, 0x0L, -1, 0x0L), /* j=8256 */
};

  private static final Dint64[] _LOG_INV_2_1 = {
    new Dint64(0xb1641795ce3ca97bL, 0x7af915300e517391L, -2, 0x1L), /* i=90 */
    new Dint64(0xabb3b8ba2ad362a4L, 0xd5b6506cc17a01f1L, -2, 0x1L), /* i=91 */
    new Dint64(0xa5f2fcabbbc506daL, 0x64ca4fb7ec323d73L, -2, 0x1L), /* i=92 */
    new Dint64(0xa0218434353f1de8L, 0x6093efa632530ac8L, -2, 0x1L), /* i=93 */
    new Dint64(0x9bb93315fec2d792L, 0xa7589fba0865790eL, -2, 0x1L), /* i=94 */
    new Dint64(0x95c981d5c4e924edL, 0x29404f5aa577d6b2L, -2, 0x1L), /* i=95 */
    new Dint64(0x914a0fde7bcb2d12L, 0x1429ed3aea197a5dL, -2, 0x1L), /* i=96 */
    new Dint64(0x8b3ae55d5d30701cL, 0xe63eab883717047eL, -2, 0x1L), /* i=97 */
    new Dint64(0x86a35abcd5ba5903L, 0xec81c3cbd925cccfL, -2, 0x1L), /* i=98 */
    new Dint64(0x8073622d6a80e634L, 0x6a97009015316071L, -2, 0x1L), /* i=99 */
    new Dint64(0xf7856e5ee2c9b290L, 0xc6f2a1b84190a7d7L, -3, 0x1L), /* i=100 */
    new Dint64(0xee0de5055f63eb06L, 0x98a33316df83ba57L, -3, 0x1L), /* i=101 */
    new Dint64(0xe47fbe3cd4d10d61L, 0x2ec0f797fdcd1257L, -3, 0x1L), /* i=102 */
    new Dint64(0xdada8cf47dad2374L, 0x4ffb833c3409ee78L, -3, 0x1L), /* i=103 */
    new Dint64(0xd11de0ff15ab18c9L, 0xb88d83d4cc613f20L, -3, 0x1L), /* i=104 */
    new Dint64(0xc74946f4436a0552L, 0xc4f5cb531201c0d1L, -3, 0x1L), /* i=105 */
    new Dint64(0xbd5c481086c848dfL, 0x1b596b5030403240L, -3, 0x1L), /* i=106 */
    new Dint64(0xb3566a13956a86f6L, 0xff1b1e1574d9fd54L, -3, 0x1L), /* i=107 */
    new Dint64(0xa9372f1d0da1bd17L, 0x200eb71e58cd36deL, -3, 0x1L), /* i=108 */
    new Dint64(0x9efe158766314e54L, 0xc571827efe892fc4L, -3, 0x1L), /* i=109 */
    new Dint64(0x981eb8c723fe97f4L, 0xa31c134fb702d432L, -3, 0x1L), /* i=110 */
    new Dint64(0x8db956a97b3d0148L, 0x3023472cd739f9deL, -3, 0x1L), /* i=111 */
    new Dint64(0x8338a89652cb7150L, 0xc647eb86498c2ce1L, -3, 0x1L), /* i=112 */
    new Dint64(0xf85186008b15330bL, 0xe64b8b775997898dL, -4, 0x1L), /* i=113 */
    new Dint64(0xe2f2a47ade3a18aeL, 0xb0bf7c0b0d8bb4edL, -4, 0x1L), /* i=114 */
    new Dint64(0xd49369d256ab1b28L, 0x5e9154e1d5263cd5L, -4, 0x1L), /* i=115 */
    new Dint64(0xbed3b36bd8966422L, 0x240644d7d9ed08afL, -4, 0x1L), /* i=116 */
    new Dint64(0xb032c549ba861d8eL, 0xf74e27bc92ce336aL, -4, 0x1L), /* i=117 */
    new Dint64(0xa176e5f5323781ddL, 0xd4f935996c92e8ccL, -4, 0x1L), /* i=118 */
    new Dint64(0x8b29b7751bd70743L, 0x12e0b9ee992f236dL, -4, 0x1L), /* i=119 */
    new Dint64(0xf85186008b15330bL, 0xe64b8b775997898dL, -5, 0x1L), /* i=120 */
    new Dint64(0xda16eb88cb8df614L, 0x68a63ecfb66e94acL, -5, 0x1L), /* i=121 */
    new Dint64(0xac52dd7e4726a463L, 0x547a963a91bb3012L, -5, 0x1L), /* i=122 */
    new Dint64(0x8d86cc491ecbfe16L, 0x51776453b7e8254dL, -5, 0x1L), /* i=123 */
    new Dint64(0xdcfe013d7c8cbfdeL, 0xa32dbac46f30cfffL, -6, 0x1L), /* i=124 */
    new Dint64(0x9e75221a352ba779L, 0xa52b7ea62f2198d0L, -6, 0x1L), /* i=125 */
    new Dint64(0xbee23afc0853b6e9L, 0x289782c20df350a1L, -7, 0x1L), /* i=126 */
    new Dint64(0x0L, 0x0L, 127, 0x1L), /* i=127 */
    new Dint64(0x0L, 0x0L, 127, 0x1L), /* i=128 */
    new Dint64(0xc122451c45155104L, 0xb16137f09a002b3cL, -7, 0x0L), /* i=129 */
    new Dint64(0xa195492cc06604e6L, 0x4a18dff7cdb4ae5cL, -6, 0x0L), /* i=130 */
    new Dint64(0xe31e9760a5578c63L, 0xf9eb2f284f31c35cL, -6, 0x0L), /* i=131 */
    new Dint64(0x8a4f1f2002d46756L, 0x5be970314148c645L, -5, 0x0L), /* i=132 */
    new Dint64(0xab8ae2601e777722L, 0x3b89d7f254f8d4dL, -5, 0x0L), /* i=133 */
    new Dint64(0xcd0c3dab9ef3dd1bL, 0x13b26f298aa357c8L, -5, 0x0L), /* i=134 */
    new Dint64(0xe65b9e6eed965c36L, 0xe09f5fe2058d6006L, -5, 0x0L), /* i=135 */
    new Dint64(0x842cc5acf1d03445L, 0x1fecdfa819b96098L, -4, 0x0L), /* i=136 */
    new Dint64(0x9103dae3c2a4ec67L, 0xe0863df62ab5671aL, -4, 0x0L), /* i=137 */
    new Dint64(0xa242f01edefd6a37L, 0x469355b78dc796e3L, -4, 0x0L), /* i=138 */
    new Dint64(0xaf4ad26cbc8e5be7L, 0xe8b8b88a14ff0ceL, -4, 0x0L), /* i=139 */
    new Dint64(0xc0cbf17a071f80dcL, 0xf96ffdf76a147cccL, -4, 0x0L), /* i=140 */
    new Dint64(0xce06196a692a41fbL, 0xbe3ccc15326765fL, -4, 0x0L), /* i=141 */
    new Dint64(0xdb56446d6ad8deffL, 0xa8112e35a60e6375L, -4, 0x0L), /* i=142 */
    new Dint64(0xe8bcbc410c9b219dL, 0xaf7df76ad29e5b60L, -4, 0x0L), /* i=143 */
    new Dint64(0xf639cc185088fe5dL, 0x4066e87f2c0f7340L, -4, 0x0L), /* i=144 */
    new Dint64(0x842cc5acf1d03445L, 0x1fecdfa819b96098L, -3, 0x0L), /* i=145 */
    new Dint64(0x8b064012593d85a5L, 0x52013c7a80ad089bL, -3, 0x0L), /* i=146 */
    new Dint64(0x91eb89524e100d23L, 0x8fd3df5c52d67e7bL, -3, 0x0L), /* i=147 */
    new Dint64(0x98dcca69d27c263bL, 0x8e94203f336fc8c5L, -3, 0x0L), /* i=148 */
    new Dint64(0x9fda2d2cc9465c4fL, 0x32b9565f5355182L, -3, 0x0L), /* i=149 */
    new Dint64(0xa6e3dc4bde0e3cdbL, 0x570ff874170d2a9L, -3, 0x0L), /* i=150 */
    new Dint64(0xab9be6480c66ea9eL, 0x9ae21fd871b8d27cL, -3, 0x0L), /* i=151 */
    new Dint64(0xb2ba75f46099cf8bL, 0x2c3c2e77904afa78L, -3, 0x0L), /* i=152 */
    new Dint64(0xb9e5c83a7e8a655bL, 0xcbffe9661fe72421L, -3, 0x0L), /* i=153 */
    new Dint64(0xc11e0b2a8d1e0ddbL, 0x9a631e830fd30904L, -3, 0x0L), /* i=154 */
    new Dint64(0xc8636dcfe5e6ca0aL, 0x88e72835b3292d50L, -3, 0x0L), /* i=155 */
    new Dint64(0xcd43bc6f5d51c3e8L, 0xfbfb0e3f0fd23074L, -3, 0x0L), /* i=156 */
    new Dint64(0xd49f69e456cf1b79L, 0x5f53bd2e406e66e7L, -3, 0x0L), /* i=157 */
    new Dint64(0xdc08b985c11e9068L, 0x3b9cd767c3b1ac53L, -3, 0x0L), /* i=158 */
    new Dint64(0xe1014558bfcda3e2L, 0x35470a74be1230ecL, -3, 0x0L), /* i=159 */
    new Dint64(0xe881bf932af3dac0L, 0xc524848e3443e040L, -3, 0x0L), /* i=160 */
    new Dint64(0xed89ed86a44a01aaL, 0x11d49f96cb88317bL, -3, 0x0L), /* i=161 */
    new Dint64(0xf52224f82557a459L, 0x8dcca8d7f17fa2a9L, -3, 0x0L), /* i=162 */
    new Dint64(0xfa3a589a6f9146d8L, 0x388212895529a6fbL, -3, 0x0L), /* i=163 */
    new Dint64(0x80f572b1363487b9L, 0xf5bd0b5b3479d5f4L, -2, 0x0L), /* i=164 */
    new Dint64(0x8389c3026ac3139bL, 0x62dda9d2270fa1f4L, -2, 0x0L), /* i=165 */
    new Dint64(0x86216b3b0b17188bL, 0x163ceae88f720f1eL, -2, 0x0L), /* i=166 */
    new Dint64(0x8a0b3f79b3bc180fL, 0x49b55ea7d3730d7L, -2, 0x0L), /* i=167 */
    new Dint64(0x8cab69dcde17d2f7L, 0x3ad1aa142b94f16aL, -2, 0x0L), /* i=168 */
    new Dint64(0x8f4f0b3c44cfa2a2L, 0x586e9343c9cfdbacL, -2, 0x0L), /* i=169 */
    new Dint64(0x934b1089a6dc93c1L, 0xdf5bb3b60554e152L, -2, 0x0L), /* i=170 */
    new Dint64(0x95f783e6e49a9cfaL, 0x4a5004f3ef063313L, -2, 0x0L), /* i=171 */
    new Dint64(0x98a78f0e9ae71d85L, 0x2cdec34784707839L, -2, 0x0L), /* i=172 */
    new Dint64(0x9b5b3bb5f088b766L, 0xd878bbe3d392be25L, -2, 0x0L), /* i=173 */
    new Dint64(0x9e1293b9998c1daaL, 0x5b035eae273a855fL, -2, 0x0L), /* i=174 */
    new Dint64(0xa22c8f029cfa45a9L, 0xdb5b709e0b69e773L, -2, 0x0L), /* i=175 */
    new Dint64(0xa4ed3f9de620f666L, 0x9b5e973353638c11L, -2, 0x0L), /* i=176 */
    new Dint64(0xa7b1bf5dd4c07d4eL, 0x699db68db75e9a7fL, -2, 0x0L), /* i=177 */
    new Dint64(0xaa7a18dbdf0d44aaL, 0x604884a8dd76d08aL, -2, 0x0L), /* i=178 */
    new Dint64(0xad4656ddf6fd070cL, 0x9ea10260fe452ba2L, -2, 0x0L), /* i=179 */
    new Dint64(0xb0168457848f5f48L, 0xbb6f9fb246068d52L, -2, 0x0L), /* i=180 */
    new Dint64(0xb2eaac6a67005513L, 0xf4b716f6fec8156bL, -2, 0x0L), /* i=181 */
};

  private static final Dint64[] _LOG_INV_2_2 = {
    new Dint64(0xff015358833c47e1L, 0xbb481c8ee141695aL, -8, 0x1L), /* j=8128 */
    new Dint64(0xfb0933b732572a6dL, 0x214cca3dd1d4796aL, -8, 0x1L), /* j=8129 */
    new Dint64(0xf710f492711d9d26L, 0xfbc7b38b17b2019L, -8, 0x1L), /* j=8130 */
    new Dint64(0xf31895e84b1a6be6L, 0xb76782b9e88c84cbL, -8, 0x1L), /* j=8131 */
    new Dint64(0xef2017b6cba9cf9aL, 0x2dc85881664025b5L, -8, 0x1L), /* j=8132 */
    new Dint64(0xeb2779fbfdf96874L, 0xce4ab4e678d0ed03L, -8, 0x1L), /* j=8133 */
    new Dint64(0xe72ebcb5ed08382bL, 0xb60585f4c4bb6062L, -8, 0x1L), /* j=8134 */
    new Dint64(0xe335dfe2a3a69c2bL, 0x59bcffe9d5650564L, -8, 0x1L), /* j=8135 */
    new Dint64(0xdf3ce3802c7647cdL, 0x3602021fa93b1e18L, -8, 0x1L), /* j=8136 */
    new Dint64(0xdb43c78c91ea3e8cL, 0x9944002534d09b3dL, -8, 0x1L), /* j=8137 */
    new Dint64(0xd74a8c05de46ce3aL, 0x87aa95782311a277L, -8, 0x1L), /* j=8138 */
    new Dint64(0xd35130ea1ba18930L, 0xb88be10313a1303dL, -8, 0x1L), /* j=8139 */
    new Dint64(0xcf57b63753e14083L, 0xad54bc31433dddbaL, -8, 0x1L), /* j=8140 */
    new Dint64(0xcb5e1beb90bdfe33L, 0xe1b7d813e3f825e1L, -8, 0x1L), /* j=8141 */
    new Dint64(0xc7646204dbc0ff5eL, 0x14f8c1be7370f219L, -8, 0x1L), /* j=8142 */
    new Dint64(0xc36a88813e44ae6aL, 0xac27c5a6139cd30cL, -8, 0x1L), /* j=8143 */
    new Dint64(0xbf708f5ec1749d3cL, 0x2d23a0744e00f594L, -8, 0x1L), /* j=8144 */
    new Dint64(0xbb76769b6e4d7f5cL, 0xd235e25fb9644c31L, -8, 0x1L), /* j=8145 */
    new Dint64(0xb77c3e354d9d242bL, 0x361ee0bcb5db0449L, -8, 0x1L), /* j=8146 */
    new Dint64(0xb381e62a68027106L, 0x18660815da3d7963L, -8, 0x1L), /* j=8147 */
    new Dint64(0xaf876e78c5ed5b77L, 0x39c357b6bfdf81b5L, -8, 0x1L), /* j=8148 */
    new Dint64(0xab8cd71e6f9ee35dL, 0x5076c62c951204f6L, -8, 0x1L), /* j=8149 */
    new Dint64(0xa79220196d290d15L, 0x146244d643f7fa2bL, -8, 0x1L), /* j=8150 */
    new Dint64(0xa3974967c66edba1L, 0x62bb0f3208d9a1bbL, -8, 0x1L), /* j=8151 */
    new Dint64(0x9f9c530783244ad2L, 0x7926e92808bd580dL, -8, 0x1L), /* j=8152 */
    new Dint64(0x9ba13cf6aace496cL, 0x4819e620d5fcc068L, -8, 0x1L), /* j=8153 */
    new Dint64(0x97a6073344c2b34bL, 0xdc494943d427214eL, -8, 0x1L), /* j=8154 */
    new Dint64(0x93aab1bb58284b8bL, 0xdf0805c4161e404cL, -8, 0x1L), /* j=8155 */
    new Dint64(0x8faf3c8cebf6b6a8L, 0x2d615caaa0514c3cL, -8, 0x1L), /* j=8156 */
    new Dint64(0x8bb3a7a606f674a0L, 0x85c60c12eca0aedcL, -8, 0x1L), /* j=8157 */
    new Dint64(0x87b7f304afc0db1aL, 0x4c207a522524f8deL, -8, 0x1L), /* j=8158 */
    new Dint64(0x83bc1ea6ecc00f81L, 0x64243e02c6215a4fL, -8, 0x1L), /* j=8159 */
    new Dint64(0xff805515885e0250L, 0x435ab4da6a5bb48dL, -9, 0x1L), /* j=8160 */
    new Dint64(0xf7882d5c7832c6ccL, 0x9e06fc84b6ea5e24L, -9, 0x1L), /* j=8161 */
    new Dint64(0xef8fc61eb4b74f6eL, 0x91ab122ee427cfb5L, -9, 0x1L), /* j=8162 */
    new Dint64(0xe7971f584945efaeL, 0x5f832513e3211643L, -9, 0x1L), /* j=8163 */
    new Dint64(0xdf9e390540da5fbeL, 0x5e7b48cfeeb85aa8L, -9, 0x1L), /* j=8164 */
    new Dint64(0xd7a51321a611b0c1L, 0xb36a9f58eb4ccd08L, -9, 0x1L), /* j=8165 */
    new Dint64(0xcfabada9832a4101L, 0x3360751e43c7af35L, -9, 0x1L), /* j=8166 */
    new Dint64(0xc7b20898e203b01eL, 0x6fab78aca91193cbL, -9, 0x1L), /* j=8167 */
    new Dint64(0xbfb823ebcc1ed344L, 0xeb432409cffdad8dL, -9, 0x1L), /* j=8168 */
    new Dint64(0xb7bdff9e4a9da959L, 0x793b5acf3a336462L, -9, 0x1L), /* j=8169 */
    new Dint64(0xafc39bac66434f27L, 0xc3ea2cd93f316b34L, -9, 0x1L), /* j=8170 */
    new Dint64(0xa7c8f8122773f38dL, 0xfc679a28e9d9f212L, -9, 0x1L), /* j=8171 */
    new Dint64(0x9fce14cb9634cba6L, 0xb20f215bd3b58c61L, -9, 0x1L), /* j=8172 */
    new Dint64(0x97d2f1d4ba2c06f0L, 0xd1aacedcefe9d377L, -9, 0x1L), /* j=8173 */
    new Dint64(0x8fd78f299aa0c375L, 0xcbef6fac33691e95L, -9, 0x1L), /* j=8174 */
    new Dint64(0x87dbecc63e7b01edL, 0xe2f1775134c8da75L, -9, 0x1L), /* j=8175 */
    new Dint64(0xffc0154d588733c5L, 0x3c742a7c76356396L, -10, 0x1L), /* j=8176 */
    new Dint64(0xefc7d18dd4485b9eL, 0xca47c52b7d7ffce2L, -10, 0x1L), /* j=8177 */
    new Dint64(0xdfcf0e45fbce3e80L, 0x7e4cfbd830393b88L, -10, 0x1L), /* j=8178 */
    new Dint64(0xcfd5cb6dd9ef05ddL, 0x7370ae83f9e72748L, -10, 0x1L), /* j=8179 */
    new Dint64(0xbfdc08fd78c229b9L, 0xe6dbb624f9739782L, -10, 0x1L), /* j=8180 */
    new Dint64(0xafe1c6ece1a058ddL, 0x97fa2fd0c9dc723eL, -10, 0x1L), /* j=8181 */
    new Dint64(0x9fe705341d236102L, 0x7199cd06ae5d39b3L, -10, 0x1L), /* j=8182 */
    new Dint64(0x8febc3cb332616ffL, 0x7b6d1248c3e1fd40L, -10, 0x1L), /* j=8183 */
    new Dint64(0xffe0055455887de0L, 0x26828c92649a3a39L, -11, 0x1L), /* j=8184 */
    new Dint64(0xdfe7839214b4e8aeL, 0xda6959f7f0e01bf0L, -11, 0x1L), /* j=8185 */
    new Dint64(0xbfee023faf0c2480L, 0xb47505bfa5a03b06L, -11, 0x1L), /* j=8186 */
    new Dint64(0x9ff3814d2e4a36b2L, 0xa8740b91c95df537L, -11, 0x1L), /* j=8187 */
    new Dint64(0xfff0015535588833L, 0x3c56c598c659c2a3L, -12, 0x1L), /* j=8188 */
    new Dint64(0xbff7008ff5e0c257L, 0x379eba7e6465ff63L, -12, 0x1L), /* j=8189 */
    new Dint64(0xfff8005551558885L, 0xde026e271ee0549dL, -13, 0x1L), /* j=8190 */
    new Dint64(0x0L, 0x0L, 127, 0x1L), /* j=8191 */
    new Dint64(0x0L, 0x0L, 127, 0x1L), /* j=8192 */
    new Dint64(0xc004802401440c26L, 0xdfeb485085f6f454L, -13, 0x0L), /* j=8193 */
    new Dint64(0xa00640535a37a37aL, 0x6bc1e20eac8448b4L, -12, 0x0L), /* j=8194 */
    new Dint64(0xe00c40e4bd6e4efdL, 0xc72446cc1bf728bdL, -12, 0x0L), /* j=8195 */
    new Dint64(0x900a20f319a3e273L, 0x569b26aaa485ea5cL, -11, 0x0L), /* j=8196 */
    new Dint64(0xb00f21bbe3e388eeL, 0x5f69768284463b9bL, -11, 0x0L), /* j=8197 */
    new Dint64(0xd01522dcc4f87991L, 0x14d9d76196d8043aL, -11, 0x0L), /* j=8198 */
    new Dint64(0xf01c2465c5e61b6fL, 0x661e135f49a47c40L, -11, 0x0L), /* j=8199 */
    new Dint64(0x881213337898871eL, 0x9a31ba0cbc030353L, -10, 0x0L), /* j=8200 */
    new Dint64(0x98169478296fad41L, 0x7ad1e9c315328f7eL, -10, 0x0L), /* j=8201 */
    new Dint64(0xa81b9608fc3c50ecL, 0xf105b66ec4703edeL, -10, 0x0L), /* j=8202 */
    new Dint64(0xb82117edf8832797L, 0xd6aef30cd312169aL, -10, 0x0L), /* j=8203 */
    new Dint64(0xc8271a2f2689e388L, 0xe6e2acf8f4d4c24aL, -10, 0x0L), /* j=8204 */
    new Dint64(0xd82d9cd48f574c00L, 0x28bb3cd9f2a65fb5L, -10, 0x0L), /* j=8205 */
    new Dint64(0xe8349fe63cb35564L, 0x224a96f5a7471c46L, -10, 0x0L), /* j=8206 */
    new Dint64(0xf83c236c39273972L, 0xd462b63756c87e80L, -10, 0x0L), /* j=8207 */
    new Dint64(0x842213b747fec7bbL, 0x3ff51287882500edL, -9, 0x0L), /* j=8208 */
    new Dint64(0x8c2655faa6a1323fL, 0x1ab9679b55f78a6bL, -9, 0x0L), /* j=8209 */
    new Dint64(0x942ad8843ee1a9cdL, 0x17e4b7ac6c600cb4L, -9, 0x0L), /* j=8210 */
    new Dint64(0x9c2f9b581787cf0dL, 0xfd1a09c848e3950eL, -9, 0x0L), /* j=8211 */
    new Dint64(0xa4349e7a37bc21edL, 0x318b2ddd9d0a33b4L, -9, 0x0L), /* j=8212 */
    new Dint64(0xac39e1eea7080dbcL, 0x9dd91e52c79fd070L, -9, 0x0L), /* j=8213 */
    new Dint64(0xb43f65b96d55f55aL, 0x72de1d99ce252efdL, -9, 0x0L), /* j=8214 */
    new Dint64(0xbc4529de92f13f58L, 0xd7bd1d62ef25480dL, -9, 0x0L), /* j=8215 */
    new Dint64(0xc44b2e6220866227L, 0x7f921124f1ecb59eL, -9, 0x0L), /* j=8216 */
    new Dint64(0xcc5173481f22f03fL, 0x271ee1cd6d5cdf9eL, -9, 0x0L), /* j=8217 */
    new Dint64(0xd457f8949835a44eL, 0xfad0cc8b5faea8ccL, -9, 0x0L), /* j=8218 */
    new Dint64(0xdc5ebe4b958e6d6bL, 0xe57a0acb9d5cd4dfL, -9, 0x0L), /* j=8219 */
    new Dint64(0xe465c471215e7b41L, 0xc81bb5a8d789f444L, -9, 0x0L), /* j=8220 */
    new Dint64(0xec6d0b0946384a46L, 0x9b1beb40437575f5L, -9, 0x0L), /* j=8221 */
    new Dint64(0xf47492180f0fafefL, 0x7944509046652d99L, -9, 0x0L), /* j=8222 */
    new Dint64(0xfc7c59a18739e6e7L, 0x94e51ebff53a2f15L, -9, 0x0L), /* j=8223 */
    new Dint64(0x824230d4dd36cda4L, 0x8bbc7f765b13ebbeL, -8, 0x0L), /* j=8224 */
    new Dint64(0x8646551a5a617b6bL, 0xf61305ef7390939cL, -8, 0x0L), /* j=8225 */
    new Dint64(0x8a4a99a34159d69fL, 0x3abc32a78afd4b7bL, -8, 0x0L), /* j=8226 */
    new Dint64(0x8e4efe71988d8426L, 0x17596a598cb29436L, -8, 0x0L), /* j=8227 */
    new Dint64(0x92538387669afa1bL, 0x1c890bee9a9d743cL, -8, 0x0L), /* j=8228 */
    new Dint64(0x965828e6b25185ecL, 0xeaafbd07b543145dL, -8, 0x0L), /* j=8229 */
    new Dint64(0x9a5cee9182b15280L, 0x6517bc4112d64b17L, -8, 0x0L), /* j=8230 */
    new Dint64(0x9e61d489deeb6e53L, 0xdb94a1dfd653d3a5L, -8, 0x0L), /* j=8231 */
    new Dint64(0xa266dad1ce61d1a3L, 0x2ada01ce7ed36080L, -8, 0x0L), /* j=8232 */
    new Dint64(0xa66c016b58a7648cL, 0xd3b36c029ea7bb5dL, -8, 0x0L), /* j=8233 */
    new Dint64(0xaa71485885800538L, 0x94c529f32403828L, -8, 0x0L), /* j=8234 */
    new Dint64(0xae76af9b5ce08dfbL, 0xb6b6676248bba139L, -8, 0x0L), /* j=8235 */
    new Dint64(0xb27c3735e6eedb86L, 0x7bdd0c2a9c7a679aL, -8, 0x0L), /* j=8236 */
    new Dint64(0xb47f0724b1906935L, 0x23deb274e953a259L, -8, 0x0L), /* j=8237 */
    new Dint64(0xb884bf4697559ffaL, 0xdae7e343fa859415L, -8, 0x0L), /* j=8238 */
    new Dint64(0xbc8a97c544fdd5ebL, 0x17759bff5c717993L, -8, 0x0L), /* j=8239 */
    new Dint64(0xc09090a2c35aa070L, 0x52e7e4dde874daceL, -8, 0x0L), /* j=8240 */
    new Dint64(0xc496a9e11b6eb30cL, 0xa88971f8277a4d11L, -8, 0x0L), /* j=8241 */
    new Dint64(0xc89ce382566de587L, 0x269de85f0df92588L, -8, 0x0L), /* j=8242 */
    new Dint64(0xcca33d887dbd3a1aL, 0x180d255422c3377cL, -8, 0x0L), /* j=8243 */
    new Dint64(0xd0a9b7f59af2e3a2L, 0x46da70925ee85c05L, -8, 0x0L), /* j=8244 */
    new Dint64(0xd4b052cbb7d64bcfL, 0x37968ceafaf7b453L, -8, 0x0L), /* j=8245 */
    new Dint64(0xd8b70e0cde601954L, 0x5dfba4cfdd38a059L, -8, 0x0L), /* j=8246 */
    new Dint64(0xdcbde9bb18ba361bL, 0x4ae21abe75d5a19bL, -8, 0x0L), /* j=8247 */
    new Dint64(0xe0c4e5d8713fd576L, 0xd3bd4fd98a1e6fe5L, -8, 0x0L), /* j=8248 */
    new Dint64(0xe4cc0266f27d7a57L, 0x33cf7d5ebfb93ad3L, -8, 0x0L), /* j=8249 */
    new Dint64(0xe8d33f68a730fd7fL, 0x2743c805a4928087L, -8, 0x0L), /* j=8250 */
    new Dint64(0xecda9cdf9a4993baL, 0x5dbeb9795455a5L, -8, 0x0L), /* j=8251 */
    new Dint64(0xf0e21acdd6e7d412L, 0xb6ed80852ae6fd63L, -8, 0x0L), /* j=8252 */
    new Dint64(0xf4e9b935685dbe0bL, 0xf237cff1acb306b3L, -8, 0x0L), /* j=8253 */
    new Dint64(0xf8f178185a2ebfd9L, 0xd81648249cece4cL, -8, 0x0L), /* j=8254 */
    new Dint64(0xfcf95778b80fbc98L, 0x176cd56887ac7fe9L, -8, 0x0L), /* j=8255 */
    new Dint64(0x8080abac46f38946L, 0x662d417ced007a46L, -7, 0x0L), /* j=8256 */
};

  private static final Dint64[] T1_2 = {
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L),
    new Dint64(0x8164d1f3bc030773L, 0x7be56527bd14def5L, 0, 0x0L),
    new Dint64(0x82cd8698ac2ba1d7L, 0x3e2a475b46520bffL, 0, 0x0L),
    new Dint64(0x843a28c3acde4046L, 0x1af92eca13fd1582L, 0, 0x0L),
    new Dint64(0x85aac367cc487b14L, 0xc5c95b8c2154c1b2L, 0, 0x0L),
    new Dint64(0x871f61969e8d1010L, 0x3a1727c57b52a956L, 0, 0x0L),
    new Dint64(0x88980e8092da8527L, 0x5df8d76c98c67563L, 0, 0x0L),
    new Dint64(0x8a14d575496efd9aL, 0x80ca1d92c3680c2L, 0, 0x0L),
    new Dint64(0x8b95c1e3ea8bd6e6L, 0xfbe4628758a53c90L, 0, 0x0L),
    new Dint64(0x8d1adf5b7e5ba9e5L, 0xb4c7b4968e41ad36L, 0, 0x0L),
    new Dint64(0x8ea4398b45cd53c0L, 0x2dc0144c8783d4c6L, 0, 0x0L),
    new Dint64(0x9031dc431466b1dcL, 0x775814a8494e87e2L, 0, 0x0L),
    new Dint64(0x91c3d373ab11c336L, 0xfd6d8e0ae5ac9d8L, 0, 0x0L),
    new Dint64(0x935a2b2f13e6e92bL, 0xd339940e9d924ee7L, 0, 0x0L),
    new Dint64(0x94f4efa8fef70961L, 0x2e8afad12551de54L, 0, 0x0L),
    new Dint64(0x96942d3720185a00L, 0x48ea9b683a9c22c5L, 0, 0x0L),
    new Dint64(0x9837f0518db8a96fL, 0x46ad23182e42f6f6L, 0, 0x0L),
    new Dint64(0x99e0459320b7fa64L, 0xe43086cb34b5fcafL, 0, 0x0L),
    new Dint64(0x9b8d39b9d54e5538L, 0xa2a817a2a3cc3f1fL, 0, 0x0L),
    new Dint64(0x9d3ed9a72cffb750L, 0xde494cf050e99b0bL, 0, 0x0L),
    new Dint64(0x9ef5326091a111adL, 0xa0911f09ebb9fdd1L, 0, 0x0L),
    new Dint64(0xa0b0510fb9714fc2L, 0x192dc79edb0fd9a9L, 0, 0x0L),
    new Dint64(0xa27043030c496818L, 0x9b7a04ef80cfdea8L, 0, 0x0L),
    new Dint64(0xa43515ae09e6809eL, 0xd1db4831781e1efL, 0, 0x0L),
    new Dint64(0xa5fed6a9b15138eaL, 0x1cbd7f621710701bL, 0, 0x0L),
    new Dint64(0xa7cd93b4e9653569L, 0x9ec5b4d5039f72afL, 0, 0x0L),
    new Dint64(0xa9a15ab4ea7c0ef8L, 0x541e24ec3531fa73L, 0, 0x0L),
    new Dint64(0xab7a39b5a93ed337L, 0x658023b2759e0079L, 0, 0x0L),
    new Dint64(0xad583eea42a14ac6L, 0x4980a8c8f59a2ec4L, 0, 0x0L),
    new Dint64(0xaf3b78ad690a4374L, 0xdf26101ccbb35033L, 0, 0x0L),
    new Dint64(0xb123f581d2ac258fL, 0x87d037e96d215d8eL, 0, 0x0L),
    new Dint64(0xb311c412a9112489L, 0x3ecf14dc798a519cL, 0, 0x0L),
    new Dint64(0xb504f333f9de6484L, 0x597d89b3754abe9fL, 0, 0x0L),
    new Dint64(0xb6fd91e328d17791L, 0x7165f0ddd541a5aL, 0, 0x0L),
    new Dint64(0xb8fbaf4762fb9ee9L, 0x1b879778566b65a2L, 0, 0x0L),
    new Dint64(0xbaff5ab2133e45fbL, 0x74d519d24593838cL, 0, 0x0L),
    new Dint64(0xbd08a39f580c36beL, 0xa8811fb66d0faf7aL, 0, 0x0L),
    new Dint64(0xbf1799b67a731082L, 0xe815d0abcbf0b851L, 0, 0x0L),
    new Dint64(0xc12c4cca66709456L, 0x7c457d59a50087b5L, 0, 0x0L),
    new Dint64(0xc346ccda24976407L, 0x20ec856128b83a42L, 0, 0x0L),
    new Dint64(0xc5672a115506daddL, 0x3e2ad0c964dd9f37L, 0, 0x0L),
    new Dint64(0xc78d74c8abb9b15cL, 0xc13a2e3976c0277eL, 0, 0x0L),
    new Dint64(0xc9b9bd866e2f27a2L, 0x80e1f92a0511697eL, 0, 0x0L),
    new Dint64(0xcbec14fef2727c5cL, 0xf4907c8f45ebf6ddL, 0, 0x0L),
    new Dint64(0xce248c151f8480e3L, 0xe235838f95f2c6edL, 0, 0x0L),
    new Dint64(0xd06333daef2b2594L, 0xd6d45c6559a4d502L, 0, 0x0L),
    new Dint64(0xd2a81d91f12ae45aL, 0x12248e57c3de4028L, 0, 0x0L),
    new Dint64(0xd4f35aabcfedfa1fL, 0x5921deffa6262c5bL, 0, 0x0L),
    new Dint64(0xd744fccad69d6af4L, 0x39a68bb9902d3fdeL, 0, 0x0L),
    new Dint64(0xd99d15c278afd7b5L, 0xfe873deca3e12bacL, 0, 0x0L),
    new Dint64(0xdbfbb797daf23755L, 0x3d840d5a9e29aa64L, 0, 0x0L),
    new Dint64(0xde60f4825e0e9123L, 0xdd07a2d9e8466859L, 0, 0x0L),
    new Dint64(0xe0ccdeec2a94e111L, 0x65895048dd333caL, 0, 0x0L),
    new Dint64(0xe33f8972be8a5a51L, 0x9bfe90795980eedL, 0, 0x0L),
    new Dint64(0xe5b906e77c8348a8L, 0x1e5e8f4a4edbb0edL, 0, 0x0L),
    new Dint64(0xe8396a503c4bdc68L, 0x791790d0ac70c7deL, 0, 0x0L),
    new Dint64(0xeac0c6e7dd24392eL, 0xd02d75b3706e54fbL, 0, 0x0L),
    new Dint64(0xed4f301ed9942b84L, 0x600d2db6a64bfb12L, 0, 0x0L),
    new Dint64(0xefe4b99bdcdaf5cbL, 0x46561cf6948db913L, 0, 0x0L),
    new Dint64(0xf281773c59ffb139L, 0xe8980a9cc8f47a4bL, 0, 0x0L),
    new Dint64(0xf5257d152486cc2cL, 0x7b9d0c7aed980fc3L, 0, 0x0L),
    new Dint64(0xf7d0df730ad13bb8L, 0xfe90d496d60fb6ebL, 0, 0x0L),
    new Dint64(0xfa83b2db722a033aL, 0x7c25bb14315d7fcdL, 0, 0x0L),
    new Dint64(0xfd3e0c0cf486c174L, 0x853f3a5931e0ee03L, 0, 0x0L),
};

  private static final Dint64[] T2_2 = {
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L),
    new Dint64(0x80058baf7fee3b5dL, 0x1c718b38e549cb93L, 0, 0x0L),
    new Dint64(0x800b179c82028fd0L, 0x945e54e2ae18f2f0L, 0, 0x0L),
    new Dint64(0x8010a3c708e73282L, 0x2b96d62d51c15a07L, 0, 0x0L),
    new Dint64(0x8016302f17467628L, 0x3690dfe44d11d008L, 0, 0x0L),
    new Dint64(0x801bbcd4afcacb08L, 0xe23a986bd3e626f0L, 0, 0x0L),
    new Dint64(0x802149b7d51ebefbL, 0x7bdbadbc888aeb29L, 0, 0x0L),
    new Dint64(0x8026d6d889ecfd69L, 0xb904bbfb40d3a2b7L, 0, 0x0L),
    new Dint64(0x802c6436d0e04f50L, 0xff8ce94a6797b3ceL, 0, 0x0L),
    new Dint64(0x8031f1d2aca39b43L, 0xad9db772901d96b6L, 0, 0x0L),
    new Dint64(0x80377fac1fe1e56aL, 0x61cd0bffd7cfc683L, 0, 0x0L),
    new Dint64(0x803d0dc32d464f85L, 0x43456f71b96affd4L, 0, 0x0L),
    new Dint64(0x80429c17d77c18edL, 0x49fc841afba9c3c6L, 0, 0x0L),
    new Dint64(0x80482aaa212e9e95L, 0x86f7b54f6c45c85eL, 0, 0x0L),
    new Dint64(0x804db97a0d095b0cL, 0x6c9f1f7d1efcfe68L, 0, 0x0L),
    new Dint64(0x805348879db7e67dL, 0x171eb1ceef1d1f28L, 0, 0x0L),
    new Dint64(0x8058d7d2d5e5f6b0L, 0x94d589f608ee4aa2L, 0, 0x0L),
    new Dint64(0x805e675bb83f5f0fL, 0x2ed38ab8472b2144L, 0, 0x0L),
    new Dint64(0x8063f722477010a1L, 0xb1652de1378af1a1L, 0, 0x0L),
    new Dint64(0x8069872686241a12L, 0xb4ad9233a0390cadL, 0, 0x0L),
    new Dint64(0x806f17687707a7afL, 0xe54ec5f966eb1872L, 0, 0x0L),
    new Dint64(0x8074a7e81cc7036bL, 0x4d204ecfc11f4aabL, 0, 0x0L),
    new Dint64(0x807a38a57a0e94dcL, 0x9bf3ef4d9be2d1e4L, 0, 0x0L),
    new Dint64(0x807fc9a0918ae142L, 0x7068ab2230585d13L, 0, 0x0L),
    new Dint64(0x80855ad965e88b83L, 0xa0cc0a49c10ea66bL, 0, 0x0L),
    new Dint64(0x808aec4ff9d45430L, 0x84099bf6830f2768L, 0, 0x0L),
    new Dint64(0x80907e044ffb1984L, 0x3aa8b9cbbc65a8abL, 0, 0x0L),
    new Dint64(0x80960ff66b09d765L, 0xf7d88c0928ba3947L, 0, 0x0L),
    new Dint64(0x809ba2264dada76aL, 0x4a8a4f44bb703db6L, 0, 0x0L),
    new Dint64(0x80a13493fa93c0d4L, 0x6699dc50dd96b774L, 0, 0x0L),
    new Dint64(0x80a6c73f74697897L, 0x6e0472ed4ccfa2e0L, 0, 0x0L),
    new Dint64(0x80ac5a28bddc4157L, 0xba2dc7e0c72e51baL, 0, 0x0L),
    new Dint64(0x80b1ed4fd999ab6cL, 0x25335719b6e6fd20L, 0, 0x0L),
    new Dint64(0x80b780b4ca4f64dfL, 0x534dfa7417846aa4L, 0, 0x0L),
    new Dint64(0x80bd145792ab3970L, 0xfc41c5c2d5336cccL, 0, 0x0L),
    new Dint64(0x80c2a838355b1297L, 0x34dc28baed8f3fdeL, 0, 0x0L),
    new Dint64(0x80c83c56b50cf77fL, 0xb880575ea03548c1L, 0, 0x0L),
    new Dint64(0x80cdd0b3146f0d11L, 0x32c1f98704428c71L, 0, 0x0L),
    new Dint64(0x80d3654d562f95ecL, 0x890e222a5eb95372L, 0, 0x0L),
    new Dint64(0x80d8fa257cfcf26eL, 0x24628efd9ca9d59bL, 0, 0x0L),
    new Dint64(0x80de8f3b8b85a0afL, 0x3b13310f5ad57fb1L, 0, 0x0L),
    new Dint64(0x80e4248f84783c87L, 0x1a9dfefaeb616564L, 0, 0x0L),
    new Dint64(0x80e9ba216a837f8cL, 0x718d1151d109bf98L, 0, 0x0L),
    new Dint64(0x80ef4ff140564116L, 0x996709da2e25f04cL, 0, 0x0L),
    new Dint64(0x80f4e5ff089f763eL, 0xe0adc640acaa6b0bL, 0, 0x0L),
    new Dint64(0x80fa7c4ac60e31e1L, 0xd4eb5edc6b341283L, 0, 0x0L),
    new Dint64(0x810012d47b51a4a0L, 0x8ccd7223820719e3L, 0, 0x0L),
    new Dint64(0x8105a99c2b191ce1L, 0xf24ebd6eb9ca4292L, 0, 0x0L),
    new Dint64(0x810b40a1d81406d4L, 0xcef03ab14a66550L, 0, 0x0L),
    new Dint64(0x8110d7e584f1ec6dL, 0x4bf94297d1519822L, 0, 0x0L),
    new Dint64(0x81166f673462756dL, 0xd0d8372f966cf15eL, 0, 0x0L),
    new Dint64(0x811c0726e9156760L, 0xb97931db7b7be2ecL, 0, 0x0L),
    new Dint64(0x81219f24a5baa59dL, 0x6abd3b0eab9c7048L, 0, 0x0L),
    new Dint64(0x812737606d023148L, 0xdaf888e96508151aL, 0, 0x0L),
    new Dint64(0x812ccfda419c2956L, 0xdc8046821f46122eL, 0, 0x0L),
    new Dint64(0x813268922638ca8bL, 0x6846ad73a8d9027fL, 0, 0x0L),
    new Dint64(0x813801881d886f7bL, 0xe885724f14131287L, 0, 0x0L),
    new Dint64(0x813d9abc2a3b9090L, 0x83768490519df895L, 0, 0x0L),
    new Dint64(0x8143342e4f02c405L, 0x661b22b45e25de18L, 0, 0x0L),
    new Dint64(0x8148cdde8e8ebdecL, 0xf11430fef78c6eeL, 0, 0x0L),
    new Dint64(0x814e67cceb90502cL, 0x99775205944eadc4L, 0, 0x0L),
    new Dint64(0x815401f968b86a87L, 0x7de463a40d18261L, 0, 0x0L),
    new Dint64(0x81599c6408b81a94L, 0x8f4a0b6748df7960L, 0, 0x0L),
    new Dint64(0x815f370cce408bc8L, 0xe2404468cfe5ab9fL, 0, 0x0L),
};

  private static final Dint64[] P_2 = {
    new Dint64(0xe38e3954a09e560eL, 0x0L, -4, 0x0L),
    new Dint64(0x800000399d09d767L, 0x0L, -3, 0x1L),
    new Dint64(0x9249249249248676L, 0x0L, -3, 0x0L),
    new Dint64(0xaaaaaaaaaaaa9fddL, 0x0L, -3, 0x1L),
    new Dint64(0xccccccccccccccccL, 0xcccdc5fe0ef93b8dL, -3, 0x0L),
    new Dint64(0x8000000000000000L, 0x600135b960d8L, -2, 0x1L),
    new Dint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaa77b5eL, -2, 0x0L),
    new Dint64(0xffffffffffffffffL, 0xfffffffffffe33caL, -2, 0x1L),
    new Dint64(0x8000000000000000L, 0x0L, 0, 0x0L),
};

  private static final Dint64[] Q_2 = {
    new Dint64(0xd00d00cd98416862L, 0x0L, -13, 0x0L),
    new Dint64(0xb60b60b932146a54L, 0x0L, -10, 0x0L),
    new Dint64(0x8888888888888897L, 0x0L, -7, 0x0L),
    new Dint64(0xaaaaaaaaaaaaaaa3L, 0x0L, -5, 0x0L),
    new Dint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaa6a1e0776aeL, -3, 0x0L),
    new Dint64(0x8000000000000000L, 0xc06f3cd29L, -1, 0x0L),
    new Dint64(0x8000000000000000L, 0x88L, 0, 0x0L),
    new Dint64(0xffffffffffffffffL, 0xffffffffffffffd0L, -1, 0x0L),
};

  private static final Qint64 M_ONE_Q = new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x1L);

  private static final Qint64 LOG2_Q = new Qint64(
      0xb17217f7d1cf79abL, 0xc9e3b39803f2f6afL, 0x40f343267298b62dL, 0x8a0d175b8baafa2bL, -1, 0x0L);

  private static final Qint64 LOG2_INV_Q = new Qint64(
      0xb8aa3b295c17f0bcL, 0x0L, 0x0L, 0x0L, 12, 0x0L);

  private static final Qint64 ZERO_Q = new Qint64(0x0L, 0x0L, 0x0L, 0x0L, 0, 0x0L);

  private static final Qint64[] _INVERSE_3_1 = {
    new Qint64(0xb500000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=90 */
    new Qint64(0xb300000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=91 */
    new Qint64(0xb100000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=92 */
    new Qint64(0xaf00000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=93 */
    new Qint64(0xad80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=94 */
    new Qint64(0xab80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=95 */
    new Qint64(0xaa00000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=96 */
    new Qint64(0xa800000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=97 */
    new Qint64(0xa680000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=98 */
    new Qint64(0xa480000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=99 */
    new Qint64(0xa300000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=100 */
    new Qint64(0xa180000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=101 */
    new Qint64(0xa000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=102 */
    new Qint64(0x9e80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=103 */
    new Qint64(0x9d00000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=104 */
    new Qint64(0x9b80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=105 */
    new Qint64(0x9a00000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=106 */
    new Qint64(0x9880000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=107 */
    new Qint64(0x9700000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=108 */
    new Qint64(0x9580000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=109 */
    new Qint64(0x9480000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=110 */
    new Qint64(0x9300000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=111 */
    new Qint64(0x9180000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=112 */
    new Qint64(0x9080000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=113 */
    new Qint64(0x8f00000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=114 */
    new Qint64(0x8e00000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=115 */
    new Qint64(0x8c80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=116 */
    new Qint64(0x8b80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=117 */
    new Qint64(0x8a80000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=118 */
    new Qint64(0x8900000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=119 */
    new Qint64(0x8800000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=120 */
    new Qint64(0x8700000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=121 */
    new Qint64(0x8580000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=122 */
    new Qint64(0x8480000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=123 */
    new Qint64(0x8380000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=124 */
    new Qint64(0x8280000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=125 */
    new Qint64(0x8180000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=126 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=127 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* i=128 */
    new Qint64(0xfd00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=129 */
    new Qint64(0xfb00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=130 */
    new Qint64(0xf900000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=131 */
    new Qint64(0xf780000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=132 */
    new Qint64(0xf580000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=133 */
    new Qint64(0xf380000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=134 */
    new Qint64(0xf200000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=135 */
    new Qint64(0xf000000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=136 */
    new Qint64(0xee80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=137 */
    new Qint64(0xec80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=138 */
    new Qint64(0xeb00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=139 */
    new Qint64(0xe900000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=140 */
    new Qint64(0xe780000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=141 */
    new Qint64(0xe600000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=142 */
    new Qint64(0xe480000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=143 */
    new Qint64(0xe300000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=144 */
    new Qint64(0xe100000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=145 */
    new Qint64(0xdf80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=146 */
    new Qint64(0xde00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=147 */
    new Qint64(0xdc80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=148 */
    new Qint64(0xdb00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=149 */
    new Qint64(0xd980000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=150 */
    new Qint64(0xd880000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=151 */
    new Qint64(0xd700000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=152 */
    new Qint64(0xd580000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=153 */
    new Qint64(0xd400000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=154 */
    new Qint64(0xd280000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=155 */
    new Qint64(0xd180000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=156 */
    new Qint64(0xd000000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=157 */
    new Qint64(0xce80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=158 */
    new Qint64(0xcd80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=159 */
    new Qint64(0xcc00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=160 */
    new Qint64(0xcb00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=161 */
    new Qint64(0xc980000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=162 */
    new Qint64(0xc880000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=163 */
    new Qint64(0xc700000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=164 */
    new Qint64(0xc600000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=165 */
    new Qint64(0xc500000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=166 */
    new Qint64(0xc380000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=167 */
    new Qint64(0xc280000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=168 */
    new Qint64(0xc180000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=169 */
    new Qint64(0xc000000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=170 */
    new Qint64(0xbf00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=171 */
    new Qint64(0xbe00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=172 */
    new Qint64(0xbd00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=173 */
    new Qint64(0xbc00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=174 */
    new Qint64(0xba80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=175 */
    new Qint64(0xb980000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=176 */
    new Qint64(0xb880000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=177 */
    new Qint64(0xb780000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=178 */
    new Qint64(0xb680000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=179 */
    new Qint64(0xb580000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=180 */
    new Qint64(0xb480000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* i=181 */
};

  private static final Qint64[] _INVERSE_3_2 = {
    new Qint64(0x8100000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8128 */
    new Qint64(0x80fc000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8129 */
    new Qint64(0x80f8000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8130 */
    new Qint64(0x80f4000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8131 */
    new Qint64(0x80f0000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8132 */
    new Qint64(0x80ec000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8133 */
    new Qint64(0x80e8000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8134 */
    new Qint64(0x80e4000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8135 */
    new Qint64(0x80e0000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8136 */
    new Qint64(0x80dc000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8137 */
    new Qint64(0x80d8000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8138 */
   new Qint64(0x80d4000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8139 */
    new Qint64(0x80d0000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8140 */
    new Qint64(0x80cc000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8141 */
    new Qint64(0x80c8000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8142 */
    new Qint64(0x80c4000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8143 */
    new Qint64(0x80c0000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8144 */
    new Qint64(0x80bc000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8145 */
    new Qint64(0x80b8000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8146 */
    new Qint64(0x80b4000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8147 */
    new Qint64(0x80b0000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8148 */
    new Qint64(0x80ac000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8149 */
    new Qint64(0x80a8000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8150 */
    new Qint64(0x80a4000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8151 */
    new Qint64(0x80a0000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8152 */
    new Qint64(0x809c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8153 */
    new Qint64(0x8098000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8154 */
    new Qint64(0x8094000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8155 */
    new Qint64(0x8090000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8156 */
    new Qint64(0x808c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8157 */
    new Qint64(0x8088000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8158 */
    new Qint64(0x8084000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8159 */
    new Qint64(0x8080000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8160 */
    new Qint64(0x807c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8161 */
    new Qint64(0x8078000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8162 */
    new Qint64(0x8074000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8163 */
    new Qint64(0x8070000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8164 */
    new Qint64(0x806c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8165 */
    new Qint64(0x8068000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8166 */
    new Qint64(0x8064000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8167 */
    new Qint64(0x8060000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8168 */
    new Qint64(0x805c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8169 */
    new Qint64(0x8058000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8170 */
    new Qint64(0x8054000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8171 */
    new Qint64(0x8050000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8172 */
    new Qint64(0x804c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8173 */
    new Qint64(0x8048000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8174 */
    new Qint64(0x8044000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8175 */
    new Qint64(0x8040000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8176 */
    new Qint64(0x803c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8177 */
    new Qint64(0x8038000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8178 */
    new Qint64(0x8034000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8179 */
    new Qint64(0x8030000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8180 */
    new Qint64(0x802c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8181 */
    new Qint64(0x8028000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8182 */
    new Qint64(0x8024000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8183 */
    new Qint64(0x8020000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8184 */
    new Qint64(0x801c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8185 */
    new Qint64(0x8018000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8186 */
    new Qint64(0x8014000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8187 */
    new Qint64(0x8010000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8188 */
    new Qint64(0x800c000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8189 */
    new Qint64(0x8008000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8190 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8191 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* j=8192 */
    new Qint64(0xfff4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8193 */
    new Qint64(0xffec000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8194 */
    new Qint64(0xffe4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8195 */
    new Qint64(0xffdc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8196 */
    new Qint64(0xffd4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8197 */
    new Qint64(0xffcc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8198 */
    new Qint64(0xffc4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8199 */
    new Qint64(0xffbc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8200 */
    new Qint64(0xffb4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8201 */
    new Qint64(0xffac000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8202 */
    new Qint64(0xffa4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8203 */
    new Qint64(0xff9c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8204 */
    new Qint64(0xff94000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8205 */
    new Qint64(0xff8c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8206 */
    new Qint64(0xff84000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8207 */
    new Qint64(0xff7c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8208 */
    new Qint64(0xff74000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8209 */
    new Qint64(0xff6c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8210 */
    new Qint64(0xff64000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8211 */
    new Qint64(0xff5c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8212 */
    new Qint64(0xff54000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8213 */
    new Qint64(0xff4c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8214 */
    new Qint64(0xff44000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8215 */
    new Qint64(0xff3c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8216 */
    new Qint64(0xff34000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8217 */
    new Qint64(0xff2c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8218 */
    new Qint64(0xff24000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8219 */
    new Qint64(0xff1c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8220 */
    new Qint64(0xff14000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8221 */
    new Qint64(0xff0c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8222 */
    new Qint64(0xff04000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8223 */
    new Qint64(0xfefc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8224 */
    new Qint64(0xfef4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8225 */
    new Qint64(0xfeec000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8226 */
    new Qint64(0xfee4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8227 */
    new Qint64(0xfedc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8228 */
    new Qint64(0xfed4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8229 */
    new Qint64(0xfecc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8230 */
    new Qint64(0xfec4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8231 */
    new Qint64(0xfebc000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8232 */
    new Qint64(0xfeb4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8233 */
    new Qint64(0xfeac000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8234 */
    new Qint64(0xfea4000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8235 */
    new Qint64(0xfe9c000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8236 */
    new Qint64(0xfe98000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8237 */
    new Qint64(0xfe90000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8238 */
    new Qint64(0xfe88000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8239 */
    new Qint64(0xfe80000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8240 */
    new Qint64(0xfe78000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8241 */
    new Qint64(0xfe70000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8242 */
    new Qint64(0xfe68000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8243 */
    new Qint64(0xfe60000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8244 */
    new Qint64(0xfe58000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8245 */
    new Qint64(0xfe50000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8246 */
    new Qint64(0xfe48000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8247 */
    new Qint64(0xfe40000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8248 */
    new Qint64(0xfe38000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8249 */
    new Qint64(0xfe30000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8250 */
    new Qint64(0xfe28000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8251 */
    new Qint64(0xfe20000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8252 */
    new Qint64(0xfe18000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8253 */
    new Qint64(0xfe10000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8254 */
    new Qint64(0xfe08000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8255 */
    new Qint64(0xfe00000000000000L, 0x0L, 0x0L, 0x0L, -1, 0x0L), /* j=8256 */
};

  private static final Qint64[] _LOG_INV_3_1 = {
    new Qint64(0xb1641795ce3ca97bL, 0x7af915300e517391L, 0x362aee92bfa25a80L, 0x1646679ea2568305L, -2, 0x1L), /* i=90 */
    new Qint64(0xabb3b8ba2ad362a4L, 0xd5b6506cc17a01f1L, 0x706866327ef7c050L, 0x628f2d55f109eac9L, -2, 0x1L), /* i=91 */
    new Qint64(0xa5f2fcabbbc506daL, 0x64ca4fb7ec323d72L, 0xa68b0ce7a5e0a7eaL, 0xd6cad0f0b6c847efL, -2, 0x1L), /* i=92 */
    new Qint64(0xa0218434353f1de8L, 0x6093efa632530ac8L, 0x304fb3b2345b41a9L, 0xe440d92b32eac488L, -2, 0x1L), /* i=93 */
    new Qint64(0x9bb93315fec2d792L, 0xa7589fba0865790dL, 0x82b75e91fcdfa14eL, 0x98d12c3138e33333L, -2, 0x1L), /* i=94 */
    new Qint64(0x95c981d5c4e924edL, 0x29404f5aa577d6b1L, 0xba0ea3f2ae1e1d07L, 0x758e4ab7f718ea9fL, -2, 0x1L), /* i=95 */
    new Qint64(0x914a0fde7bcb2d12L, 0x1429ed3aea197a5dL, 0x355a6f4f0ec5ce8fL, 0xc4eca5fff76cbf20L, -2, 0x1L), /* i=96 */
    new Qint64(0x8b3ae55d5d30701cL, 0xe63eab883717047eL, 0xcfa09487833ea69L, 0x8791b8732b281e2fL, -2, 0x1L), /* i=97 */
    new Qint64(0x86a35abcd5ba5903L, 0xec81c3cbd925cccfL, 0x6a2f869f2c41ea0aL, 0x261913d1bbc49fafL, -2, 0x1L), /* i=98 */
    new Qint64(0x8073622d6a80e634L, 0x6a97009015316070L, 0x9f1d0d49f7cf8122L, 0xc0baf08f2dd617cfL, -2, 0x1L), /* i=99 */
    new Qint64(0xf7856e5ee2c9b290L, 0xc6f2a1b84190a7d6L, 0x94261a0e91f0e8f2L, 0x960d286867d7da8dL, -3, 0x1L), /* i=100 */
    new Qint64(0xee0de5055f63eb06L, 0x98a33316df83ba56L, 0xa28f0225cea42f20L, 0xc8c87785c07b059dL, -3, 0x1L), /* i=101 */
    new Qint64(0xe47fbe3cd4d10d61L, 0x2ec0f797fdcd1257L, 0x1d97a9d046b706c5L, 0xc3c4cfd592ff1d1bL, -3, 0x1L), /* i=102 */
    new Qint64(0xdada8cf47dad2374L, 0x4ffb833c3409ee78L, 0x3713df786be7d79fL, 0xdfdccbdb9cc5e4fcL, -3, 0x1L), /* i=103 */
    new Qint64(0xd11de0ff15ab18c9L, 0xb88d83d4cc613f1fL, 0x8db36c5996f30e02L, 0x67caaac70b1e203fL, -3, 0x1L), /* i=104 */
    new Qint64(0xc74946f4436a0552L, 0xc4f5cb531201c0d0L, 0xe377c62941756ddaL, 0xbf31ff26e7952aa7L, -3, 0x1L), /* i=105 */
    new Qint64(0xbd5c481086c848dfL, 0x1b596b503040323fL, 0xf0a4a6c408595abbL, 0x18b2e81de5a7413dL, -3, 0x1L), /* i=106 */
    new Qint64(0xb3566a13956a86f6L, 0xff1b1e1574d9fd53L, 0xd790e4993973cb21L, 0xb570c1978023c83L, -3, 0x1L), /* i=107 */
    new Qint64(0xa9372f1d0da1bd17L, 0x200eb71e58cd36deL, 0x631daa222aa1cc5eL, 0xc53df36a99bd161eL, -3, 0x1L), /* i=108 */
    new Qint64(0x9efe158766314e54L, 0xc571827efe892fc4L, 0x5fb87ab4717a500L, 0x73890974d65b5cfdL, -3, 0x1L), /* i=109 */
    new Qint64(0x981eb8c723fe97f4L, 0xa31c134fb702d431L, 0xa1267633d7a950a6L, 0xf1d435478be2e98dL, -3, 0x1L), /* i=110 */
    new Qint64(0x8db956a97b3d0148L, 0x3023472cd739f9deL, 0x3f642654cbb04a9bL, 0x501b839196278b37L, -3, 0x1L), /* i=111 */
    new Qint64(0x8338a89652cb7150L, 0xc647eb86498c2ce1L, 0x6fdaaacd24ed99fcL, 0x73619b3ac0a2580eL, -3, 0x1L), /* i=112 */
    new Qint64(0xf85186008b15330bL, 0xe64b8b775997898dL, 0x3474d3375b525967L, 0x1851f0a96f698496L, -4, 0x1L), /* i=113 */
    new Qint64(0xe2f2a47ade3a18aeL, 0xb0bf7c0b0d8bb4ecL, 0xb357c6e1bb965608L, 0xb1f3fb65de3326acL, -4, 0x1L), /* i=114 */
    new Qint64(0xd49369d256ab1b28L, 0x5e9154e1d5263cd4L, 0xfb3f11769cc680efL, 0x5588fd21488d3117L, -4, 0x1L), /* i=115 */
    new Qint64(0xbed3b36bd8966422L, 0x240644d7d9ed08aeL, 0x8bd331e0f0163a57L, 0xad0c8b665d0ba662L, -4, 0x1L), /* i=116 */
    new Qint64(0xb032c549ba861d8eL, 0xf74e27bc92ce336aL, 0x476c441f8cbfb247L, 0xb421b4cceddb6decL, -4, 0x1L), /* i=117 */
    new Qint64(0xa176e5f5323781ddL, 0xd4f935996c92e8cbL, 0xb1ed0cd9e5eb16c4L, 0xd070037b7a65dbb6L, -4, 0x1L), /* i=118 */
    new Qint64(0x8b29b7751bd70743L, 0x12e0b9ee992f236dL, 0x21482d3342d35569L, 0xbf365c4132567724L, -4, 0x1L), /* i=119 */
    new Qint64(0xf85186008b15330bL, 0xe64b8b775997898dL, 0x3474d3375b525967L, 0x1851f0a96f698496L, -5, 0x1L), /* i=120 */
    new Qint64(0xda16eb88cb8df614L, 0x68a63ecfb66e94abL, 0xce26340fc53dc9e7L, 0x7778bea9e4485112L, -5, 0x1L), /* i=121 */
    new Qint64(0xac52dd7e4726a463L, 0x547a963a91bb3012L, 0x6146c24c8704d774L, 0x9181c8e24fdd9bf3L, -5, 0x1L), /* i=122 */
    new Qint64(0x8d86cc491ecbfe16L, 0x51776453b7e8254dL, 0x1fe3399d400c4228L, 0x939ae69b03a586bcL, -5, 0x1L), /* i=123 */
    new Qint64(0xdcfe013d7c8cbfdeL, 0xa32dbac46f30cffeL, 0xda998fa29b9bb98bL, 0x121d35ae45b4e2e1L, -6, 0x1L), /* i=124 */
    new Qint64(0x9e75221a352ba779L, 0xa52b7ea62f2198d0L, 0x797189a4ceffb772L, 0x67b1205aea8ed5f1L, -6, 0x1L), /* i=125 */
    new Qint64(0xbee23afc0853b6e9L, 0x289782c20df350a1L, 0x4943001d3f0647d1L, 0x907db46b91a9be11L, -7, 0x1L), /* i=126 */
    new Qint64(0x0L, 0x0L, 0x0L, 0x0L, 255, 0x1L), /* i=127 */
    new Qint64(0x0L, 0x0L, 0x0L, 0x0L, 255, 0x1L), /* i=128 */
    new Qint64(0xc122451c45155104L, 0xb16137f09a002b3cL, 0x114425f06f494d45L, 0xe2fa6c6fbca3a43aL, -7, 0x0L), /* i=129 */
    new Qint64(0xa195492cc06604e6L, 0x4a18dff7cdb4ae5cL, 0x1b120e15ca3dceb7L, 0x62148cc46c40db43L, -6, 0x0L), /* i=130 */
    new Qint64(0xe31e9760a5578c63L, 0xf9eb2f284f31c35cL, 0x739276a47bc0067fL, 0x13d3be097b445a02L, -6, 0x0L), /* i=131 */
    new Qint64(0x8a4f1f2002d46756L, 0x5be970314148c644L, 0xd7177b23dafc1e78L, 0x62496477de5b9b70L, -5, 0x0L), /* i=132 */
    new Qint64(0xab8ae2601e777722L, 0x3b89d7f254f8d4dL, 0x7fea49aded4406bdL, 0xbd68cbc383b2b959L, -5, 0x0L), /* i=133 */
    new Qint64(0xcd0c3dab9ef3dd1bL, 0x13b26f298aa357c8L, 0x307b8ee396d79ef6L, 0x801ddc72263552e3L, -5, 0x0L), /* i=134 */
    new Qint64(0xe65b9e6eed965c36L, 0xe09f5fe2058d6005L, 0xb58f9a65c1043b41L, 0xe2faca238c300fa9L, -5, 0x0L), /* i=135 */
    new Qint64(0x842cc5acf1d03445L, 0x1fecdfa819b96097L, 0xe362c7f8dd18e5cbL, 0x2c885ebb0a67bc24L, -4, 0x0L), /* i=136 */
    new Qint64(0x9103dae3c2a4ec67L, 0xe0863df62ab56719L, 0xe0c7d4d12db021b8L, 0x55776e4da79f922aL, -4, 0x0L), /* i=137 */
    new Qint64(0xa242f01edefd6a37L, 0x469355b78dc796e2L, 0xb3c575a2031956ecL, 0x395fd54d8695b022L, -4, 0x0L), /* i=138 */
    new Qint64(0xaf4ad26cbc8e5be7L, 0xe8b8b88a14ff0cdL, 0x9ad6b7f2deaa8ae6L, 0x47e37e4affafd5a1L, -4, 0x0L), /* i=139 */
    new Qint64(0xc0cbf17a071f80dcL, 0xf96ffdf76a147cccL, 0x3700761ca4fb5278L, 0xf6961709acabf991L, -4, 0x0L), /* i=140 */
    new Qint64(0xce06196a692a41fbL, 0xbe3ccc15326765fL, 0x733187c6d6f39bb8L, 0xe3f54edc6e2e0350L, -4, 0x0L), /* i=141 */
    new Qint64(0xdb56446d6ad8deffL, 0xa8112e35a60e6374L, 0xdd62571dda9ce602L, 0xe44b58c81d361f18L, -4, 0x0L), /* i=142 */
    new Qint64(0xe8bcbc410c9b219dL, 0xaf7df76ad29e5b5fL, 0xe6a09f8913389334L, 0x7de7a2535752b786L, -4, 0x0L), /* i=143 */
    new Qint64(0xf639cc185088fe5dL, 0x4066e87f2c0f733fL, 0x8296a39b87519924L, 0x5a18333a98b0adbcL, -4, 0x0L), /* i=144 */
    new Qint64(0x842cc5acf1d03445L, 0x1fecdfa819b96097L, 0xe362c7f8dd18e5cbL, 0x2c885ebb0a67bc24L, -3, 0x0L), /* i=145 */
    new Qint64(0x8b064012593d85a5L, 0x52013c7a80ad089bL, 0x42ada32f6b02af2dL, 0x88cfab3b8ffc09bcL, -3, 0x0L), /* i=146 */
    new Qint64(0x91eb89524e100d23L, 0x8fd3df5c52d67e7bL, 0x2024f18ebc9b8af6L, 0x81f936979eefae26L, -3, 0x0L), /* i=147 */
    new Qint64(0x98dcca69d27c263bL, 0x8e94203f336fc8c4L, 0xcfe4e777e9932f10L, 0x9ed7ba1820b6ff5L, -3, 0x0L), /* i=148 */
    new Qint64(0x9fda2d2cc9465c4fL, 0x32b9565f5355181L, 0xdc751798a72b3dc9L, 0x56eca518e7d2fe44L, -3, 0x0L), /* i=149 */
    new Qint64(0xa6e3dc4bde0e3cdbL, 0x570ff874170d2a8L, 0xc7be23c834886156L, 0xbd706a5a2627f5aaL, -3, 0x0L), /* i=150 */
    new Qint64(0xab9be6480c66ea9eL, 0x9ae21fd871b8d27cL, 0x7851f4e516e5c9bfL, 0xc1c5a79f56e96b76L, -3, 0x0L), /* i=151 */
    new Qint64(0xb2ba75f46099cf8bL, 0x2c3c2e77904afa78L, 0x77fa400e7e689a3L, 0x30a5f043d61bad59L, -3, 0x0L), /* i=152 */
    new Qint64(0xb9e5c83a7e8a655bL, 0xcbffe9661fe72421L, 0x2096b17331fac5dcL, 0x7775b110db2591bdL, -3, 0x0L), /* i=153 */
    new Qint64(0xc11e0b2a8d1e0ddbL, 0x9a631e830fd30903L, 0xd59edb68f6b3f63bL, 0x9ede162ed215bb6bL, -3, 0x0L), /* i=154 */
    new Qint64(0xc8636dcfe5e6ca0aL, 0x88e72835b3292d4fL, 0xd07fb98b088395eeL, 0x10b2726db3c15971L, -3, 0x0L), /* i=155 */
    new Qint64(0xcd43bc6f5d51c3e8L, 0xfbfb0e3f0fd23074L, 0x435c6598be364fabL, 0x938d2827ce902e72L, -3, 0x0L), /* i=156 */
    new Qint64(0xd49f69e456cf1b79L, 0x5f53bd2e406e66e7L, 0x7188af8f4f45b9eeL, 0x7ec0d7bfb6b65f0dL, -3, 0x0L), /* i=157 */
    new Qint64(0xdc08b985c11e9068L, 0x3b9cd767c3b1ac52L, 0x81fdd139ee15996cL, 0xdf9da9ae69110ecbL, -3, 0x0L), /* i=158 */
    new Qint64(0xe1014558bfcda3e2L, 0x35470a74be1230ecL, 0x7ea4f73313d9cef6L, 0x7a6dd1127f07bf9aL, -3, 0x0L), /* i=159 */
    new Qint64(0xe881bf932af3dac0L, 0xc524848e3443e03fL, 0xc22bd8fede6ee351L, 0x93f48308bc589a07L, -3, 0x0L), /* i=160 */
    new Qint64(0xed89ed86a44a01aaL, 0x11d49f96cb88317aL, 0xb09cac07eab378a8L, 0xe6342851611cc8beL, -3, 0x0L), /* i=161 */
    new Qint64(0xf52224f82557a459L, 0x8dcca8d7f17fa2a9L, 0x330d7e7fe8c1c62aL, 0x18d25c613e0e9a6dL, -3, 0x0L), /* i=162 */
    new Qint64(0xfa3a589a6f9146d8L, 0x388212895529a6faL, 0x937d820ed16d615eL, 0x6bf1e0ae92585a10L, -3, 0x0L), /* i=163 */
    new Qint64(0x80f572b1363487b9L, 0xf5bd0b5b3479d5f4L, 0x501b8b4a63fd6f67L, 0x6e9f54a7361289b3L, -2, 0x0L), /* i=164 */
    new Qint64(0x8389c3026ac3139bL, 0x62dda9d2270fa1f4L, 0x29aec44c9ebb0731L, 0xee2b9479c54b01fbL, -2, 0x0L), /* i=165 */
    new Qint64(0x86216b3b0b17188bL, 0x163ceae88f720f1dL, 0x9a8ffa0ca490b651L, 0x815baea454ee87caL, -2, 0x0L), /* i=166 */
    new Qint64(0x8a0b3f79b3bc180fL, 0x49b55ea7d3730d7L, 0x1f95d048ae9871b6L, 0xb7eb0007a2dfe5c0L, -2, 0x0L), /* i=167 */
    new Qint64(0x8cab69dcde17d2f7L, 0x3ad1aa142b94f169L, 0x82d7e38d0ce7657cL, 0x800bbe6769b633e0L, -2, 0x0L), /* i=168 */
    new Qint64(0x8f4f0b3c44cfa2a2L, 0x586e9343c9cfdbacL, 0x1fee2e686760d584L, 0xc0243088b56a6c0fL, -2, 0x0L), /* i=169 */
    new Qint64(0x934b1089a6dc93c1L, 0xdf5bb3b60554e151L, 0x87a486e65aa1bcd5L, 0xad047f998c197d96L, -2, 0x0L), /* i=170 */
    new Qint64(0x95f783e6e49a9cfaL, 0x4a5004f3ef063312L, 0xcac9f0589aff46a5L, 0x3b2136f4975a446eL, -2, 0x0L), /* i=171 */
    new Qint64(0x98a78f0e9ae71d85L, 0x2cdec34784707839L, 0x4861cab8c5ee1c94L, 0xb0ddc91e9b86138fL, -2, 0x0L), /* i=172 */
    new Qint64(0x9b5b3bb5f088b766L, 0xd878bbe3d392be25L, 0x24f04843d0f8f41L, 0xd27746bfed0adcfeL, -2, 0x0L), /* i=173 */
    new Qint64(0x9e1293b9998c1daaL, 0x5b035eae273a855eL, 0xf58182e4db06261cL, 0x73db477d896b83f5L, -2, 0x0L), /* i=174 */
    new Qint64(0xa22c8f029cfa45a9L, 0xdb5b709e0b69e773L, 0x5e171935b5381c36L, 0x930e7dec83978f45L, -2, 0x0L), /* i=175 */
    new Qint64(0xa4ed3f9de620f666L, 0x9b5e973353638c10L, 0xd81763d28db5c039L, 0xaef301c1c91f3649L, -2, 0x0L), /* i=176 */
    new Qint64(0xa7b1bf5dd4c07d4eL, 0x699db68db75e9a7eL, 0x8e98852150ea76b5L, 0x2d4a158846053fd7L, -2, 0x0L), /* i=177 */
    new Qint64(0xaa7a18dbdf0d44aaL, 0x604884a8dd76d08aL, 0x6c40e044972eeeb2L, 0x8d4e70541a83bec5L, -2, 0x0L), /* i=178 */
    new Qint64(0xad4656ddf6fd070cL, 0x9ea10260fe452ba2L, 0x59b0b64abac9cb07L, 0x30a999bf35a66756L, -2, 0x0L), /* i=179 */
    new Qint64(0xb0168457848f5f48L, 0xbb6f9fb246068d52L, 0x3e567a3312c2443dL, 0xe963d8ddfd9f7f8bL, -2, 0x0L), /* i=180 */
    new Qint64(0xb2eaac6a67005513L, 0xf4b716f6fec8156bL, 0x2c74b8f4ed61d394L, 0x1166335bb2b54fe0L, -2, 0x0L), /* i=181 */
};

  private static final Qint64[] _LOG_INV_3_2 = {
    new Qint64(0xff015358833c47e1L, 0xbb481c8ee1416959L, 0xed961f7cd039d43bL, 0x3813c435abc461e9L, -8, 0x1L), /* j=8128 */
    new Qint64(0xfb0933b732572a6dL, 0x214cca3dd1d4796aL, 0x63275973180916L, 0xce33d61e9d12b379L, -8, 0x1L), /* j=8129 */
    new Qint64(0xf710f492711d9d26L, 0xfbc7b38b17b2019L, 0x53eb4b80e74f4d9fL, 0xec0f1ece69a0881L, -8, 0x1L), /* j=8130 */
    new Qint64(0xf31895e84b1a6be6L, 0xb76782b9e88c84cbL, 0x3765e2cb07bc7842L, 0x1b2970050550b17bL, -8, 0x1L), /* j=8131 */
    new Qint64(0xef2017b6cba9cf9aL, 0x2dc85881664025b4L, 0xce96efdd54fdcc41L, 0xc953aa9864a2c806L, -8, 0x1L), /* j=8132 */
    new Qint64(0xeb2779fbfdf96874L, 0xce4ab4e678d0ed03L, 0x7c9ecdfd8f89db96L, 0xa637f86e778c350cL, -8, 0x1L), /* j=8133 */
    new Qint64(0xe72ebcb5ed08382bL, 0xb60585f4c4bb6062L, 0x1caec15031f4dc54L, 0x197d0a42fdccbac4L, -8, 0x1L), /* j=8134 */
    new Qint64(0xe335dfe2a3a69c2bL, 0x59bcffe9d5650564L, 0x50c342f5fce295a8L, 0xac842dc0defcda11L, -8, 0x1L), /* j=8135 */
    new Qint64(0xdf3ce3802c7647cdL, 0x3602021fa93b1e18L, 0x4f178f6a8fbb9ca5L, 0x4f07c79076670299L, -8, 0x1L), /* j=8136 */
    new Qint64(0xdb43c78c91ea3e8cL, 0x9944002534d09b3dL, 0x2354c6de776e85abL, 0x7bfb018bb7288d07L, -8, 0x1L), /* j=8137 */
    new Qint64(0xd74a8c05de46ce3aL, 0x87aa95782311a277L, 0x2ba2456733804a75L, 0x33512ca3a3f6fe2bL, -8, 0x1L), /* j=8138 */
    new Qint64(0xd35130ea1ba18930L, 0xb88be10313a1303cL, 0xc17153c5ab3b0225L, 0xe1f11ff3205d196eL, -8, 0x1L), /* j=8139 */
    new Qint64(0xcf57b63753e14083L, 0xad54bc31433dddbaL, 0x3d675807b776c8c3L, 0x50075ac879e03385L, -8, 0x1L), /* j=8140 */
    new Qint64(0xcb5e1beb90bdfe33L, 0xe1b7d813e3f825e0L, 0xd24faf76303783f0L, 0x55ff6c2398186fbeL, -8, 0x1L), /* j=8141 */
    new Qint64(0xc7646204dbc0ff5eL, 0x14f8c1be7370f218L, 0xb165fd239443b62cL, 0x237e837cc1f0bdfbL, -8, 0x1L), /* j=8142 */
    new Qint64(0xc36a88813e44ae6aL, 0xac27c5a6139cd30cL, 0x3e814a96bc97e05eL, 0xa2417a23566b2cd7L, -8, 0x1L), /* j=8143 */
    new Qint64(0xbf708f5ec1749d3cL, 0x2d23a0744e00f594L, 0x68f9c68b2e5248L, 0x1d701478d6f6c2b6L, -8, 0x1L), /* j=8144 */
    new Qint64(0xbb76769b6e4d7f5cL, 0xd235e25fb9644c30L, 0x869c4dd8468b27deL, 0x605b72cc1d8025bcL, -8, 0x1L), /* j=8145 */
    new Qint64(0xb77c3e354d9d242bL, 0x361ee0bcb5db0449L, 0x567cab7f031b369L, 0xd9bec487ee14afebL, -8, 0x1L), /* j=8146 */
    new Qint64(0xb381e62a68027106L, 0x18660815da3d7962L, 0xd14621e31d9f0de1L, 0x8fbb93f3b4e5b10eL, -8, 0x1L), /* j=8147 */
    new Qint64(0xaf876e78c5ed5b77L, 0x39c357b6bfdf81b4L, 0xf2591df10cea40d0L, 0x730872806a501e6eL, -8, 0x1L), /* j=8148 */
    new Qint64(0xab8cd71e6f9ee35dL, 0x5076c62c951204f5L, 0xb058f99c8186daa4L, 0xae0d7dcbc2a675abL, -8, 0x1L), /* j=8149 */
    new Qint64(0xa79220196d290d15L, 0x146244d643f7fa2aL, 0x90fee0b93d40db31L, 0xe67bce480f351d7bL, -8, 0x1L), /* j=8150 */
    new Qint64(0xa3974967c66edba1L, 0x62bb0f3208d9a1baL, 0xcdd5aab43dfdb463L, 0x9338ef1dac85e113L, -8, 0x1L), /* j=8151 */
    new Qint64(0x9f9c530783244ad2L, 0x7926e92808bd580cL, 0x9a874314df180c72L, 0x2272c79abfdb422cL, -8, 0x1L), /* j=8152 */
    new Qint64(0x9ba13cf6aace496cL, 0x4819e620d5fcc067L, 0x9066b677760637d4L, 0x308f6e39ffa6ca4cL, -8, 0x1L), /* j=8153 */
    new Qint64(0x97a6073344c2b34bL, 0xdc494943d427214eL, 0x423f0610339ed04bL, 0x185c41879a1a0e44L, -8, 0x1L), /* j=8154 */
    new Qint64(0x93aab1bb58284b8bL, 0xdf0805c4161e404bL, 0xb467a7c6839cc262L, 0x831c3f31eb48d551L, -8, 0x1L), /* j=8155 */
    new Qint64(0x8faf3c8cebf6b6a8L, 0x2d615caaa0514c3bL, 0xf729c68cd270f129L, 0xaab9342f19cba76cL, -8, 0x1L), /* j=8156 */
    new Qint64(0x8bb3a7a606f674a0L, 0x85c60c12eca0aedbL, 0xa3fb0e5c1d39e0e8L, 0x1dca7781b6e7202fL, -8, 0x1L), /* j=8157 */
    new Qint64(0x87b7f304afc0db1aL, 0x4c207a522524f8deL, 0x3d54277ac7f378a6L, 0xa5d1bbca5392d277L, -8, 0x1L), /* j=8158 */
    new Qint64(0x83bc1ea6ecc00f81L, 0x64243e02c6215a4eL, 0xb38c33565546e35cL, 0x19646b6a5fd02489L, -8, 0x1L), /* j=8159 */
    new Qint64(0xff805515885e0250L, 0x435ab4da6a5bb48cL, 0xcd29dd6d72582491L, 0xba6e335a1a33227fL, -9, 0x1L), /* j=8160 */
    new Qint64(0xf7882d5c7832c6ccL, 0x9e06fc84b6ea5e24L, 0x699801dab452e328L, 0x96dc3d1e75ba8032L, -9, 0x1L), /* j=8161 */
    new Qint64(0xef8fc61eb4b74f6eL, 0x91ab122ee427cfb4L, 0x8862e24ccd48f678L, 0x95fa76270a5d0366L, -9, 0x1L), /* j=8162 */
    new Qint64(0xe7971f584945efaeL, 0x5f832513e3211642L, 0xa65998dde4dd76e0L, 0xbbb102dc658c60L, -9, 0x1L), /* j=8163 */
    new Qint64(0xdf9e390540da5fbeL, 0x5e7b48cfeeb85aa7L, 0xbcaca74cb74df3d2L, 0x55bfb0c4fdd76008L, -9, 0x1L), /* j=8164 */
    new Qint64(0xd7a51321a611b0c1L, 0xb36a9f58eb4ccd07L, 0x8770a2e82b32cf54L, 0xc9fdca283fb5d971L, -9, 0x1L), /* j=8165 */
    new Qint64(0xcfabada9832a4101L, 0x3360751e43c7af35L, 0x5d10db5217ec2ab8L, 0xc60248447782dce7L, -9, 0x1L), /* j=8166 */
    new Qint64(0xc7b20898e203b01eL, 0x6fab78aca91193cbL, 0x5e4d97ba155a9de6L, 0xcdd79f978d1afd7bL, -9, 0x1L), /* j=8167 */
    new Qint64(0xbfb823ebcc1ed344L, 0xeb432409cffdad8dL, 0x568dc2013b32ced6L, 0x72b53615be59209eL, -9, 0x1L), /* j=8168 */
    new Qint64(0xb7bdff9e4a9da959L, 0x793b5acf3a336461L, 0x9cffddada1c113a2L, 0xb54d414e644993a8L, -9, 0x1L), /* j=8169 */
    new Qint64(0xafc39bac66434f27L, 0xc3ea2cd93f316b33L, 0xbfcaabcf0318ef95L, 0x85a480f694e7b857L, -9, 0x1L), /* j=8170 */
    new Qint64(0xa7c8f8122773f38dL, 0xfc679a28e9d9f212L, 0x487785d971aec0afL, 0x61cbdae5b7255821L, -9, 0x1L), /* j=8171 */
    new Qint64(0x9fce14cb9634cba6L, 0xb20f215bd3b58c60L, 0xd8ba6eedf272eeb0L, 0x574dfcf4f87b33e8L, -9, 0x1L), /* j=8172 */
    new Qint64(0x97d2f1d4ba2c06f0L, 0xd1aacedcefe9d376L, 0xb21c7fe4cdbc5967L, 0xd35c35c4241c3714L, -9, 0x1L), /* j=8173 */
    new Qint64(0x8fd78f299aa0c375L, 0xcbef6fac33691e95L, 0x466fab846a1e3e70L, 0xef6ef4000ffc2f4bL, -9, 0x1L), /* j=8174 */
    new Qint64(0x87dbecc63e7b01edL, 0xe2f1775134c8da75L, 0x134f09715ee9c6cfL, 0x48be7593379645aaL, -9, 0x1L), /* j=8175 */
    new Qint64(0xffc0154d588733c5L, 0x3c742a7c76356395L, 0xb1d845d134023d8eL, 0x66ad982559cdd0ceL, -10, 0x1L), /* j=8176 */
    new Qint64(0xefc7d18dd4485b9eL, 0xca47c52b7d7ffce2L, 0x13c8ea71e3d8fe30L, 0x8edd8e108ea7f135L, -10, 0x1L), /* j=8177 */
    new Qint64(0xdfcf0e45fbce3e80L, 0x7e4cfbd830393b87L, 0x83304a61505642d8L, 0xceeccccf5069ebf8L, -10, 0x1L), /* j=8178 */
    new Qint64(0xcfd5cb6dd9ef05ddL, 0x7370ae83f9e72748L, 0x140f4a016e0c0d28L, 0x5aea5efc41fa18fdL, -10, 0x1L), /* j=8179 */
    new Qint64(0xbfdc08fd78c229b9L, 0xe6dbb624f9739781L, 0xbbd85b81581c98f8L, 0xe1340de77cd6a600L, -10, 0x1L), /* j=8180 */
    new Qint64(0xafe1c6ece1a058ddL, 0x97fa2fd0c9dc723dL, 0x998985ef1e4636e0L, 0x306c5597c846f0b9L, -10, 0x1L), /* j=8181 */
    new Qint64(0x9fe705341d236102L, 0x7199cd06ae5d39b3L, 0x67da60b1a110ff9L, 0x14c940c27f248cedL, -10, 0x1L), /* j=8182 */
    new Qint64(0x8febc3cb332616ffL, 0x7b6d1248c3e1fd3fL, 0xd8ceb8a313143c9cL, 0xe1449e20d24508b2L, -10, 0x1L), /* j=8183 */
    new Qint64(0xffe0055455887de0L, 0x26828c92649a3a38L, 0xc3585d8bbd3ac1b8L, 0xd315929badc83115L, -11, 0x1L), /* j=8184 */
    new Qint64(0xdfe7839214b4e8aeL, 0xda6959f7f0e01bf0L, 0x3ff9151061ec91aaL, 0x8f307c96ffa40bbaL, -11, 0x1L), /* j=8185 */
    new Qint64(0xbfee023faf0c2480L, 0xb47505bfa5a03b06L, 0x2b8ff7c8377c9037L, 0x6cd9392c0d17528bL, -11, 0x1L), /* j=8186 */
    new Qint64(0x9ff3814d2e4a36b2L, 0xa8740b91c95df537L, 0x5526081fe3d93a56L, 0x3f68c3cd00e45f8bL, -11, 0x1L), /* j=8187 */
    new Qint64(0xfff0015535588833L, 0x3c56c598c659c2a2L, 0xf5c74f2f07e4f272L, 0xc451b2e04ebd63eeL, -12, 0x1L), /* j=8188 */
    new Qint64(0xbff7008ff5e0c257L, 0x379eba7e6465ff63L, 0x3535a7e74bbb0089L, 0x937a324b4307d36L, -12, 0x1L), /* j=8189 */
    new Qint64(0xfff8005551558885L, 0xde026e271ee0549cL, 0x8cd0b8002d083c9bL, 0x2e9198222f25f83cL, -13, 0x1L), /* j=8190 */
    new Qint64(0x0L, 0x0L, 0x0L, 0x0L, 255, 0x1L), /* j=8191 */
    new Qint64(0x0L, 0x0L, 0x0L, 0x0L, 255, 0x1L), /* j=8192 */
    new Qint64(0xc004802401440c26L, 0xdfeb485085f6f453L, 0xb62f8fe41e621f91L, 0x274178d2188f9ea7L, -13, 0x0L), /* j=8193 */
    new Qint64(0xa00640535a37a37aL, 0x6bc1e20eac8448b4L, 0xdf9f60ec72f7062L, 0x6f190de53deca5e8L, -12, 0x0L), /* j=8194 */
    new Qint64(0xe00c40e4bd6e4efdL, 0xc72446cc1bf728bdL, 0x5e7d3be7e456c8a7L, 0x180e4b0fc869da77L, -12, 0x0L), /* j=8195 */
    new Qint64(0x900a20f319a3e273L, 0x569b26aaa485ea5bL, 0xf5e4d6d9243bca81L, 0x2714208ba14d04c8L, -11, 0x0L), /* j=8196 */
    new Qint64(0xb00f21bbe3e388eeL, 0x5f69768284463b9bL, 0x18baa187613466fL, 0x5b94041491764cd7L, -11, 0x0L), /* j=8197 */
    new Qint64(0xd01522dcc4f87991L, 0x14d9d76196d8043aL, 0x179e870f7485c2ebL, 0x7931552a9aec92fbL, -11, 0x0L), /* j=8198 */
    new Qint64(0xf01c2465c5e61b6fL, 0x661e135f49a47c40L, 0x42882a135aa4aa82L, 0x82166812bed98c2dL, -11, 0x0L), /* j=8199 */
    new Qint64(0x881213337898871eL, 0x9a31ba0cbc030352L, 0xdc58134f3ce2ed6eL, 0x6c0826bdc4127939L, -10, 0x0L), /* j=8200 */
    new Qint64(0x98169478296fad41L, 0x7ad1e9c315328f7dL, 0xcc0b6a758f391573L, 0xa0bdfad5f77c4a7fL, -10, 0x0L), /* j=8201 */
    new Qint64(0xa81b9608fc3c50ecL, 0xf105b66ec4703edeL, 0x76406288c82a0e9cL, 0xc06e5c3eaccc9b65L, -10, 0x0L), /* j=8202 */
    new Qint64(0xb82117edf8832797L, 0xd6aef30cd312169aL, 0x24ed9892618f8da1L, 0x6091c8f53e42724eL, -10, 0x0L), /* j=8203 */
    new Qint64(0xc8271a2f2689e388L, 0xe6e2acf8f4d4c249L, 0x830dad0cbcb297deL, 0x6f8eeb67afc8978dL, -10, 0x0L), /* j=8204 */
    new Qint64(0xd82d9cd48f574c00L, 0x28bb3cd9f2a65fb4L, 0x9c8f264a305434a2L, 0x719267af576b8da7L, -10, 0x0L), /* j=8205 */
    new Qint64(0xe8349fe63cb35564L, 0x224a96f5a7471c45L, 0xd39ed746e5b2ba94L, 0xdb742c4b8aeaa82eL, -10, 0x0L), /* j=8206 */
    new Qint64(0xf83c236c39273972L, 0xd462b63756c87e80L, 0x157ee3bffb879ef4L, 0x9e384ccad23b6068L, -10, 0x0L), /* j=8207 */
    new Qint64(0x842213b747fec7bbL, 0x3ff51287882500edL, 0x124d4848d57cf1e2L, 0xca3575b044a7e9d6L, -9, 0x0L), /* j=8208 */
    new Qint64(0x8c2655faa6a1323fL, 0x1ab9679b55f78a6aL, 0x84963a91b59a785cL, 0xf77577a5e4380677L, -9, 0x0L), /* j=8209 */
    new Qint64(0x942ad8843ee1a9cdL, 0x17e4b7ac6c600cb4L, 0x6767118f1a71745bL, 0x98a85668eda9496fL, -9, 0x0L), /* j=8210 */
    new Qint64(0x9c2f9b581787cf0dL, 0xfd1a09c848e3950dL, 0xe0e66fad558345dbL, 0xd0914651b6655eb2L, -9, 0x0L), /* j=8211 */
    new Qint64(0xa4349e7a37bc21edL, 0x318b2ddd9d0a33b3L, 0x9dd7a4359a20f4aeL, 0x176ea6faed5bf263L, -9, 0x0L), /* j=8212 */
    new Qint64(0xac39e1eea7080dbcL, 0x9dd91e52c79fd070L, 0x184596be172aa3d1L, 0x8a86b1bb367fee8L, -9, 0x0L), /* j=8213 */
    new Qint64(0xb43f65b96d55f55aL, 0x72de1d99ce252efdL, 0x149ec47368e2b10fL, 0xb294ccdf83577dc0L, -9, 0x0L), /* j=8214 */
    new Qint64(0xbc4529de92f13f58L, 0xd7bd1d62ef25480dL, 0x7b1a44d708e0875bL, 0x83a3462cb58566daL, -9, 0x0L), /* j=8215 */
    new Qint64(0xc44b2e6220866227L, 0x7f921124f1ecb59eL, 0x7696655fbc6715a6L, 0xb820b79a7e7be96fL, -9, 0x0L), /* j=8216 */
    new Qint64(0xcc5173481f22f03fL, 0x271ee1cd6d5cdf9dL, 0x808228274a503b4bL, 0x3ec027918b227262L, -9, 0x0L), /* j=8217 */
    new Qint64(0xd457f8949835a44eL, 0xfad0cc8b5faea8cbL, 0xa39dee1517d770f7L, 0x87b62eb6a5071ae6L, -9, 0x0L), /* j=8218 */
    new Qint64(0xdc5ebe4b958e6d6bL, 0xe57a0acb9d5cd4deL, 0xd926113c7cf9cae4L, 0x2cc6a626c5980669L, -9, 0x0L), /* j=8219 */
    new Qint64(0xe465c471215e7b41L, 0xc81bb5a8d789f443L, 0x8c30a4efa2085a91L, 0xd8416fa14d8a0531L, -9, 0x0L), /* j=8220 */
    new Qint64(0xec6d0b0946384a46L, 0x9b1beb40437575f4L, 0x86e839aee0bd3623L, 0x447c2a52014276c5L, -9, 0x0L), /* j=8221 */
    new Qint64(0xf47492180f0fafefL, 0x7944509046652d98L, 0xb65fa4f60aca7f76L, 0x4229769700e69546L, -9, 0x0L), /* j=8222 */
    new Qint64(0xfc7c59a18739e6e7L, 0x94e51ebff53a2f15L, 0x12a476e601733b9fL, 0xe7eb2f0e037ceedbL, -9, 0x0L), /* j=8223 */
    new Qint64(0x824230d4dd36cda4L, 0x8bbc7f765b13ebbeL, 0x68772abd1ce3258cL, 0xe7cf18b488213d56L, -8, 0x0L), /* j=8224 */
    new Qint64(0x8646551a5a617b6bL, 0xf61305ef7390939cL, 0x1819673e1d680b66L, 0x3bb660c119045dcbL, -8, 0x0L), /* j=8225 */
    new Qint64(0x8a4a99a34159d69fL, 0x3abc32a78afd4b7aL, 0xea68f0383acd9ff4L, 0xe7d3305627ebab51L, -8, 0x0L), /* j=8226 */
    new Qint64(0x8e4efe71988d8426L, 0x17596a598cb29436L, 0x3dddd5d01649b409L, 0x314d41b7d5759903L, -8, 0x0L), /* j=8227 */
    new Qint64(0x92538387669afa1bL, 0x1c890bee9a9d743cL, 0x7139a25d67ed976cL, 0x715b9888be5228e7L, -8, 0x0L), /* j=8228 */
    new Qint64(0x965828e6b25185ecL, 0xeaafbd07b543145cL, 0xb28914b789a13376L, 0xc286d314221430a0L, -8, 0x0L), /* j=8229 */
    new Qint64(0x9a5cee9182b15280L, 0x6517bc4112d64b17L, 0x3e099f46da82a60fL, 0x5ac8c579eeb8644cL, -8, 0x0L), /* j=8230 */
    new Qint64(0x9e61d489deeb6e53L, 0xdb94a1dfd653d3a5L, 0x46503739bf42fb2eL, 0xeabea04e3ce90eedL, -8, 0x0L), /* j=8231 */
    new Qint64(0xa266dad1ce61d1a3L, 0x2ada01ce7ed3607fL, 0x99d41a958f292523L, 0x2b08613964628dc6L, -8, 0x0L), /* j=8232 */
    new Qint64(0xa66c016b58a7648cL, 0xd3b36c029ea7bb5dL, 0x36f49e73617ad152L, 0x967313472d10eeeeL, -8, 0x0L), /* j=8233 */
    new Qint64(0xaa71485885800538L, 0x94c529f32403828L, 0x253f332743a0ff31L, 0x6548b3ed5877119aL, -8, 0x0L), /* j=8234 */
    new Qint64(0xae76af9b5ce08dfbL, 0xb6b6676248bba138L, 0xb6c98155115da0b7L, 0x6fcf6732c378b707L, -8, 0x0L), /* j=8235 */
    new Qint64(0xb27c3735e6eedb86L, 0x7bdd0c2a9c7a679aL, 0x5fb1c32c4c750e98L, 0xb5b84cc7549d184aL, -8, 0x0L), /* j=8236 */
    new Qint64(0xb47f0724b1906935L, 0x23deb274e953a258L, 0x990d7e42e0f2ed1bL, 0xe6c4e93f7ba138feL, -8, 0x0L), /* j=8237 */
    new Qint64(0xb884bf4697559ffaL, 0xdae7e343fa859415L, 0x5e4e41c1b5e85e4aL, 0xf82d9009ecaad498L, -8, 0x0L), /* j=8238 */
    new Qint64(0xbc8a97c544fdd5ebL, 0x17759bff5c717992L, 0x949294749166f218L, 0x780bea6b173a668dL, -8, 0x0L), /* j=8239 */
    new Qint64(0xc09090a2c35aa070L, 0x52e7e4dde874dacdL, 0xa952b40de6b303a6L, 0x46e9f250087d1466L, -8, 0x0L), /* j=8240 */
    new Qint64(0xc496a9e11b6eb30cL, 0xa88971f8277a4d10L, 0xcc6e1825f856fe70L, 0x18193291e2b36381L, -8, 0x0L), /* j=8241 */
    new Qint64(0xc89ce382566de587L, 0x269de85f0df92587L, 0xe65771fd08853e31L, 0xa9b4b0681339900L, -8, 0x0L), /* j=8242 */
    new Qint64(0xcca33d887dbd3a1aL, 0x180d255422c3377cL, 0x35564b2dfc6a51ccL, 0xad4c9d66d29442baL, -8, 0x0L), /* j=8243 */
    new Qint64(0xd0a9b7f59af2e3a2L, 0x46da70925ee85c05L, 0x7b96716bbe5fc916L, 0x949df14f9331856dL, -8, 0x0L), /* j=8244 */
    new Qint64(0xd4b052cbb7d64bcfL, 0x37968ceafaf7b452L, 0x866ac7791182f8a4L, 0x879a1365f37550b8L, -8, 0x0L), /* j=8245 */
    new Qint64(0xd8b70e0cde601954L, 0x5dfba4cfdd38a058L, 0xc04093a8c6018f91L, 0x343b553466b61759L, -8, 0x0L), /* j=8246 */
    new Qint64(0xdcbde9bb18ba361bL, 0x4ae21abe75d5a19aL, 0xd62f6d57c561e135L, 0x49b45337d2f46eb8L, -8, 0x0L), /* j=8247 */
    new Qint64(0xe0c4e5d8713fd576L, 0xd3bd4fd98a1e6fe5L, 0x625d637fcaa83aa9L, 0x32bb34856444782cL, -8, 0x0L), /* j=8248 */
    new Qint64(0xe4cc0266f27d7a57L, 0x33cf7d5ebfb93ad3L, 0x6a8d8f7f711ab809L, 0xe15b1d297f6f796cL, -8, 0x0L), /* j=8249 */
    new Qint64(0xe8d33f68a730fd7fL, 0x2743c805a4928086L, 0x8923300474737280L, 0xd02a66f2f0de4befL, -8, 0x0L), /* j=8250 */
    new Qint64(0xecda9cdf9a4993baL, 0x5dbeb9795455a5L, 0x5b168ca67fe36520L, 0xaf0629427a97e44fL, -8, 0x0L), /* j=8251 */
    new Qint64(0xf0e21acdd6e7d412L, 0xb6ed80852ae6fd62L, 0xdfbedfa9caad16a4L, 0x5f822584035372e8L, -8, 0x0L), /* j=8252 */
    new Qint64(0xf4e9b935685dbe0bL, 0xf237cff1acb306b3L, 0x8557e05d2318ee0L, 0x5453667bec383296L, -8, 0x0L), /* j=8253 */
    new Qint64(0xf8f178185a2ebfd9L, 0xd81648249cece4cL, 0x3f4609735b1102e2L, 0x295ae12732b36dafL, -8, 0x0L), /* j=8254 */
    new Qint64(0xfcf95778b80fbc98L, 0x176cd56887ac7fe8L, 0xaa02e8447626768dL, 0x553f90bb90928bc4L, -8, 0x0L), /* j=8255 */
    new Qint64(0x8080abac46f38946L, 0x662d417ced007a45L, 0xc0be1062bd88c8e8L, 0xe925964e76028722L, -7, 0x0L), /* j=8256 */
};

  private static final Qint64[] T1_3 = {
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L),
    new Qint64(0x8164d1f3bc030773L, 0x7be56527bd14def4L, 0x9eb851655e2e5c4dL, 0xd08075ac1f200e4cL, 0, 0x0L),
    new Qint64(0x82cd8698ac2ba1d7L, 0x3e2a475b46520bffL, 0x29f1a4afbefa5d7cL, 0x2502f15067378a17L, 0, 0x0L),
    new Qint64(0x843a28c3acde4046L, 0x1af92eca13fd1582L, 0xd96b414ec4c9d06L, 0x806bddad09d9c4a3L, 0, 0x0L),
    new Qint64(0x85aac367cc487b14L, 0xc5c95b8c2154c1b2L, 0x148a0459e7585151L, 0x5d42b362af1ee859L, 0, 0x0L),
    new Qint64(0x871f61969e8d1010L, 0x3a1727c57b52a956L, 0x259ac58894f4fcb3L, 0x5229a7352c9b247bL, 0, 0x0L),
    new Qint64(0x88980e8092da8527L, 0x5df8d76c98c67562L, 0xe623d58b3772ba13L, 0x8bc3587fb118c94dL, 0, 0x0L),
    new Qint64(0x8a14d575496efd9aL, 0x80ca1d92c3680c2L, 0x259c4df53d76e910L, 0xe9c32d22e935007dL, 0, 0x0L),
    new Qint64(0x8b95c1e3ea8bd6e6L, 0xfbe4628758a53c90L, 0x1aa84ffbebac349fL, 0x91e135ee84a3f734L, 0, 0x0L),
    new Qint64(0x8d1adf5b7e5ba9e5L, 0xb4c7b4968e41ad36L, 0x183926ae7d718dc2L, 0x724a166325437476L, 0, 0x0L),
    new Qint64(0x8ea4398b45cd53c0L, 0x2dc0144c8783d4c5L, 0xa11037230b367828L, 0xeb90ce3700bf59b6L, 0, 0x0L),
    new Qint64(0x9031dc431466b1dcL, 0x775814a8494e87e2L, 0x43e90e15c2002132L, 0x6f398dfe3f7903f1L, 0, 0x0L),
    new Qint64(0x91c3d373ab11c336L, 0xfd6d8e0ae5ac9d8L, 0x1942b34816fb4f26L, 0xf1203caf65bfb9b9L, 0, 0x0L),
    new Qint64(0x935a2b2f13e6e92bL, 0xd339940e9d924ee7L, 0x2748c36eeaffa273L, 0x583eab6852a22bb1L, 0, 0x0L),
    new Qint64(0x94f4efa8fef70961L, 0x2e8afad12551de54L, 0x4856046901ff6c05L, 0x35fb634c2e63a0fL, 0, 0x0L),
    new Qint64(0x96942d3720185a00L, 0x48ea9b683a9c22c4L, 0xe0e68d9f200c5358L, 0x9a22b1526bb6a2e4L, 0, 0x0L),
    new Qint64(0x9837f0518db8a96fL, 0x46ad23182e42f6f6L, 0x5e139a1b14fa8178L, 0xd78b65cbefa7bb70L, 0, 0x0L),
    new Qint64(0x99e0459320b7fa64L, 0xe43086cb34b5fcaeL, 0x8ac981ca9ceca6b3L, 0x1560e51a5df911dcL, 0, 0x0L),
    new Qint64(0x9b8d39b9d54e5538L, 0xa2a817a2a3cc3f1fL, 0x928b5fce34cdf21L, 0x9769d9b0a908a786L, 0, 0x0L),
    new Qint64(0x9d3ed9a72cffb750L, 0xde494cf050e99b0bL, 0x1ff17c29677589a0L, 0x33a6fe2d4fd53e8aL, 0, 0x0L),
    new Qint64(0x9ef5326091a111adL, 0xa0911f09ebb9fdd1L, 0x65c15c122133e2a2L, 0x21f977fe7c7fa118L, 0, 0x0L),
    new Qint64(0xa0b0510fb9714fc2L, 0x192dc79edb0fd9a9L, 0x782a0735d02b1a20L, 0x9f33f7bc78dc629fL, 0, 0x0L),
    new Qint64(0xa27043030c496818L, 0x9b7a04ef80cfdea7L, 0x9da4384dbc2c8eaeL, 0x5a7a799221808de9L, 0, 0x0L),
    new Qint64(0xa43515ae09e6809eL, 0xd1db4831781e1eeL, 0xbae743abfbc07376L, 0x4c72418596cc5bd0L, 0, 0x0L),
    new Qint64(0xa5fed6a9b15138eaL, 0x1cbd7f621710701bL, 0x1dd170ace2bcfc17L, 0x2589c98a8290d3f0L, 0, 0x0L),
    new Qint64(0xa7cd93b4e9653569L, 0x9ec5b4d5039f72afL, 0x1424bd194d3999eL, 0xdd30939a1d1e929cL, 0, 0x0L),
    new Qint64(0xa9a15ab4ea7c0ef8L, 0x541e24ec3531fa73L, 0x3951f214c02d824aL, 0x325c9e2203504517L, 0, 0x0L),
    new Qint64(0xab7a39b5a93ed337L, 0x658023b2759e0079L, 0x7ad59ec00ebe6393L, 0x967357d6b36df9f8L, 0, 0x0L),
    new Qint64(0xad583eea42a14ac6L, 0x4980a8c8f59a2ec4L, 0x6be409407034fdedL, 0xb165f141833a67daL, 0, 0x0L),
    new Qint64(0xaf3b78ad690a4374L, 0xdf26101ccbb35032L, 0xa4502c14f429ded9L, 0x5a8c73beaa946990L, 0, 0x0L),
    new Qint64(0xb123f581d2ac258fL, 0x87d037e96d215d8eL, 0x757cfb9913adc577L, 0x97ced890d5b0b0c0L, 0, 0x0L),
    new Qint64(0xb311c412a9112489L, 0x3ecf14dc798a519bL, 0xfa6e051d6f8bc3ffL, 0xba1e54cf684354dfL, 0, 0x0L),
    new Qint64(0xb504f333f9de6484L, 0x597d89b3754abe9fL, 0x1d6f60ba893ba84cL, 0xed17ac8583339915L, 0, 0x0L),
    new Qint64(0xb6fd91e328d17791L, 0x7165f0ddd541a59L, 0xf88abbe777df360eL, 0x20850e774a86cd8fL, 0, 0x0L),
    new Qint64(0xb8fbaf4762fb9ee9L, 0x1b879778566b65a1L, 0xa5ab16cf451056edL, 0x322d7893ed4da9a8L, 0, 0x0L),
    new Qint64(0xbaff5ab2133e45fbL, 0x74d519d24593838cL, 0x2f30d0bdcaa516dL, 0x6c373a75c2828202L, 0, 0x0L),
    new Qint64(0xbd08a39f580c36beL, 0xa8811fb66d0faf7aL, 0x15b34bbcb0298f41L, 0xd9a4be023ece032L, 0, 0x0L),
    new Qint64(0xbf1799b67a731082L, 0xe815d0abcbf0b850L, 0xa13fc7e6faf9c830L, 0x83ea957596be426dL, 0, 0x0L),
    new Qint64(0xc12c4cca66709456L, 0x7c457d59a50087b5L, 0x6b2e5dd607a9969cL, 0xdefefee72ae7a33dL, 0, 0x0L),
    new Qint64(0xc346ccda24976407L, 0x20ec856128b83a42L, 0x6b9f89b7dabbcb2bL, 0x5b718d616c4fef19L, 0, 0x0L),
    new Qint64(0xc5672a115506daddL, 0x3e2ad0c964dd9f37L, 0x6b0f939998251a36L, 0xc7686006e4e6c093L, 0, 0x0L),
    new Qint64(0xc78d74c8abb9b15cL, 0xc13a2e3976c0277eL, 0x4da570a2c574a304L, 0xcea65224bc9900d0L, 0, 0x0L),
    new Qint64(0xc9b9bd866e2f27a2L, 0x80e1f92a0511697eL, 0x257ac0db1f419377L, 0xf4dd023ff93c7ffbL, 0, 0x0L),
    new Qint64(0xcbec14fef2727c5cL, 0xf4907c8f45ebf6dcL, 0xeb8a25b7b40c0426L, 0x639aa6f940962626L, 0, 0x0L),
    new Qint64(0xce248c151f8480e3L, 0xe235838f95f2c6edL, 0x6f28610b8c36485aL, 0x2bbd398af35c079fL, 0, 0x0L),
    new Qint64(0xd06333daef2b2594L, 0xd6d45c6559a4d502L, 0x11546d3ea28976d6L, 0x2a33269ab05c3e5dL, 0, 0x0L),
    new Qint64(0xd2a81d91f12ae45aL, 0x12248e57c3de4028L, 0x52029c0b81f7be57L, 0xfa7663033f05357bL, 0, 0x0L),
    new Qint64(0xd4f35aabcfedfa1fL, 0x5921deffa6262c5aL, 0xb8e7a32e5783da5cL, 0xfa628009459a2417L, 0, 0x0L),
    new Qint64(0xd744fccad69d6af4L, 0x39a68bb9902d3fdeL, 0x1d733af522058b16L, 0xb5c13ada0e77829aL, 0, 0x0L),
    new Qint64(0xd99d15c278afd7b5L, 0xfe873deca3e12babL, 0xc0edda4d891be43dL, 0xb70cfbb1bdf6eb5dL, 0, 0x0L),
    new Qint64(0xdbfbb797daf23755L, 0x3d840d5a9e29aa64L, 0x481e1ab725b12d56L, 0x613b0d1dbfa0d717L, 0, 0x0L),
    new Qint64(0xde60f4825e0e9123L, 0xdd07a2d9e8466859L, 0x1438495eacdf256L, 0xcc2490c8643ef6b4L, 0, 0x0L),
    new Qint64(0xe0ccdeec2a94e111L, 0x65895048dd333caL, 0x224b251b33092002L, 0x1cb99d3f1ff298a2L, 0, 0x0L),
    new Qint64(0xe33f8972be8a5a51L, 0x9bfe90795980eecL, 0xf358a8d368fceaeaL, 0xfa8fcbb2e85b853fL, 0, 0x0L),
    new Qint64(0xe5b906e77c8348a8L, 0x1e5e8f4a4edbb0ecL, 0xaacd6065b6e9f6acL, 0xcefcd5b62a14b818L, 0, 0x0L),
    new Qint64(0xe8396a503c4bdc68L, 0x791790d0ac70c7ddL, 0xfe312f84fa665204L, 0x3a1c6473409c261dL, 0, 0x0L),
    new Qint64(0xeac0c6e7dd24392eL, 0xd02d75b3706e54faL, 0xc4faace043b7f91cL, 0x17d8d1e8ca31880bL, 0, 0x0L),
    new Qint64(0xed4f301ed9942b84L, 0x600d2db6a64bfb12L, 0x3787630a764ae4c9L, 0xc8e7c95b06416e6dL, 0, 0x0L),
    new Qint64(0xefe4b99bdcdaf5cbL, 0x46561cf6948db912L, 0xd4a277eaddaa925cL, 0x9392870834f21a53L, 0, 0x0L),
    new Qint64(0xf281773c59ffb139L, 0xe8980a9cc8f47a4bL, 0x2cf0b49df0bd70e9L, 0x7c43b0ea5d43228dL, 0, 0x0L),
    new Qint64(0xf5257d152486cc2cL, 0x7b9d0c7aed980fc3L, 0x6f510308677709f5L, 0xbdd80329364aa2a0L, 0, 0x0L),
    new Qint64(0xf7d0df730ad13bb8L, 0xfe90d496d60fb6eaL, 0xe914ffb4723793f1L, 0xef6797b5a11efb7cL, 0, 0x0L),
    new Qint64(0xfa83b2db722a033aL, 0x7c25bb14315d7fccL, 0x8006fe21a95d14dcL, 0x4844b29bf4af18e8L, 0, 0x0L),
    new Qint64(0xfd3e0c0cf486c174L, 0x853f3a5931e0ee03L, 0x61b7bb285a60791L, 0x9d2285b6754edd61L, 0, 0x0L),
};

  private static final Qint64[] T2_3 = {
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L),
    new Qint64(0x80058baf7fee3b5dL, 0x1c718b38e549cb93L, 0x34a318717a85d198L, 0x945b3ca6120b7d55L, 0, 0x0L),
    new Qint64(0x800b179c82028fd0L, 0x945e54e2ae18f2f0L, 0x36ee988aaff03620L, 0x76cc37ff9584ce15L, 0, 0x0L),
    new Qint64(0x8010a3c708e73282L, 0x2b96d62d51c15a07L, 0x68b51f6090715cdaL, 0xe7a99fea0d150e10L, 0, 0x0L),
    new Qint64(0x8016302f17467628L, 0x3690dfe44d11d008L, 0x403605216aed73f0L, 0x49b8f71dcaa49423L, 0, 0x0L),
    new Qint64(0x801bbcd4afcacb08L, 0xe23a986bd3e626f0L, 0x5bdd95c213fb273cL, 0x9e66ef0d411e38d0L, 0, 0x0L),
    new Qint64(0x802149b7d51ebefbL, 0x7bdbadbc888aeb29L, 0x201cf874aa8cafc4L, 0x92f52199af16c4deL, 0, 0x0L),
    new Qint64(0x8026d6d889ecfd69L, 0xb904bbfb40d3a2b6L, 0x84a6d5d525029ce2L, 0x32bdce1e8420f0a8L, 0, 0x0L),
    new Qint64(0x802c6436d0e04f50L, 0xff8ce94a6797b3ceL, 0x345f82f5b1fae20eL, 0x3d0b18c06975c162L, 0, 0x0L),
    new Qint64(0x8031f1d2aca39b43L, 0xad9db772901d96b5L, 0x8f6321e8e84c97d3L, 0x3cc84ae246bf5abbL, 0, 0x0L),
    new Qint64(0x80377fac1fe1e56aL, 0x61cd0bffd7cfc682L, 0xc0432e96c959387bL, 0xb9c4ef247a66c427L, 0, 0x0L),
    new Qint64(0x803d0dc32d464f85L, 0x43456f71b96affd4L, 0x34c51656768b5277L, 0xb09b272d97fcc8L, 0, 0x0L),
    new Qint64(0x80429c17d77c18edL, 0x49fc841afba9c3c5L, 0xaedee98517f79365L, 0x836514d2d81fdf14L, 0, 0x0L),
    new Qint64(0x80482aaa212e9e95L, 0x86f7b54f6c45c85eL, 0x14747b1b6977fb14L, 0xbec69e11682e0863L, 0, 0x0L),
    new Qint64(0x804db97a0d095b0cL, 0x6c9f1f7d1efcfe68L, 0x6b994b07993e3561L, 0x9e86080e1001781dL, 0, 0x0L),
    new Qint64(0x805348879db7e67dL, 0x171eb1ceef1d1f28L, 0x5629bb4d6d20a74aL, 0x7407003ab22ffa82L, 0, 0x0L),
    new Qint64(0x8058d7d2d5e5f6b0L, 0x94d589f608ee4aa2L, 0x2adc0c3f864ba0f5L, 0x9dc70119154b8f9aL, 0, 0x0L),
    new Qint64(0x805e675bb83f5f0fL, 0x2ed38ab8472b2143L, 0xc40f99da125c266fL, 0x94d103f4365ed44bL, 0, 0x0L),
    new Qint64(0x8063f722477010a1L, 0xb1652de1378af1a0L, 0x8e5b66f89923f0ceL, 0x4c9bb4d5be541cc3L, 0, 0x0L),
    new Qint64(0x8069872686241a12L, 0xb4ad9233a0390cacL, 0x930d2b4079a002bdL, 0x5cf73638639bbbd6L, 0, 0x0L),
    new Qint64(0x806f17687707a7afL, 0xe54ec5f966eb1872L, 0x76754509f037248aL, 0xd762ffd79b46d451L, 0, 0x0L),
    new Qint64(0x8074a7e81cc7036bL, 0x4d204ecfc11f4aaaL, 0xf02c00376690ea79L, 0x233e0911cc8de5f9L, 0, 0x0L),
    new Qint64(0x807a38a57a0e94dcL, 0x9bf3ef4d9be2d1e4L, 0x6dbfe64309a2b072L, 0x3c5cacc11f785d53L, 0, 0x0L),
    new Qint64(0x807fc9a0918ae142L, 0x7068ab2230585d12L, 0x9fe6067d9e828773L, 0xdc2ac4e4e300cb2cL, 0, 0x0L),
    new Qint64(0x80855ad965e88b83L, 0xa0cc0a49c10ea66aL, 0xf0eb8fefacaf32d8L, 0x78899d5679c5b99eL, 0, 0x0L),
    new Qint64(0x808aec4ff9d45430L, 0x84099bf6830f2767L, 0x9a875f4408858619L, 0xdfa0d299eae4aa3aL, 0, 0x0L),
    new Qint64(0x80907e044ffb1984L, 0x3aa8b9cbbc65a8aaL, 0x8b22713e014be438L, 0x47da7d37d3e079f6L, 0, 0x0L),
    new Qint64(0x80960ff66b09d765L, 0xf7d88c0928ba3946L, 0xd1441da0989f9760L, 0xff7e8daa7390655aL, 0, 0x0L),
    new Qint64(0x809ba2264dada76aL, 0x4a8a4f44bb703db6L, 0x212bb24b9d533796L, 0xfcdbcb683daab7f0L, 0, 0x0L),
    new Qint64(0x80a13493fa93c0d4L, 0x6699dc50dd96b773L, 0x8712128a139dc866L, 0xab9445c9a773244fL, 0, 0x0L),
    new Qint64(0x80a6c73f74697897L, 0x6e0472ed4ccfa2dfL, 0xc2857930dae5bef1L, 0x95df5640f17d2dbeL, 0, 0x0L),
    new Qint64(0x80ac5a28bddc4157L, 0xba2dc7e0c72e51baL, 0x6765fb22ac558acaL, 0x9a33e936c809a0e7L, 0, 0x0L),
    new Qint64(0x80b1ed4fd999ab6cL, 0x25335719b6e6fd20L, 0x1f60261b05f1202L, 0x3c355acba4df4faL, 0, 0x0L),
    new Qint64(0x80b780b4ca4f64dfL, 0x534dfa7417846aa4L, 0x68164a4ae2414ea4L, 0x2c64cb5808ef6fa6L, 0, 0x0L),
    new Qint64(0x80bd145792ab3970L, 0xfc41c5c2d5336cccL, 0x65250abea5b33d49L, 0xad82dbaac7bfa2e3L, 0, 0x0L),
    new Qint64(0x80c2a838355b1297L, 0x34dc28baed8f3fdeL, 0x533c9eca3a17497dL, 0xa1b8b14b109d4838L, 0, 0x0L),
    new Qint64(0x80c83c56b50cf77fL, 0xb880575ea03548c1L, 0x4704388d9f1b3cd2L, 0x86f4e188e2ca8a59L, 0, 0x0L),
    new Qint64(0x80cdd0b3146f0d11L, 0x32c1f98704428c71L, 0x7e5ed5955b2d4887L, 0x5b7e292a686df542L, 0, 0x0L),
    new Qint64(0x80d3654d562f95ecL, 0x890e222a5eb95372L, 0x1197e58ebf689d43L, 0x3896d92dd4431f8bL, 0, 0x0L),
    new Qint64(0x80d8fa257cfcf26eL, 0x24628efd9ca9d59aL, 0xc5f4be776ef6a61aL, 0x101735de189170ecL, 0, 0x0L),
    new Qint64(0x80de8f3b8b85a0afL, 0x3b13310f5ad57fb0L, 0x9bad68937edd6b38L, 0xeae4250b29447d4bL, 0, 0x0L),
    new Qint64(0x80e4248f84783c87L, 0x1a9dfefaeb616563L, 0x94426c99024f23f0L, 0x235b5252cafbaa02L, 0, 0x0L),
    new Qint64(0x80e9ba216a837f8cL, 0x718d1151d109bf97L, 0x85189bdd7ac4b012L, 0x9bea88f10391b325L, 0, 0x0L),
    new Qint64(0x80ef4ff140564116L, 0x996709da2e25f04bL, 0xe18453f8dafeabf1L, 0xf5867174289d8d94L, 0, 0x0L),
    new Qint64(0x80f4e5ff089f763eL, 0xe0adc640acaa6b0aL, 0x8b6d28b5eb20d2f2L, 0xb125fb6305bf7e6dL, 0, 0x0L),
    new Qint64(0x80fa7c4ac60e31e1L, 0xd4eb5edc6b341283L, 0x370761b5ce7d7e44L, 0xf1eb5df89b727f7cL, 0, 0x0L),
    new Qint64(0x810012d47b51a4a0L, 0x8ccd7223820719e3L, 0x118525e07f78529cL, 0x97f6dffe47385081L, 0, 0x0L),
    new Qint64(0x8105a99c2b191ce1L, 0xf24ebd6eb9ca4292L, 0x70f4efb7d5c90568L, 0x5301745d3b39c4d0L, 0, 0x0L),
    new Qint64(0x810b40a1d81406d4L, 0xcef03ab14a6654fL, 0xa9c9ffc2ca67ffdeL, 0xada5b6f36036c85aL, 0, 0x0L),
    new Qint64(0x8110d7e584f1ec6dL, 0x4bf94297d1519822L, 0x9ee96b903910b0fL, 0x45fe2b1237a101fcL, 0, 0x0L),
    new Qint64(0x81166f673462756dL, 0xd0d8372f966cf15dL, 0xb70c0ef050a08aa9L, 0xd499953c6b9aa8f0L, 0, 0x0L),
    new Qint64(0x811c0726e9156760L, 0xb97931db7b7be2ecL, 0x135c526104fa1c29L, 0x215ef11d179cc996L, 0, 0x0L),
    new Qint64(0x81219f24a5baa59dL, 0x6abd3b0eab9c7047L, 0xa7712808fe956328L, 0x717f9b1d39438323L, 0, 0x0L),
    new Qint64(0x812737606d023148L, 0xdaf888e965081519L, 0xada38ad7502e18a9L, 0x6ebf0e93981c95f2L, 0, 0x0L),
    new Qint64(0x812ccfda419c2956L, 0xdc8046821f46122dL, 0x8b2f742bd9d4370aL, 0x5718a10a231edabfL, 0, 0x0L),
    new Qint64(0x813268922638ca8bL, 0x6846ad73a8d9027fL, 0x1163a8bcf6bffce3L, 0x2e1b37721d94b76L, 0, 0x0L),
    new Qint64(0x813801881d886f7bL, 0xe885724f14131286L, 0x9cea3c3530355654L, 0xf9c7f1fa9145fa7fL, 0, 0x0L),
    new Qint64(0x813d9abc2a3b9090L, 0x83768490519df895L, 0x605362ea89eb07d4L, 0x4794c4e3bcb98244L, 0, 0x0L),
    new Qint64(0x8143342e4f02c405L, 0x661b22b45e25de17L, 0xa82b3121936ae61dL, 0x91fe35aa6124aefbL, 0, 0x0L),
    new Qint64(0x8148cdde8e8ebdecL, 0xf11430fef78c6eeL, 0x932801def6b0fbL, 0x8aa35adbcc33b28eL, 0, 0x0L),
    new Qint64(0x814e67cceb90502cL, 0x99775205944eadc4L, 0x2555ab2151b96f7cL, 0x1ab6ca4ae6eda941L, 0, 0x0L),
    new Qint64(0x815401f968b86a87L, 0x7de463a40d18260L, 0xdc941f1fd7a051c0L, 0xdddfd0f8f59dec56L, 0, 0x0L),
    new Qint64(0x81599c6408b81a94L, 0x8f4a0b6748df795fL, 0x988da3f28bde163dL, 0x951855dd23786b9cL, 0, 0x0L),
    new Qint64(0x815f370cce408bc8L, 0xe2404468cfe5ab9fL, 0x4db5f07dc6319207L, 0x221ce2379e877086L, 0, 0x0L),
};

  private static final Qint64[] P_3 = {
    new Qint64(0xe38e39d490f62b2fL, 0x0L, 0x0L, 0x0L, -5, 0x1L), /* degree 18 */
    new Qint64(0xf0f0f1e1e1d4e1cfL, 0xbbb343000334fd0fL, 0x0L, 0x0L, -5, 0x0L), /* degree 17 */
    new Qint64(0xffffffffffff88b8L, 0xc17633c5a3181e76L, 0x0L, 0x0L, -5, 0x1L), /* degree 16 */
    new Qint64(0x8888888888885088L, 0x8f6a4426b02f93beL, 0x0L, 0x0L, -4, 0x0L), /* degree 15 */
    new Qint64(0x9249249249249249L, 0x24a2676c009fc980L, 0x0L, 0x0L, -4, 0x1L), /* degree 14 */
    new Qint64(0x9d89d89d89d89d89L, 0xd8ab89d5a96621f1L, 0x0L, 0x0L, -4, 0x0L), /* degree 13 */
    new Qint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaa815192aL, 0x0L, 0x0L, -4, 0x1L), /* degree 12 */
    new Qint64(0xba2e8ba2e8ba2e8bL, 0xa2e8ba2e899ae964L, 0x0L, 0x0L, -4, 0x0L), /* degree 11 */
    new Qint64(0xccccccccccccccccL, 0xcccccccccccccccdL, 0xcc491481418dc51L, 0x1cebff4e21be093eL, -4, 0x1L), /* degree 10 */
    new Qint64(0xe38e38e38e38e38eL, 0x38e38e38e38e38e3L, 0xba38ce3dcedbed7dL, 0xfa2cc6f77565683fL, -4, 0x0L), /* degree 9 */
    new Qint64(0xffffffffffffffffL, 0xffffffffffffffffL, 0xfffffffc6072860aL, 0x786bd58754911c58L, -4, 0x1L), /* degree 8 */
    new Qint64(0x9249249249249249L, 0x2492492492492492L, 0x492492481c930545L, 0xf0298bcd6e1b2310L, -3, 0x0L), /* degree 7 */
    new Qint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaab8L, 0xd8b61a619485f089L, -3, 0x1L), /* degree 6 */
    new Qint64(0xccccccccccccccccL, 0xccccccccccccccccL, 0xccccccccccccccd2L, 0xccc65d183d01d5efL, -3, 0x0L), /* degree 5 */
    new Qint64(0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffcdc3a6a23cL, -3, 0x1L), /* degree 4 */
    new Qint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0xaaaaaaa4aab50b70L, -2, 0x0L), /* degree 3 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0xdL, -1, 0x1L), /* degree 2 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* degree 1 */
};

  private static final Qint64[] Q_3 = {
    new Qint64(0xc9cba547af749429L, 0x0L, 0x0L, 0x0L, -37, 0x0L), /* degree 14 */
    new Qint64(0xb092309ec73dd7dbL, 0x0L, 0x0L, 0x0L, -33, 0x0L), /* degree 13 */
    new Qint64(0x8f76c77fc6c4bda8L, 0xcd9aab7578033f6dL, 0x0L, 0x0L, -29, 0x0L), /* degree 12 */
    new Qint64(0xd7322b3faa271c7dL, 0xb3537cbfd60dcb9L, 0x0L, 0x0L, -26, 0x0L), /* degree 11 */
    new Qint64(0x93f27dbbc4fae397L, 0x780b69f6554de3d9L, 0x0L, 0x0L, -22, 0x0L), /* degree 10 */
    new Qint64(0xb8ef1d2ab6399c7dL, 0x560e44741a6a8e66L, 0x0L, 0x0L, -19, 0x0L), /* degree 9 */
    new Qint64(0xd00d00d00d00d00dL, 0xd00d00d00d00cfL, 0x0L, 0x0L, -16, 0x0L), /* degree 8 */
    new Qint64(0xd00d00d00d00d00dL, 0xd00d00d00d00ceL, 0xca5a80878f19216bL, 0xd236a7fa15252936L, -13, 0x0L), /* degree 7 */
    new Qint64(0xb60b60b60b60b60bL, 0x60b60b60b60b60b6L, 0xb60b60be6ac2e60L, 0x800a9987617257e3L, -10, 0x0L), /* degree 6 */
    new Qint64(0x8888888888888888L, 0x8888888888888888L, 0x888888890ac16c5aL, 0xf78e687c535a714L, -7, 0x0L), /* degree 5 */
    new Qint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0x6b3ad4c251cd03d5L, -5, 0x0L), /* degree 4 */
    new Qint64(0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaaaaaaaL, 0x4df8c3de374c499eL, -3, 0x0L), /* degree 3 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x1446e270L, -1, 0x0L), /* degree 2 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x262ce809L, 0, 0x0L), /* degree 1 */
    new Qint64(0x8000000000000000L, 0x0L, 0x0L, 0x0L, 0, 0x0L), /* degree 0 */
};

  // Extract both the mantissa and exponent of a double
  private static void fastExtract(Dint64 a, double x) {
    long u = Double.doubleToRawLongBits(x);

    a.ex = (u >>> 52) & 0x7ff;
    a.hi = (u & (~0L >>> 12)) + (a.ex != 0 ? (1L << 52) : 0);
    a.ex = a.ex - 0x3ff;
  }

  // The same for a qint64_t, whose upper limb is called hh
  private static void fastExtract(Qint64 a, double x) {
    long u = Double.doubleToRawLongBits(x);

    a.ex = (u >>> 52) & 0x7ff;
    a.hh = (u & (~0L >>> 12)) + (a.ex != 0 ? (1L << 52) : 0);
    a.ex = a.ex - 0x3ff;
  }

  // When x is a NaN, returns 1 if x is an sNaN and 0 if it is a qNaN
  private static int isSignaling(double x) {
    long u = Double.doubleToRawLongBits(x);

    return (u & (1L << 51)) == 0 ? 1 : 0;
  }

  /* Add a + b, such that *hi + *lo approximates a + b.
     Assumes |a| >= |b|.
     For rounding to nearest we have hi + lo = a + b exactly.
     For directed rounding, we have
     (a) hi + lo = a + b exactly when the exponent difference between a and b
         is at most 53 (the binary64 precision)
     (b) otherwise |(a+b)-(hi+lo)| <= 2^-105 min(|a+b|,|hi|)
         (see https://hal.inria.fr/hal-03798376)
     We also have |lo| < ulp(hi). */
  private static void fastTwoSum(double[] hi, double[] lo, double a, double b) {
    double e;

    // assert (a == 0 || __builtin_fabs (a) >= __builtin_fabs (b));
    hi[0] = a + b;
    e = hi[0] - a; /* exact */
    lo[0] = b - e; /* exact */
  }

  // Add a + (bh + bl), assuming |a| >= |bh|
  private static void fastSum(double[] hi, double[] lo, double a, double bh,
                              double bl) {
    fastTwoSum(hi, lo, a, bh);
    /* |(a+bh)-(hi+lo)| <= 2^-105 |hi| and |lo| < ulp(hi) */
    lo[0] += bl;
    /* |(a+bh+bl)-(hi+lo)| <= 2^-105 |hi| + ulp(lo),
       where |lo| <= ulp(hi) + |bl|. */
  }

  // Multiply exactly a and b, such that *hi + *lo = a * b.
  private static void aMul(double[] hi, double[] lo, double a, double b) {
    hi[0] = a * b;
    lo[0] = Math.fma(a, b, -hi[0]);
  }

  // Multiply a double with a double double : a * (bh + bl)
  private static void sMul(double[] hi, double[] lo, double a, double bh,
                           double bl) {
    double s;

    aMul(hi, lo, a, bh); /* exact */
    lo[0] = Math.fma(a, bl, lo[0]);
    /* the error is bounded by ulp(lo), where |lo| < |a*bl| + ulp(hi) */
  }

  // Returns (ah + al) * (bh + bl) - (al * bl)
  // We can ignore al * bl when assuming al <= ulp(ah) and bl <= ulp(bh)
  private static void dMul(double[] hi, double[] lo, double ah, double al,
                           double bh, double bl) {
    double s, t;

    aMul(hi, lo, ah, bh);
    t = Math.fma(al, bh, lo[0]);
    lo[0] = Math.fma(ah, bl, t);
  }

  // Returns 1 if x is an integer
  private static int isInt(double x) { return x == Math.rint(x) ? 1 : 0; }

  // Returns (e, m) such that m is odd and x = 2^E \times m
  private static void extract(long[] e, long[] m, double x) {
    long u = Double.doubleToRawLongBits(x);

    e[0] = (u >>> 52) & 0x7ff;
    m[0] = (u & (~0L >>> 12)) + (e[0] != 0 ? (1L << 52) : 0);
    int t = Long.numberOfTrailingZeros(m[0]);
    m[0] = m[0] >>> t;
    e[0] = e[0] + t - (0x433 - (e[0] == 0 ? 1 : 0));
  }

  // Rounds a dint64_t value to 54 bits, a shortcut is taken as in `exact_pow`, we
  // only consider numbers that end with only ones or only zeroes
  private static void round54(long[] G, long[] k, Dint64 x) {
    G[0] = x.ex - 53;
    k[0] = (x.hi >>> 10) + ((x.hi >>> 9) & 0x1);
  }

  // Multiply x by 2^e
  private static void pow2(double[] x, long e) {
    if ((e & 0x1) != 0)
      x[0] *= 0x1p+1;

    double e2 = Double.longBitsToDouble((((e >> 1) + 0x3ff) & 0x7ff) << 52);
    x[0] = (x[0] * e2) * e2;
  }

  // Convert a dint64_t value to an integer, rounding towards zero
  private static long dintToi(Dint64 a) {
    if (a.ex < 0)
      return 0L;

    long r = a.hi >>> (int) (63 - a.ex);

    return a.sgn != 0 ? -r : r;
  }

  // round a, assuming a is in the subnormal range
  // exact is non-zero iff x^y is exact
  private static double dintTodSubnormal(Dint64 a, int exact) {
    int underflow = 1;
    double ret = 0;

    long ex = -(1011 + a.ex); // ex >= 12
    // we have to shift right hi,lo by ex bits so that the least significant
    // bit of hi corresponds to 2^-1074 (the number of extra bits is
    // -1022 - a->ex, and we add 11 = 64 - 53 since hi has 64 bits)

    long rb, sb;

    if (Long.compareUnsigned(ex, 64) >= 0) { // all bits disappear: |a| < 2^-1074
      /* The C code switches on fegetround() here and handles FE_TONEAREST,
         FE_DOWNWARD, FE_UPWARD and FE_TOWARDZERO. The JVM has no floating-point
         environment and round-to-nearest-even is the only mode, so only the
         FE_TONEAREST case is translated; the directed modes are out of scope in
         AGENTS.md. */
      rb = (a.hi >>> 63);        // only used when e=64
      sb = (a.hi << 1) | a.lo;   // idem
      ret = (ex > 64 || rb == 0 || sb == 0) ? +0.0 : 0x1p-1074;
      ret = (a.sgn != 0) ? -ret : ret;
    } else {
      // now ex < 64
      long hi;
      hi = a.hi >>> (int) ex;
      rb = (a.hi >>> (int) (ex - 1)) & 0x1; // round bit
      sb = (a.hi << (int) (65 - ex)) | a.lo; // sticky bit

      // if ex=12 there is no underflow when hi rounds to 2^52 and rb=1
      // and the next bit is 1 too
      hi += sb != 0 ? rb : hi & rb;
      if (ex == 12 && (hi >>> 52) != 0 && rb != 0)
      {
        long rbb = (a.hi >>> (int) (ex - 2)) & 0x1; // next bit after the round bit
        if (rbb != 0)
          underflow = 0;
      }

      // now hi <= 2^52 stores the low bits of the result (up to sign)
      // (if hi has overflowed in 2^52 this is exactly what we want)

      ret = Double.longBitsToDouble(hi | (a.sgn << 63));
    }

    if (underflow != 0 && exact == 0) {
      // the C source raises FE_UNDERFLOW here
    }

    return ret;
  }

  // Convert a dint64_t value to a double
  // exact is non-zero iff x^y is exact
  private static double dintTod(Dint64 a, int exact) {
    if (a.ex < -1022)
      return dintTodSubnormal(a, exact);

    // r is the significand in [1,2)
    double r = Double.longBitsToDouble((a.hi >>> 11) | (0x3ffL << 52));

    // round r
    double rd = 0.0;
    if (((a.hi >>> 10) & 0x1) != 0)
      rd += 0x1p-53;

    if ((a.hi & 0x3ff) != 0 || a.lo != 0)
      rd += 0x1p-54;

    if (a.sgn != 0)
      rd = -rd;

    r = Double.longBitsToDouble(Double.doubleToRawLongBits(r) | (a.sgn << 63));
    r += rd;

    double e;

    if (a.ex > -1023) { // The result is a normal double
      if (a.ex > 1023) {
        if (a.ex == 1024) { // 2^1024 <= |a| < 2^1025
          r = r * 0x1p+1;
          e = 0x1p+1023;
        } else { // |a| >= 2^1025
          r = 0x1.fffffffffffffp+1023;
          e = 0x1.fffffffffffffp+1023;
        }
      }
      else
        e = Double.longBitsToDouble(((a.ex + 1023) & 0x7ff) << 52);
    } else { // subnormal case
      // the C source raises FE_UNDERFLOW here when exact is zero
      if (a.ex < -1074) {
        if (a.ex == -1075) {
          r = r * 0x1p-1;
          e = 0x1p-1074;
        } else {
          r = 0x0.0000000000001p-1022;
          e = 0x0.0000000000001p-1022;
        }
      } else {
        e = Double.longBitsToDouble(1L << (int) (a.ex + 1074));
      }
    }

    return r * e;
  }

  // Convert a double to the corresponding qint64_t value
  private static void qintFromd(Qint64 a, double b) {
    fastExtract(a, b);

    /* |b| = 2^(ex-52)*hi */

    int t = Long.numberOfLeadingZeros(a.hh);

    a.sgn = b < 0.0 ? 1 : 0;
    a.ex = a.ex - (t > 11 ? t - 12 : 0);
    a.hh = a.hh << t;
    a.lh = 0;
    a.hl = 0;
    a.ll = 0;
    /* b = 2^ex*hh/2^64 where 1 <= hh/2^63 < 2 */
  }

  // Convert a qint64_t value to an integer
  private static long qintToi(Qint64 a) {
    if (a.ex < 0)
      return 0L;

    long r = a.hh >>> (int) (63 - a.ex);

    return a.sgn != 0 ? -r : r;
  }

  private static void subnormalizeQint(Qint64 a) {
    if (a.ex > -1023)
      return;

    long ex = -(1011 + a.ex);

    long hi = a.hh >>> (int) ex;
    long md = (a.hh >>> (int) (ex - 1)) & 0x1;
    long lo = ((a.hh & (~0L >>> (int) ex)) != 0 || a.hl != 0 || a.lh != 0
               || a.ll != 0) ? 1 : 0;

    /* As in subnormalizeDint: only the FE_TONEAREST case of the C switch on
       fegetround() is translated, the directed modes are out of scope. */
    hi += lo != 0 ? md : hi & md;

    a.hh = hi << (int) ex;
    a.hl = 0;
    a.lh = 0;
    a.ll = 0;

    if (a.hh == 0) {
      a.ex++;
      a.hh = (1L << 63);
    }
  }

  // Convert a qint64_t value to a double
  private static double qintTod(Qint64 a) {
    subnormalizeQint(a);

    double r = Double.longBitsToDouble((a.hh >>> 11) | (0x3ffL << 52));

    double rd = 0.0;
    if ((a.hh & 0x400) != 0)
      rd += 0x1p-53;

    if ((a.hh & 0x3ff) != 0 || a.hl != 0 || a.lh != 0 || a.ll != 0)
      rd += 0x1p-54;

    if (a.sgn != 0)
      rd = -rd;

    r = Double.longBitsToDouble(Double.doubleToRawLongBits(r) | (a.sgn << 63));
    r += rd;

    double e;

    if (a.ex > -1023) { // The result is a normal double
      if (a.ex > 1023)
        if (a.ex == 1024) {
          r = r * 0x1p+1;
          e = 0x1p+1023;
        } else {
          r = 0x1.fffffffffffffp+1023;
          e = 0x1.fffffffffffffp+1023;
        }
      else
        e = Double.longBitsToDouble(((a.ex + 1023) & 0x7ff) << 52);
    } else { // subnormal case
      // the C source raises FE_UNDERFLOW here
      if (a.ex < -1074) {
        if (a.ex == -1075) {
          r = r * 0x1p-1;
          e = 0x1p-1074;
        } else {
          r = 0x0.0000000000001p-1022;
          e = 0x0.0000000000001p-1022;
        }
      } else {
        e = Double.longBitsToDouble(1L << (int) (a.ex + 1074));
      }
    }

    return r * e;
  }

  // Copy a qint64_t value
  private static void cpQint(Qint64 r, Qint64 a) {
    r.ex = a.ex;
    r.rh(a.rh());
    r.rl(a.rl());
    r.sgn = a.sgn;
  }

  /* Compare the absolute values of a and b:
     return +1 if |a| > |b|, 0 if |a| = |b|, -1 if |a| < |b| */
  private static int cmpQint(Qint64 a, Qint64 b) {
    return cmp(a.ex, b.ex) != 0 ? cmp(a.ex, b.ex)
        : cmpu128(a.rh(), b.rh()) != 0 ? cmpu128(a.rh(), b.rh())
        : cmpu128(a.rl(), b.rl());
  }

  /* same as cmp_qint, but only compare the upper 2 limbs */
  private static int cmpQint22(Qint64 a, Qint64 b) {
    return cmp(a.ex, b.ex) != 0 ? cmp(a.ex, b.ex)
        : cmpu128(a.rh(), b.rh());
  }

  /* Add two qint64_t values, with error bounded by 2 ulps (ulp_256).
     If Sterbenz theorem applies, i.e., a and b are of opposite signs
     with |a|/2 <= |b| <= |a|, then the operation is exact. */
  private static void addQint(Qint64 r, Qint64 a, Qint64 b) {
    if ((a.hl | a.hh) == 0 && (a.ll | a.lh) == 0) {
      cpQint(r, b); // exact
      return;
    }

    if ((b.hl | b.hh) == 0 && (b.ll | b.lh) == 0) {
      cpQint(r, a); // exact
      return;
    }

    /* compare the absolute values of a and b */
    switch (cmpQint(a, b)) {
    case 0: /* |a| = |b| */
      if ((a.sgn ^ b.sgn) != 0) { /* signs differ */
        cpQint(r, ZERO_Q); // exact
        return;
      }

      cpQint(r, a);
      r.ex++; // exact
      return;

    case -1: /* |a| < |b| */
      addQint(r, b, a);
      return;
    }

    // From now on, |A| > |B|

    U128 ah = a.rh(), al = a.rl(), bh = b.rh(), bl = b.rl();

    long m_ex = a.ex;
    long k = a.ex - b.ex;

    if (k > 0) {
      if (k >= 128) {
        bl = Long.compareUnsigned(k, 256) < 0 ? bh.shr((int) (k - 128)) : U128.of(0, 0);
        bh = U128.of(0, 0);
      } else { /* 1 <= k <= 127 */
        bl = bl.shr((int) k).or(bh.shl((int) (128 - k)));
        bh = bh.shr((int) k);
      }
    }

    /* now we have to add (ah,al) + (bh,bl), with error <= 1 ulp
       corresponding to the ignored part of (bh,bl) */

    long sgn = a.sgn;
    long ex;
    U128 ch, cl;
    U128[] chp = new U128[1], clp = new U128[1];

    r.ex = m_ex;

    if ((a.sgn ^ b.sgn) != 0) { // subtraction case
      /* a and b have different signs: C = A + (-B) */
      ch = ah.sub(bh);

      if (subu128(al, bl, clp) != 0)
        ch = ch.sub(U128.of(1, 0));
      cl = clp[0];
      /* we cannot have C=0 since |A| > |B| */
      long chh = ch.hi, clh = cl.hi;
      ex =
        chh != 0 ? Long.numberOfLeadingZeros(chh)
        : 64 + ((ch.hi | ch.lo) != 0 ? Long.numberOfLeadingZeros(ch.lo)
                : 64 + (clh != 0 ? Long.numberOfLeadingZeros(clh)
                        : 64 + Long.numberOfLeadingZeros(cl.lo)));
      /* ex < 256 since |A| > |B| */

      /* If ex=0 or ex=1, the rounding error is bounded by 2 ulps. */
      if (ex > 0)
        {
          /* shift A by ex bits to the left, and B by ex-k bits to the left */
          if (ex >= 128)
            {
              ah = al.shl((int) (ex - 128));
              al = U128.of(0, 0);
            }
          else /* 1 <= ex < 128 */
            {
              ah = ah.shl((int) ex).or(al.shr((int) (128 - ex)));
              al = al.shl((int) ex);
            }
          int sh = (int) (ex - k);
          bh = b.rh();
          bl = b.rl();
          if (sh >= 0) {
            if (sh >= 128) {
              bh = bl.shl(sh - 128);
              bl = U128.of(0, 0);
            }
            else if (sh > 0) { /* 1 <= sh < 128 */
              bh = bh.shl(sh).or(bl.shr(128 - sh));
              bl = bl.shl(sh);
            }
          }
          else { /* sh < 0: shift b by -sh bits to the right */
            int j = -sh;
            if (j >= 128) {
              bl = bh.shr(j - 128);
              bh = U128.of(0, 0);
            }
            else { /* 0 < j < 128 (j cannot be 0 since sh < 0) */
              bl = bh.shl(128 - j).or(bl.shr(j));
              bh = bh.shr(j);
            }
          }
          r.ex -= ex;
          ch = ah.sub(bh);

          if (subu128(al, bl, clp) != 0)
            ch = ch.sub(U128.of(1, 0));
          cl = clp[0];
          /* we cannot have C=0 since |A| > |B| */
          chh = ch.hi;
          clh = cl.hi;
          ex =
            chh != 0 ? Long.numberOfLeadingZeros(chh)
            : 64 + ((ch.hi | ch.lo) != 0 ? Long.numberOfLeadingZeros(ch.lo)
                    : 64 + (clh != 0 ? Long.numberOfLeadingZeros(clh)
                            : 64 + Long.numberOfLeadingZeros(cl.lo)));
        }
      if (ex != 0) {
        ch = ch.shl((int) ex).or(cl.shr((int) (128 - ex)));
        cl = cl.shl((int) ex);
      }
      r.ex -= ex;
      /* We distinguish three cases according to the first value of ex:
         If ex=0, the error is bounded by 1 ulp (ignored part of B).
         If ex=1, the error is bounded by 2 ulps (ignored part of B
           multiplied by 2).
         In the case ex>1, the error is bounded by 1 ulp (truncated part of B),
         which might be multiplied by 2 if the final value of ex is 1.
      */
    } else { // addition case
      int cy = addu128(ah, bh, chp);
      ch = chp[0];

      if (addu128(al, bl, clp) != 0) {
        ch = ch.add(U128.of(1, 0));
        cy += ((ch.hi | ch.lo) == 0) ? 1 : 0;
      }
      cl = clp[0];

      /* 0 <= cy <= 1 */

      if (cy != 0) { // carry in the 256-bit addition
        cl = ch.shl(127).or(cl.shr(1));
        ch = U128.of(0, 1L << 63).or(ch.shr(1));
        r.ex++;
      }
      /* In the addition case, the rounding error is bounded by 1 ulp. */
    }

    r.sgn = sgn;
    r.rh(ch);
    r.rl(cl);
  }

  /* same as add_qint, but only considers the upper 2 limbs of a and b,
     with rounding error < 2 ulps(128) */
  private static void addQint22(Qint64 r, Qint64 a, Qint64 b) {
    if ((a.hl | a.hh) == 0) {
      cpQint(r, b); // exact
      return;
    }

    if ((b.hl | b.hh) == 0) {
      cpQint(r, a); // exact
      return;
    }

    /* compare the absolute values of a and b */
    switch (cmpQint22(a, b)) {
    case 0: /* |a| = |b| */
      if ((a.sgn ^ b.sgn) != 0) { /* signs differ */
        cpQint(r, ZERO_Q); // exact
        return;
      }

      cpQint(r, a);
      r.ex++; // exact
      return;

    case -1: /* |a| < |b| */
      addQint22(r, b, a);
      return;
    }

    // From now on, |A| > |B|

    U128 ah = a.rh(), bh = b.rh();

    long m_ex = a.ex;
    long k = a.ex - b.ex;

    if (k > 0)
      bh = Long.compareUnsigned(k, 128) >= 0 ? U128.of(0, 0) : bh.shr((int) k);

    /* now we have to add ah + bh, with error <= 1 ulp
       corresponding to the ignored part of bh */

    long sgn = a.sgn;
    long ex;
    U128 ch;
    U128[] chp = new U128[1];

    r.ex = m_ex;

    if ((a.sgn ^ b.sgn) != 0) { // subtraction case
      /* a and b have different signs: C = A + (-B) */
      ch = ah.sub(bh);

      /* we cannot have ch=0 since |A| > |B| */
      long chh = ch.hi;
      ex = chh != 0 ? Long.numberOfLeadingZeros(chh)
           : 64 + Long.numberOfLeadingZeros(ch.lo);

      /* ex < 128 since |A| > |B| */

      if (ex > 0)
        {
          /* shift A and B by ex bits to the left */
          ah = ah.shl((int) ex);
          /* for B, we have to shift by k bits to the right and ex to the left */
          if (ex >= k)
            bh = b.rh().shl((int) (ex - k)); // since ex < 128, the shift is well defined
          else
            bh = b.rh().shr((int) (k - ex));
          /* since k < 128 (otherwise bh=0 and ch=ah thus ex=0), this shift is
             also well defined */
          r.ex -= ex;
          ch = ah.sub(bh);

          /* we cannot have C=0 since |A| > |B| */
          chh = ch.hi;
          ex = chh != 0 ? Long.numberOfLeadingZeros(chh)
               : 64 + Long.numberOfLeadingZeros(ch.lo);
          /* rounding error is bounded by 1 ulp(128) */
        }
      ch = ch.shl((int) ex);
      /* if ex=1, the rounding error is multiplied by 2, thus < 2 ulp(128) */
      r.ex -= ex;
    } else { // addition case
      int cy = addu128(ah, bh, chp);
      ch = chp[0];

      if (cy != 0) { // carry in the 128-bit addition
        ch = U128.of(0, 1L << 63).or(ch.shr(1));
        r.ex ++;
      }
      /* In the addition case, the rounding error is bounded by 1 ulp(128) */
    }

    r.sgn = sgn;
    r.rh(ch);
    r.rl(U128.of(0, 0));
  }

  // Multiply two dint64_t numbers, with error < 14 ulps
  private static void mulQint(Qint64 r, Qint64 a, Qint64 b) {
    U128 r33 = U128.mul(a.hh, b.hh);

    U128 r32 = U128.mul(a.hh, b.hl);
    U128 r23 = U128.mul(a.hl, b.hh);

    U128 r31 = U128.mul(a.hh, b.lh);
    U128 r13 = U128.mul(a.lh, b.hh);
    U128 r22 = U128.mul(a.hl, b.hl);

    U128 r30 = U128.mul(a.hh, b.ll);
    U128 r03 = U128.mul(a.ll, b.hh);
    U128 r21 = U128.mul(a.hl, b.lh);
    U128 r12 = U128.mul(a.lh, b.hl);

    U128 t4, t5;
    U128[] t4p = new U128[1], t5p = new U128[1];
    long c5, c4;

    U128 t3 = r12.shr(64).add(r21.shr(64)).add(r03.shr(64)).add(r30.shr(64));
    /* no overflow since each term is < 2^64, thus the sum < 2^66 */

    /* t3 is the sum of the terms of "degree" 3 divided by 2^64 */

    c4 = addu128(r22, t3, t4p);
    c4 += addu128(r13, t4p[0], t4p);
    c4 += addu128(r31, t4p[0], t4p);
    t4 = t4p[0];

    /* (c4:1,t4:128) is the sum of the terms of "degree" 3 and 4 */

    c5 = addu128(r23, t4.shr(64), t5p);
    c5 += addu128(r32, t5p[0], t5p);
    t5 = t5p[0];

    /* (c5:1,t5:128,low(t4):64) is the sum of the terms of "degree" 3 to 5 */

    U128 t6 = r33.add(U128.of(0, c5).or(t5.shr(64))).addLow(c4);

    /* (t6:128,low(t5):64,low(t4):64) is the sum of the terms of "degree" 3-6 */

    /* No carry can happen since the full product of the significands is
       bounded by 2^512.
       The approximated sum is:
       t6 (128 bits) + low(t5) (64 bits) + low(t4) (64 bits) + low(t3) (64 bits)
       with error bounded by:
       * 3*(B-1)^2/B^2 ulp for the neglected terms of "degree" 2: r20 + r11 + r02
       * 2*(B-1)^2/B^3 ulp for the neglected terms of "degree" 1: r10 + r01
       * 1*(B-1)^2/B^4 ulp for the neglected term of "degree" 0: r00
       * 1 ulp for each of the neglected low parts of r12, r21, r03 and r30
         thus 4 ulps in total
       The sum of the first three terms is less than 3, thus bounded by 3 ulps.
       This yields an error bound of 7 ulps so far.
    */

    long ex = t6.shr(127).lo == 0 ? 1 : 0;

    t5 = t5.shl(64).or(t4.and(U128.of(0xffffffffffffffffL, 0)));
    if (ex != 0) { /* ex=1 */
      r.rh(t6.shl(1).or(t5.shr(127)));
      r.rl(t5.shl(1));
      /* the previous rounding error is multiplied by 2, thus < 14 ulps now */
    }
    else { /* ex=0 */
      r.rh(t6);
      r.rl(t5);
      /* error < 7 ulps */
    }

    r.ex = a.ex + b.ex + 1 - ex;

    r.sgn = a.sgn ^ b.sgn;
  }

  /* same as mul_qint, but considering only the upper limb from b,
     and with error < 2 ulps */
  private static void mulQint41(Qint64 r, Qint64 a, Qint64 b) {
    U128 r33 = U128.mul(a.hh, b.hh);

    U128 r23 = U128.mul(a.hl, b.hh);

    U128 r13 = U128.mul(a.lh, b.hh);

    U128 r03 = U128.mul(a.ll, b.hh);

    U128 t4, t5;
    U128[] t4p = new U128[1], t5p = new U128[1];
    long c5, c4;

    U128 t3 = r03.shr(64);

    /* t3 is the term of "degree" 3 divided by 2^64 */

    c4 = addu128(r13, t3, t4p);
    t4 = t4p[0];

    /* (c4:1,t4:128) is the sum of the terms of "degree" 3 and 4 */

    c5 = addu128(r23, t4.shr(64), t5p);
    t5 = t5p[0];

    /* (c5:1,t5:128,low(t4):64) is the sum of the terms of "degree" 3 to 5 */

    U128 t6 = r33.add(U128.of(0, c5).or(t5.shr(64))).addLow(c4);

    /* (t6:128,low(t5):64,low(t4):64) is the sum of the terms of "degree" 3-6 */

    /* No carry can happen since the full product of the significands is
       bounded by 2^512.
       The approximated sum is:
       t6 (128 bits) + low(t5) (64 bits) + low(t4) (64 bits) + low(t3) (64 bits)
       with error bounded by 1 ulp for the neglected low part of r03.
    */

    long ex = t6.shr(127).lo == 0 ? 1 : 0;

    t5 = t5.shl(64).or(t4.and(U128.of(0xffffffffffffffffL, 0)));
    if (ex != 0) { /* ex=1 */
      r.rh(t6.shl(1).or(t5.shr(127)));
      r.rl(t5.shl(1));
      /* the previous rounding error is multiplied by 2, thus < 2 ulps now */
    }
    else { /* ex=0 */
      r.rh(t6);
      r.rl(t5);
      /* error < 1 ulp */
    }

    r.ex = a.ex + b.ex + 1 - ex;

    r.sgn = a.sgn ^ b.sgn;
  }

  /* same as mul_qint, but considering only the 3 upper limbs from a,
     and the upper limb from b, with no error (exact product) */
  private static void mulQint31(Qint64 r, Qint64 a, Qint64 b) {
    U128 r33 = U128.mul(a.hh, b.hh);
    U128 r23 = U128.mul(a.hl, b.hh);
    U128 r13 = U128.mul(a.lh, b.hh);

    U128 t5;
    U128[] t5p = new U128[1];
    long c5;

    U128 t4 = r13;

    /* t4 is the only term of "degree" 4 */

    c5 = addu128(r23, t4.shr(64), t5p);
    t5 = t5p[0];

    /* (c5:1,t5:128,low(t4):64) is the sum of the terms of "degree" 4 to 5 */

    U128 t6 = r33.add(U128.of(0, c5).or(t5.shr(64)));

    /* (t6:128,low(t5):64,low(t4):64) is the sum of the terms of "degree" 4-6 */

    /* No carry can happen since the full product of the significands is
       bounded by 2^512.
       The approximated sum is:
       t6 (128 bits) + low(t5) (64 bits) + low(t4) (64 bits)
       with no error.
    */

    long ex = t6.shr(127).lo == 0 ? 1 : 0;

    t5 = t5.shl(64).or(t4.and(U128.of(0xffffffffffffffffL, 0)));
    if (ex != 0) { /* ex=1 */
      r.rh(t6.shl(1).or(t5.shr(127)));
      r.rl(t5.shl(1));
      /* the previous rounding error is multiplied by 2, thus < 2 ulps now */
    }
    else { /* ex=0 */
      r.rh(t6);
      r.rl(t5);
      /* error < 1 ulp */
    }

    r.ex = a.ex + b.ex + 1 - ex;

    r.sgn = a.sgn ^ b.sgn;
  }

  /* same as mul_qint, but considering only the 2 upper limbs from a and b,
     with no error (exact product) */
  private static void mulQint22(Qint64 r, Qint64 a, Qint64 b) {
    U128 r33 = U128.mul(a.hh, b.hh);

    U128 r32 = U128.mul(a.hh, b.hl);
    U128 r23 = U128.mul(a.hl, b.hh);

    U128 r22 = U128.mul(a.hl, b.hl);

    U128 t5;
    U128[] t5p = new U128[1];
    long c5;

    U128 t4 = r22;

    c5 = addu128(r23, t4.shr(64), t5p);
    c5 += addu128(r32, t5p[0], t5p);
    t5 = t5p[0];

    /* (c5:1,t5:128,low(t4):64) is the sum of the terms of "degree" 3 to 5 */

    U128 t6 = r33.add(U128.of(0, c5).or(t5.shr(64)));

    /* (t6:128,low(t5):64,low(t4):64) is the sum of the terms of "degree" 3-6 */

    /* No carry can happen since the full product of the significands is
       bounded by 2^512.
       The exact sum is:
       t6 (128 bits) + low(t5) (64 bits) + low(t4) (64 bits)
    */

    long ex = t6.shr(127).lo == 0 ? 1 : 0;

    t5 = t5.shl(64).or(t4.and(U128.of(0xffffffffffffffffL, 0)));
    if (ex != 0) { /* ex=1 */
      r.rh(t6.shl(1).or(t5.shr(127)));
      r.rl(t5.shl(1));
    }
    else { /* ex=0 */
      r.rh(t6);
      r.rl(t5);
    }

    r.ex = a.ex + b.ex + 1 - ex;

    r.sgn = a.sgn ^ b.sgn;
  }

  /* same as mul_qint, but considering only the 2 upper limbs from a, and the
     upper limb of b, with no error (exact product) */
  private static void mulQint21(Qint64 r, Qint64 a, Qint64 b) {
    U128 r33 = U128.mul(a.hh, b.hh);
    U128 r23 = U128.mul(a.hl, b.hh);

    U128 t6 = r33.add(r23.shr(64));

    /* No carry can happen since the full product of the significands is
       bounded by 2^512.
    */

    long ex = t6.shr(127).lo == 0 ? 1 : 0;

    U128 t5 = r23.shl(64);
    if (ex != 0) { /* ex=1 */
      r.rh(t6.shl(1).or(t5.shr(127)));
      r.rl(t5.shl(1));
    }
    else { /* ex=0 */
      r.rh(t6);
      r.rl(t5);
    }

    r.ex = a.ex + b.ex + 1 - ex;

    r.sgn = a.sgn ^ b.sgn;
  }

  /* same as mul_qint, but considering only the upper limb from a and b,
     with no error (exact product) */
  private static void mulQint11(Qint64 r, Qint64 a, Qint64 b) {
    U128 t6 = U128.mul(a.hh, b.hh);
    long ex = t6.shr(127).lo == 0 ? 1 : 0;

    /* ex can be 0 or 1 */

    r.rh(t6.shl((int) ex));
    r.rl(U128.of(0, 0));
    r.ex = a.ex + b.ex + 1 - ex;
    r.sgn = a.sgn ^ b.sgn;
  }

  // Multiply an integer with a qint64_t variable, with error < 2 ulps
  private static void mulQint2(Qint64 r, long b, Qint64 a) {
    if (b == 0) {
      cpQint(r, ZERO_Q); // exact
      return;
    }

    long c = b < 0 ? -b : b;
    if (c == 1) {
      cpQint(r, a); // exact
      r.sgn = (b < 0 ? 1 : 0) ^ a.sgn;

      return;
    }

    r.sgn = (b < 0 ? 1 : 0) ^ a.sgn;
    r.ex = a.ex + 64;

    /* scale c so that 2^63 <= c < 2^64 */
    int k = Long.numberOfLeadingZeros(c);
    c = c << k;
    r.ex -= k;

    U128 t3 = U128.mul(a.hh, c);
    U128 t2 = U128.mul(a.hl, c);
    U128 t1 = U128.mul(a.lh, c);
    U128 t0 = U128.mul(a.ll, c);

    int cy;
    U128 t = t0.shr(64);
    U128[] t1p = new U128[1];
    U128[] t2p = new U128[1];

    /* t:64 is the term of degree 0 (divided by 2^64) */

    cy = addu128(t, t1, t1p);
    t1 = t1p[0];
    /* (cy:1,t1:128) is the sum of the terms of degree 0 and 1 */

    t = U128.of(0, cy).or(t1.shr(64));
    cy = addu128(t, t2, t2p);
    t2 = t2p[0];
    /* (cy:1,t2:128,low(t1):64) is the sum of the terms of degree 0 to 2 */

    t3 = t3.add(U128.of(0, cy).or(t2.shr(64)));
    /* (t3,low(t2):64,low(t1):64) is the sum of the terms of degree 0 to 3 */

    long ex = Long.numberOfLeadingZeros(t3.hi);

    t2 = t2.shl(64).or(t1.and(U128.of(0xffffffffffffffffL, 0)));

    /* ex is 0 or 1 because a and c are normalized (2^63 <= a->hh, c < 2^64) */

    /* we only ignore the low part of t0 which contributes less than 1 ulp */

    if (ex != 0)
      {
        r.rh(t3.shl(1).or(t2.shr(127)));
        r.rl(t2.shl(1));
        /* the error is scaled by 2, thus less than 2 ulps */
        r.ex --;
      }
    else
      {
        r.rh(t3);
        r.rl(t2);
        /* error less than 1 ulp in that case */
      }
  }

  /***************** polynomial approximations of exp(z) ***********************/

  /* Given z such that |z| < 2^-12.905,
     this routine puts in qh+ql an approximation of exp(z) such that

     | (qh+ql) / exp(z) - 1 | < 2^-64.902632

     and |ql| <= 2^-51.999. See Lemma 6 from reference [5].
  */
  private static void q_1(double[] qh, double[] ql, double z) {
    double q, h0;
    double[] h1 = new double[1], l1 = new double[1];

    q = Math.fma(Q_1[4], z, Q_1[3]);

    q = Math.fma(q, z, Q_1[2]);

    h0 = Math.fma(q, z, Q_1[1]);

    aMul(h1, l1, z, h0);

    fastSum(qh, ql, Q_1[0], h1[0], l1[0]);
  }

  /* Given |y| < 0.00016923 < 2^-12.52, put in r an approximation of exp(y),
     with relative error bounded by 2^-122.29.
   */
  private static void q_2(Dint64 r, Dint64 y) {
    mulDint11(r, y, Q_2[0]);
    /* |y| < 2^-12.52 and |Q_2[0]| < 2^-12.29 thus |r| < 2^-24.81;
       mul_dint_11() is exact, and the low part of Q_2[0] is 0,
       but we ignore y.lo * Q_2[0], which is bounded by
       ulp64(2^-12.52)*2^-12.29 < 2^-88.29 */
    addDint11(r, Q_2[1], r);
    /* |Q_2[1]| < 2^-9.49 and |r_in| < 2^-24.81 thus |r| < 2^-9.48;
       the rounding error on add_dint_11() is bounded by 2 ulps,
       thus < 2^-72. We also ignore the low part of r_in, which is
       bounded by ulp64(2^-24.81) = 2^-88.
       The total error on r is thus < 2^-88.29 + 2^-72 + 2^-88 < 2^-71.99.
       This error is multiplied by y^6 below, thus contributes to at most
       err1 = 2^-71.99*y^6 < 2^-147.16 */

    mulDint11(r, y, r);
    /* |y| < 2^-12.52 and |r_in| < 2^-9.48 thus |r| < 2^-22.00;
       mul_dint_11() is exact;
       we ignore y * low(r_in) < 2^-12.52*ulp64(2^-9.48) = 2^-85.52;
       we also ignore low(y) * r_in < ulp64(2^-12.52)*2^-9.48 = 2^-85.48 */
    addDint11(r, Q_2[2], r);
    /* |Q_2[2]| < 2^-6.90 and |r_in| < 2^-22.00 thus |r| < 2^-6.89;
       the rounding error on add_dint_11() is bounded by 2 ulps,
       thus < 2^-69. We also ignore the low part of r_in, which is
       bounded by ulp64(2^-22.00-eps) = 2^-86.
       The total error on r is thus < 2^-85.52+2^-85.48+2^-69+2^-86 < 2^-68.99.
       This error is multiplied by y^5 below, thus contributes to at most
       err2 = 2^-68.99*y^5 < 2^-131.64 */

    mulDint11(r, y, r);
    /* |y| < 2^-12.52 and |r_in| < 2^-6.89 thus |r| < 2^-19.41;
       mul_dint_11() is exact;
       we ignore y * low(r_in) < 2^-12.52*ulp64(2^-6.89) = 2^-82.52;
       we also ignore low(y) * r_in < ulp64(2^-12.52)*2^-6.89 = 2^-82.89 */
    addDint(r, Q_2[3], r);
    /* |Q_2[3]| < 2^-4.58 and |r_in| < 2^-19.41 thus |r| < 2^-4.57;
       the rounding error on add_dint() is bounded by 2 ulps,
       thus < 2^-131.
       The total error on r is thus < 2^-82.52+2^-82.89+2^-131 < 2^-81.69.
       This error is multiplied by y^4 below, thus contributes to at most
       err3 = 2^-81.69*y^4 < 2^-131.80 */

    mulDint(r, y, r);
    /* |y| < 2^-12.52 and |r_in| < 2^-4.57 thus |r| < 2^-17.09;
       the rounding error of mul_dint() is bounded by 6 ulps,
       thus < 2^-142.41 */
    addDint(r, Q_2[4], r);
    /* |Q_2[4]| < 2^-2.58 and |r_in| < 2^-17.09 thus |r| < 2^-2.57;
       the rounding error on add_dint() is bounded by 2 ulps,
       thus < 2^-129.
       The total error on r is thus < 2^-142.41+2^-129 < 2^-128.99.
       This error is multiplied by y^3 below, thus contributes to at most
       err4 = 2^-128.99*y^3 < 2^-166.57 */

    mulDint(r, y, r);
    /* |y| < 2^-12.52 and |r_in| < 2^-2.57 thus |r| < 2^-15.09;
       the rounding error of mul_dint() is bounded by 6 ulps,
       thus < 2^-140.41 */
    addDint(r, Q_2[5], r);
    /* |Q_2[5]| < 2^-0.99 and |r_in| < 2^-15.09 thus |r| < 2^-0.98;
       the rounding error on add_dint() is bounded by 2 ulps,
       thus < 2^-127.
       The total error on r is thus < 2^-140.41+2^-127 < 2^-126.99.
       This error is multiplied by y^2 below, thus contributes to at most
       err5 = 2^-126.99*y^2 < 2^-152.04 */

    mulDint(r, y, r);
    /* |y| < 2^-12.52 and |r_in| < 2^-0.98 thus |r| < 2^-13.50;
       the rounding error of mul_dint() is bounded by 6 ulps,
       thus < 2^-138.41 */
    addDint(r, Q_2[6], r);
    /* |Q_2[6]| < 1.01 and |r_in| < 2^-13.50 thus |r| < 1.02;
       the rounding error on add_dint() is bounded by 2 ulps,
       thus < 2^-126.
       The total error on r is thus < 2^-138.41+2^-126 < 2^-125.99.
       This error is multiplied by y below, thus contributes to at most
       err6 = 2^-125.99*y < 2^-138.51 */

    mulDint(r, y, r);
    /* |y| < 2^-12.52 and |r_in| < 1.02 thus |r| < 2^-12.49;
       the rounding error of mul_dint() is bounded by 6 ulps,
       thus < 2^-137.41 */
    addDint(r, Q_2[7], r);
    /* |Q_2[7]| < 1 and |r_in| < 2^-12.49 thus |r| < 1.01;
       the rounding error on add_dint() is bounded by 2 ulps,
       thus < 2^-126.
       The total error on r is thus err7 < 2^-137.41+2^-126 < 2^-125.99. */

    /* Total absolute errors:
       err1 < 2^-147.16
       err2 < 2^-131.64
       err3 < 2^-131.80
       err4 < 2^-166.57
       err5 < 2^-152.04
       err6 < 2^-138.51
       err7 < 2^-125.99
       Total err1+...+err7 < 2^-125.93. Since |y| < 0.00016923, this translates
       into a relative error < 2^-125.93/exp(-0.00016923) < 2^-125.92.
       We also have the approximation error from the Sollya polynomial,
       which is bounded by 2^-122.415 (relative).
       The total relative error is thus bounded with e1=2^-125.92 and
       e2=2^-122.415 by (1+e1)*(1+e2)-1 < 2^-122.29.
    */
  }

  /* Given |y| < 0.00016923 < 2^-12.52, put in r an approximation of exp(y),
     with 0.999830 < r < 1.000170, absolute/relative error bounded by 2^-241.11.
     The error analysis is from the analyze_q3() function in the accompanying
     file qint.sage. */
  private static void q_3(Qint64 r, Qint64 y) {
    /* the absolute error from the Sollya polynomial is at most 2^-242.181 */

    /* the coefficients of degree 13 and 14 (Q_3[0-1]) have 64 bits only */

    mulQint11(r, y, Q_3[0]);
    /* here |y| < 0.00016923 and |Q_3[0]| < 2^-36.34 thus |r| < 2^-48.86;
       mul_qint_11() is exact (providing a 128-bit product), but we ignore
       the low part of y, which contributes to at most ulp64(y)*Q_3[0]
       < 2^-112.34. */
    addQint22(r, Q_3[1], r);
    /* here |Q_3[1]| < 2^-32.53 and |r_in| < 2^-48.86, thus |r| < 2^-32.52;
       the rounding error of add_qint_22() is bounded by 2 ulps_128, thus 2^-159;
       the low part of Q_3[1] is 0, but we ignore the low part of r_in, which
       contributes to at most ulp128(r_in) = 2^-176.
       The total error on for the two above instructions is thus bounded by
       2^-112.34 + 2^-159 + 2^-176 < 2^-112.34.
       This error is multiplied by y^13 below, thus contributes to at most
       err1 < 2^-275.21 (confirmed by analyze_q3()).
    */

    /* the coefficients of degree 8 to 12 (Q_3[2-6]) have 128 bits only */

    for (int k = 2; k < 7; k++) {
      mulQint22(r, y, r);
      /* mul_qint_22() is exact (with a 256-bit result), but we might ignore
         as input y*low(r) and low(y)*r, which accounts for y*ulp128(r_in)
         + ulp128(y)*r */
      addQint22(r, Q_3[k], r);
      /* the rounding error in add_qint_22() is less than 2 ulps_128,
         we have no ignored part of Q_3[k],
         but we ignore the low 128 bits from r_in,
         and the error of these two instructions is multiplied by y^(15-k) */
    }

    /* the coefficients of degree 0 to 7 (Q_3[7-14]) have 256 bits,
       thus we use the full add_qint() routine to add them, but for
       the larger degrees, we truncate to 3 limbs */

    for (int k = 7; k < 12; k++) {
      mulQint31(r, y, r);
      /* the rounding error of mul_qint_33() is at most 6 ulps(256),
         and we ignore as input y*low(r) and low(y)*r,
         which accounts for y*ulp192(r_in) + ulp192(y)*r */
      addQint(r, Q_3[k], r);
      /* the rounding error of add_qint() is bounded by 2 ulps(256) */
    }

    for (int k = 12; k < 15; k++) {
      mulQint(r, y, r);
      /* the rounding error of mul_qint() is at most 14 ulps, and there is
         no ignored part */
      addQint(r, Q_3[k], r);
      /* the rounding error of add_qint() is bounded by 2 ulps(256) */
    }

    /* The function analyze_q3() from the accompanying qint.sage file gives
       a total absolute error bounded by 2^-241.113. Since r > exp(-0.00016923),
       this corresponds to a relative error < 2^-241.113/exp(-0.00016923)
       < 2^-241.11. */
  }

  /**************** polynomial approximations of log(1+x) **********************/

  /* Given |z| <= 33*2^-13, with z an integer multiple of 2^-61,
     this routine puts in ph+pl an approximation of log(1+z)-z such that

     | ph + pl - (log(1 + z) - z) | < 2^-75.492

     with |ph| < 2^-16.9, |pl| < 2^-25.446.
     Moreover if z<>0, and assuming further |z| < 32*2^-13, the relative error
     satisfies:

     | (z + ph + pl) / log(1+z) - 1 | < 2^-67.441

     See Lemma 2 from reference [5].
  */
  private static void p_1(double[] ph, double[] pl, double z) {
    double[] wh = new double[1], wl = new double[1];
    aMul(wh, wl, z, z);
    double t = Math.fma(P_1[5], z, P_1[4]);
    double u = Math.fma(P_1[3], z, P_1[2]);
    double v = Math.fma(P_1[1], z, P_1[0]);
    u = Math.fma(t, wh[0], u);
    v = Math.fma(u, wh[0], v);
    u = v * wh[0];
    ph[0] = -0.5 * wh[0];
    pl[0] = Math.fma(u, z, -0.5 * wl[0]);
  }

  // Approximation for the second iteration
  // Return in r an approximation of log(1+z) for |z| <= 2^-13
  // The low part of z is assumed to be 0.
  // with relative error bounded by 2^-124.82 and absolute error by 2^-137.95
  // The coefficients of degree 6 to 9 (P_2[0] to P_2[3]) have precision 64 bits only.
  // For the error analysis, see the analyze_p2() function in the
  // accompanying dint.sage file.
  private static void p_2(Dint64 r, Dint64 z) {
    /* the error analysis below first consider the absolute error, then we
       switch to the relative error after the final multiplication by z */
    mulDint11(r, z, P_2[0]);
    /* here |z| <= 2^-13 and |P_2[0]| < 2^-3.16 thus |r| < 2^-16.16;
       mul_dint_11() is exact (the low part of z and P_2[0] is 0),
       but we ignore the low part of r below, which contributes to
       at most ulp64(2^-16.16) < 2^-80. */
    addDint11(r, P_2[1], r);
    /* here |P_2[1]| < 2^-2.99 and |r_in| < 2^-16.16, thus |r| < 2^-2.98;
       the rounding error of add_dint_11() is bounded by 2 ulps_64, thus 2^-65;
       the low part of P_2[1] is 0, but we ignore the low part of r_in (see
       above).
       The total error for the two above instructions is thus bounded by
       2^-80+2^-65 < 2^-64.99. This error is multiplied by z^7
       below (not counting the final multiplication by z),
       thus contributes to at most err1 < 2^-155.99. */

    mulDint11(r, z, r);
    /* here |z| <= 2^-13 and |r_in| < 2^-2.98 thus |r| < 2^-15.98;
       mul_dint_11() is exact (the low part of z and r_in is 0) */
    addDint11(r, P_2[2], r);
    /* here |P_2[2]| < 2^-2.80 and |r_in| < 2^-15.98, thus |r| < 2^-2.79;
       the rounding error of add_dint_11() is bounded by 2 ulps_64, thus 2^-65;
       the low part of P_2[2] is 0, but we ignore the low part of r_in, which
       contributes to at most ulp_64(2^-15.98) < 2^-79.
       The total error for the two above instructions is thus bounded by
       2^-65+2^-79 < 2^-64.99. This error is multiplied by z^6
       below (not counting the final multiplication by z),
       thus contributes to at most err2 < 2^-142.99. */

    mulDint11(r, z, r);
    /* here |z| <= 2^-13 and |r_in| < 2^-2.79 thus |r| < 2^-15.79;
       mul_dint_11() is exact (the low part of z and r_in is 0) */
    addDint11(r, P_2[3], r);
    /* here |P_2[3]| < 2^-2.58 and |r_in| < 2^-15.79, thus |r| < 2^-2.57;
       the rounding error of add_dint_11() is bounded by 2 ulps_64, thus 2^-65;
       the low part of P_2[3] is 0, but we ignore the low part of r_in, which
       contributes to at most ulp_64(2^-15.79) < 2^-79.
       The total error for the two above instructions is thus bounded by
       2^-65+2^-79 < 2^-64.99. This error is multiplied by z^5
       below (not counting the final multiplication by z),
       thus contributes to at most err3 < 2^-129.99. */

    mulDint11(r, z, r);
    /* here |z| <= 2^-13 and |r_in| < 2^-2.57 thus |r| < 2^-15.57;
       mul_dint_11() is exact (the low part of z and r_in is 0) */
    addDint(r, P_2[4], r);
    /* here |P_2[4]| < 2^-2.32 and |r_in| < 2^-15.57, thus |r| < 2^-2.31;
       the rounding error of add_dint() is bounded by 2 ulps_128, thus 2^-129.
       The total error for the two above instructions is thus bounded by 2^-129.
       This error is multiplied by z^4 below (not counting the final
       multiplication by z), thus contributes to at most err4 < 2^-181. */

    mulDint21(r, r, z);
    /* here |z| <= 2^-13 and |r_in| < 2^-2.31 thus |r| < 2^-15.31;
       the rounding error of mul_dint_21() is bounded by 2 ulps, thus 2^-142. */
    addDint(r, P_2[5], r);
    /* here |P_2[5]| < 2^-1.999 and |r_in| < 2^-15.31, thus |r| < 2^-1.99;
       the rounding error of add_dint() is bounded by 2 ulps_128, thus 2^-128.
       The total error for the two above instructions is thus bounded by
       2^-142+2^-128 < 2^-127.99. This error is multiplied by z^3
       below (not counting the final multiplication by z),
       thus contributes to at most err5 < 2^-166.99. */

    mulDint21(r, r, z);
    /* here |z| <= 2^-13 and |r_in| < 2^-1.99 thus |r| < 2^-14.99;
       the rounding error of mul_dint_21() is bounded by 2 ulps, thus 2^-141. */
    addDint(r, P_2[6], r);
    /* here |P_2[6]| < 2^-1.58 and |r_in| < 2^-14.99, thus |r| < 2^-1.57;
       the rounding error of add_dint() is bounded by 2 ulps_128, thus 2^-128.
       The total error for the two above instructions is thus bounded by
       2^-141+2^-128 < 2^-127.99. This error is multiplied by z^2
       below (not counting the final multiplication by z),
       thus contributes to at most err6 < 2^-153.99. */

    mulDint21(r, r, z);
    /* here |z| <= 2^-13 and |r_in| < 2^-1.57 thus |r| < 2^-14.57;
       the rounding error of mul_dint_21() is bounded by 2 ulps, thus 2^-141. */
    addDint(r, P_2[7], r);
    /* here |P_2[7]| < 2^-1 and |r_in| < 2^-14.57, thus |r| < 2^-0.99;
       the rounding error of add_dint() is bounded by 2 ulps_128, thus 2^-127.
       The total error for the two above instructions is thus bounded by
       2^-141+2^-127 < 2^-126.99. This error is multiplied by z
       below (not counting the final multiplication by z),
       thus contributes to at most err7 < 2^-133.99. */

    mulDint21(r, r, z);
    /* here |z| <= 2^-13 and |r_in| < 2^-0.99 thus |r| < 2^-13.99;
       the rounding error of mul_dint_21() is bounded by 6 ulps, thus 2^-140. */
    addDint(r, P_2[8], r);
    /* here P_2[8] = 1 and |r_in| < 2^-13.99, thus |r| < 1.0001;
       the rounding error of add_dint() is bounded by 2 ulps_128, thus 2^-126.
       The total error for the two above instructions is thus bounded by
       err8 = 2^-140+2^-126 < 2^-125.99. */

    /* Maximal absolute error on r up to here:
       err1 < 2^-155.99
       err2 < 2^-142.99
       err3 < 2^-129.99
       err4 < 2^-181
       err5 < 2^-166.99
       err6 < 2^-153.99
       err7 < 2^-133.99
       err8 < 2^-125.99
       Total absolute error < err1+...+err8 < 2^-125.89.
       We now transform this into relative error, knowing that
       |r| >= 1 - 2^-13.99, this gives a relative error < 2^-125.88
       (2^-125.90 with analyze_p2()).
    */

    mulDint21(r, r, z);
    /* We bound both the absolute and relative error.

       Absolute error:
       here |z| <= 2^-13 and |r_in| < 1.01 thus |r| < 2^-12.98;
       the rounding error of mul_dint_21() is bounded by 2 ulps, thus 2^-139.
       We add the previous total absolute error on r_in multiplied by z,
       which gives 2^-139+2^-125.88*2^-13 < 2^-137.93
       (2^-137.95 with analyze_p2()).

       The relative error on the Sollya polynomial is bounded by
       eps0 = 2^-128.316.
       The relative error on r_in is bounded by eps1=2^-125.88
       and the error from mul_dint_21 is bounded by 2 ulps
       (which yields a relative error less than eps2=2*2^-127).
       This yields a relative error on r less than:
       |(1 + eps0) * (1 + eps1) * (1 + eps2) - 1| < 2^-124.80
       (2^-124.82 with analyze_p2()). */
  }

  /* Approximation of log(1+z) for the last iteration, with |z| <= 2^-13
     and z having only its upper limb being non-zero.
     For the error analysis, see function analyze_p3() in accompanying file
     qint.sage.
     Maximal relative error: 2^-252.66, maximal absolute error: 2^-265.67,
     and |r| < 0.0001221. */
  private static void p_3(Qint64 r, Qint64 z) {
    mulQint11(r, P_3[0], z); /* coefficient of degree 18 */
    /* here |P_3[0]| < 2^-4.16 and |z| <= 2^-13 thus |r| < 2^-17.16;
       mul_qint_11() is exact giving a 128-bit product
       (note that P_3[0] and z have only their upper limb non-zero). */
    addQint22(r, P_3[1], r); /* coefficient of degree 17 */
    /* here |P_3[1]| < 2^-4.08 and |r_in| < 2^-17.16, thus |r| < 2^-4.07;
       the rounding error of add_qint_22() is bounded by 2 ulps_128, thus 2^-131;
       and the low part of P_3[1] is 0.
       The total error for the two above instructions is thus bounded by
       2^-131. This error is multiplied by z^16
       below (not counting the final multiplication by z),
       thus contributes to at most err1 < 2^-339. */

    /* the coefficients of degree 15-16 (P_3[2] to P_3[3]) have two non-zero
       limbs */
    for (int k = 2; k < 4; k++) {
      mulQint11(r, r, z);
      /* mul_qint_11() is exact (giving a 128-bit product), but we ignore
         as input (r_in->hl)*z, which accounts for ulp64(r_in)*z */
      addQint22(r, P_3[k], r);
      /* the rounding error in add_qint_22() is less than 2 ulps_128,
         and the error of these two instructions is multiplied by z^(17-k)
         (not counting the final multiplication by z). */
    }

    /* the coefficients of degree 11-14 (P_3[4] to P_3[7]) have two non-zero
       limbs */
    for (int k = 4; k < 8; k++) {
      mulQint21(r, r, z);
      /* mul_qint_21() is exact (giving a 192-bit product), but we ignore the
         "lh" limb of r in add_qint_22() below, which accounts for ulp128(r) */
      addQint22(r, P_3[k], r);
      /* the rounding error in add_qint_22() is less than 2 ulps_128,
         and the error of these two instructions is multiplied by z^(17-k)
         (not counting the final multiplication by z). */
    }

    /* the coefficients of degree 5-10 (P_3[8] to P_3[13]) have full 256-bit
       accuracy */
    for (int k = 8; k < 14; k++) {
      mulQint31(r, r, z);
      /* mul_qint_31() is exact, but we ignore in input the 4th
         limb of r, which accounts for ulp192(r_in)*z (except for k=8
         since r was computed by add_qint_22() above, and we already took
         into account the truncated part of r) */
      addQint(r, P_3[k], r);
      /* the rounding error in add_qint() is less than 2 ulps_256,
         and the error of these two instructions is multiplied by z^(17-k)
         (not counting the final multiplication by z). */
    }

    for (int k = 14; k < 18; k++) {
      mulQint41(r, r, z);
      /* mul_qint_41() has an error < 2 ulps */
      addQint(r, P_3[k], r);
      /* the rounding error in add_qint() is less than 2 ulps_256,
         and the error of these two instructions is multiplied by z^(17-k)
         (not counting the final multiplication by z). */
    }

    /* The total absolute error up to here is bounded by 2^-253.92 (see
       function analyze_p3 in accompanyng file qint.sage).
       We have r > 0.99993896, thus the relative error is bounded by
       2^-253.92/0.99993896 < 2^-253.91. */

    /* since the polynomial has zero constant coefficient, we multiply by z */
    mulQint41(r, r, z);
    /* We bound both the absolute and relative error.

       Absolute error:
       here |z| <= 2^-13 and |r_in| < 1.01 thus |r| < 2^-12.98;
       the rounding error of mul_qint_41() is bounded by 2 ulps, thus 2^-267.
       We add the previous total absolute error on r_in multiplied by z,
       which gives 2^-267+2^-253.92*2^-13 < 2^-265.95.
       The absolute error on the Sollya polynomial is bounded by 2^-268.1653.
       This yields a total absolute < 2^-265.95+2^-268.1653 < 2^-265.66.
       (The analyze_p3() routine in the accompanying file yields 2^-265.67.)

       The relative error on the Sollya polynomial is bounded by
       eps0 = 2^-255.0786,
       the relative error on r_in is bounded by eps1=2^-253.91
       and the error from mul_dint_41 is bounded by 2 ulps
       (which yields a relative error less than eps2=2*2^-255).
       This yields a relative error on r less than:
       |(1 + eps0) * (1 + eps1) * (1 + eps2) - 1| < 2^-252.65.
       (The analyze_p3() routine in the accompanying file yields 2^-252.66.) */
  }

  // static const double cy[] and static const uint64_t cm[] inside log_1()
  private static final double[] cy = {1.0, 0.5};
  private static final long[] cm = {44, 45};

  // #define LOG2_H and #define LOG2_L inside log_1()
  private static final double LOG2_H = 0x1.62e42fefa38p-1;
  private static final double LOG2_L = 0x1.ef35793c7673p-45;

  /* Given 2^-1074 <= x <= 0x1.fffffffffffffp+1023, this routine puts in h+l
     an approximation of log(x) such that |l| < 2^-23.89*|h| and

     | h + l - log(x) | <= elog * |log x|

     with elog = 2^-73.527  if x < 1/sqrt(2) or sqrt(2) < x,
     and  elog = 2^-67.0544 if 1/sqrt(2) < x < sqrt(2)
     (note that x cannot equal 1/sqrt(2) nor sqrt(2)).

     See Lemma 4 from reference [5].
  */
  private static int log_1(double[] h, double[] l, double x) {
    long _xu = Double.doubleToRawLongBits(x);
    long _m = _xu & (~0L >>> 12);
    long _e = (_xu >>> 52) & 0x7ff;

    long _t;

    if (_e != 0) {
      _t = _m | (0x3ffL << 52);
      _m += 1L << 52;
      _e -= 0x3ff;
    } else { /* x is a subnormal double  */
      int k = Long.numberOfLeadingZeros(_m) - 11;

      _e = -0x3feL - k;
      _m <<= k;
      _t = _m | (0x3ffL << 52);
    }

    /* now |x| = 2^_e*_t = 2^(_e-52)*m with 1 <= _t < 2,
       and 2^52 <= _m < 2^53 */

    //   log(x) = log(t) + E · log(2)
    double t = Double.longBitsToDouble(_t);

    // Find the lookup index
    long i;

    // If m > sqrt(2) we divide it by 2 so ensure 1/sqrt(2) < t < sqrt(2)
    long c = Long.compareUnsigned(_m, 0x16a09e667f3bcdL) >= 0 ? 1 : 0;

    _e += c;
    double E = (double) _e;
    i = _m >>> (int) cm[(int) c]; /* i/2^8 <= t < (i+1)/2^8 */
    /* when c=1, we have 0x16a09e667f3bcd <= m < 2^53, thus 90 <= i <= 127;
       when c=0, we have 2^52 <= m < 0x16a09e667f3bcd, thus 128 <= i <= 181 */
    t *= cy[(int) c];
    /* now 0x1.6a09e667f3bcdp-1 <= t < 0x1.6a09e667f3bcdp+0,
       and log(x) = E * log(2) + log(t) */

    double r = _INVERSE[(int) i - 181];
    double l1 = _LOG_INV[(int) i - 181][0];
    double l2 = _LOG_INV[(int) i - 181][1];

    double z = Math.fma(r, t, -1.0);

    double th, tl;
    th = Math.fma(E, LOG2_H, l1);
    tl = Math.fma(E, LOG2_L, l2);

    fastSum(h, l, th, z, tl);
    double[] ph = new double[1], pl = new double[1];
    p_1(ph, pl, z);
    fastSum(h, l, h[0], ph[0], l[0] + pl[0]);

    if (_e == 0 && Math.abs(l[0]) > Math.abs(h[0]) * 0x1p-24)
    {
      fastTwoSum(h, l, h[0], l[0]);
      return 1;
    }

    return 0;
  }

  /* Put in r an approximation of log(x), with relative error bounded by
     2^-122.88. */
  private static void log_2(Dint64 r, Dint64 x) {
    long E = x.ex;
    int i, j;

    /* x = 2^(E-63) * hi */

    // find the 1st lookup index i = floor(x*2^7)
    if (x.hi > 0xb504f333f9de6484L) { /* hi/2^63 > sqrt(2) */
      E++;
      i = (int) (x.hi >>> (63 + 1 - 7)); // the +1 accounts for the division x/2
    }
    else
      i = (int) (x.hi >>> (63 - 7));

    /* now 90 <= i <= 181 */

    x.ex = x.ex - E;

    /* now sqrt(2)/2 < x < sqrt(2) */

    Dint64 z = new Dint64();
    mulDint11(z, x, _INVERSE_2_1[i - 90]); /* exact */
    /* The low limb of z is zero, since x has 53 significant bits, and
       _INVERSE_2_1[i-90] has 9 significant bits, thus the product fits
       in 64 bits. We have 0.9921875 <= z <= 1.0078125 here. */

    // find the 2nd lookup index j = floor(x*2^13)
    j = (int) (z.hi >>> (int) (63 - 13 - z.ex));

    mulDint11(z, z, _INVERSE_2_2[j - 8128]); /* exact */
    /* here z.lo might be non-zero */
    /* we have 0.9998779296875 <= z <= 1.0001220703125 here */

    // subtract 1, since 1/2 < z < 2 this is exact */
    addDint(z, M_ONE, z);

    /* _INVERSE_2_2[j-8128] has at most 14 significant bits, thus in principle
       before the subtraction of 1, z might have up to 53+9+14 = 76 significant
       bits. However for sqrt(2)/2 < x < 1, since x has 53 significant
       bits, x is an integer multiple of 2^-53; then _INVERSE_2_1[i-90] is an
       integer multiple of 2^-8, then x*_INVERSE_2_1[i-90] is an integer multiple
       of 2^-61. If 1 <= x < sqrt(2), x is an integer multiple of 2^-52 and
       _INVERSE_2_1[i-90] an integer multiple of 2^-9, thus again
       x*_INVERSE_2_1[i-90] an integer multiple of 2^-61.
       Now _INVERSE_2_2[j-8128] is an integer multiple of 2^-14, thus
       z0 := x*_INVERSE_2_1[i-90]*_INVERSE_2_2[j-8128] is an integer multiple of
       2^-61*2^-14=2^-75.
       Write z0 = 1 + t*2^-75 with t integer.
       Since 0.9998779296875 <= z0 <= 1.0001220703125, we deduce |t| <= 2^62,
       thus z0-1 is exactly representable on 64 bits. */

    /* now |z| <= 0.0001220703125 = 2^-13 */

    // E·log(2)
    mulDintInt64(r, LOG2, E);
    /* The rounding error in mul_dint_int64() is at most 1 ulp.
       Since |E| <= 1074, this is at most ulp(1074*log(2))=2^-118,
       and |r| <= 1074*log(2) < 2^9.55.
       We also have the approximation error on log(2), which is < 2^-129.97,
       and multiplied by |E| <= 1074, thus < 2^-119.90.
       This yields an absolute error < 2^-118+2^-119.90 < 2^-117.65.
       The relative rounding error in mul_dint_int64() is bounded by 1 ulp thus
       2^-127, and that on log(2) is bounded by 1/2 ulp thus 2^-128, which
       yields (1+2^-127)*(1+2^-128)-1 < 2^-126.41.
    */

    Dint64 p = new Dint64();

    p_2(p, z); /* relative error < 2^-124.82, absolute error < 2^-137.95,
                  and |p| < 0.0001221 */

    addDint(p, _LOG_INV_2_2[j - 8128], p);
    /* here we have |_LOG_INV_2_2[j-8128]| < 2^-6.99 and |p_in| < 0.0001221,
       thus |p| < 2^-6.96, and the rounding error of add_dint() is bounded
       by 2 ulps, thus < 2^-133 (absolute) and < 2^-126 (relative). */

    addDint(p, _LOG_INV_2_1[i - 90], p);
    /* here we have |_LOG_INV_2_1[i-90]| < 2^-1.51 and |p_in| < 2^-6.96,
       thus |p| < 2^-1.47, and the rounding error of add_dint() is bounded
       by 2 ulps, thus < 2^-128 (absolute) and < 2^-126 (relative). */

    addDint(r, p, r);
    /* here |p| < 2^-1.47 and |r_in| < 2^9.55, thus |r| < 2^9.56.
       The rounding error of add_dint() is bounded is bounded by 2 ulps,
       thus < 2^-117 (absolute) or 2^-126 (relative). */

    /* We have the following rounding errors:
      (a) rounding error on E*log(2) which is bounded by 2^-117.65 (absolute)
          and 2^-126.41 (relative)
      (b) error on p_2() bounded by 2^-137.95 (absolute) and 2^-124.82 (relative)
      (c1) approximation error on _LOG_INV_2_1[i-90],
          bounded by 2^-130 (absolute) and 2^-128 (relative)
      (c2) approximation error on _LOG_INV_2_2[j-8128],
          bounded by 2^-136 (absolute) and 2^-128 (relative)
      (d1) rounding error on _LOG_INV_2_1[i-90]+p, bounded by 2^-128 (absolute)
           and 2^-126 (relative)
      (d2) rounding error on _LOG_INV_2_2[j-8128]+p, bounded by 2^-133 (absolute)
           and 2^-126 (relative)
      (e) rounding error on p + r, bounded by 2^-117 (absolute) and 2^-126
          (relative)
       We distinguish two cases here: E<>0 and E=0.

       If E<>0, then |r_in| > 2^-0.53 and |p| < 2^-1.47, thus |r| > 2^-1.59.
       The absolute errors (b), (c1), (c2), (d1), (d2) are bounded by
       2^-137.95 + 2^-130 + 2^-136 + 2^-128 + 2^-133 < 2^-127.63, which thus
       converts to 2^-127.63/2^-1.59 < 2^-126.04 as relative error.
       Together with the relative error (a) e1=2^-126.41 on E*log(2) and
       (e) e2=2^-126 on p+r, this yields with e3=2^-126.04:
       |(1+e1)*(1+e2)*(1+e3)-1| < 2^-124.55.

       If E=0, then the rounding error (a) on E*log(2) vanishes, and likewise
       the rounding error (e) on p + r, since r=0. We further distinguish two
       cases: either i <> {127, 127}, or 127 <= i <= 128.

       If E=0 and i <> {127, 128}: the errors (a) and (e) vanish since r=0.
       Using interval arithmetic to analyze each of the possible (i,j) pairs,
       using the fact that the errors (c1) and (c2) are bounded by 1/2 ulp
       of the corresponding values, and that the errors (d1) and (d2) are
       bounded by 2 ulps of the corresponding result, we find (see routine
       max_rel_err_p2_case1 in the accompanying file dint.sage) that the
       maximal relative error is bounded by 2^-123.89, obtained for i=126
       and j=8256.

       If E=0 and (i=127 or i=128), then we have _INVERSE_2_1[i-90]=1 thus
       _LOG_INV_2_1[i-90]=0, and the errors (c1) and (d1) also vanish.
       It only remains errors (b), (c2) and (d2). We further distinguish
       two sub-cases: either j <> {8191, 8192}, or 8191 <= j <= 8192.

       If E=0, i in {127,128} and j <> {8191, 8192}, then using the same method
       as in case E=0 and i <> {127, 128}, we find that the relative error
       is bounded by 2^-122.88 (attained for j=8193). See routine
       max_rel_err_p2_case2() in the accompanying file dint.sage.

       It remains the case E=0, i in {127,128} and j in {8191, 8192}. In this
       case the errors (c2) and (d2) are also zero, it only remains error (b),
       which is bounded by 2^-124.82 (relative).

       In summary, the relative error is bounded:
       * if E<>0, by 2^-124.55
       * if E=0 and i <> {127, 128}, by 2^-123.89
       * if E=0, i in {127, 128}, j <> {8191, 8192}, by 2^-122.88
       * if E=0, i in {127, 128}, j in {8191, 8192}, by 2^-124.82

       In all cases, the relative error is bounded by 2^-122.88,
       where the largest bound comes from the case E=0, i in {127, 128},
       and j <> {8191, 8192}.
    */
  }

  /* put in r an approximation of log(x), with relative error < 2^-250.74 */
  private static void log_3(Qint64 r, Qint64 x) {
    long E = x.ex;

    // Find the lookup index: upper 8 bits of x
    int i, j;

    /* x = 2^(E-63) * hh */

    if (x.hh > 0xb504f333f9de6484L) {
      E++;
      i = (int) (x.hh >>> (63 + 1 - 7)); // the +1 accounts for the division x/2
    }
    else
      i = (int) (x.hh >>> (63 - 7));

    /* now 90 <= i <= 181 */

    x.ex = x.ex - E;

    /* now sqrt(2)/2 < x < sqrt(2) */

    Qint64 z = new Qint64();
    mulQint(z, x, _INVERSE_3_1[i - 90]);
    /* The above operation is exact since x has 53 significant bits,
       and _INVERSE_3_1[i - 90] has 9 significant bits.
       We have 0.9921875 <= z <= 1.0078125. */

      // find the 2nd lookup index j = floor(x*2^13)
    j = (int) (z.hh >>> (int) (63 - 13 - z.ex));

    mulQint(z, z, _INVERSE_3_2[j - 8128]); /* exact */
    /* here z.hl might be non-zero */
    /* we have 0.9998779296875 <= z <= 1.0001220703125 here */

    // subtract 1, since 1/2 < z < 2 this is exact */
    addQint(z, M_ONE_Q, z);
    /* z fits into 64 bits (same analysis as in log_2) */

    /* now |z| <= 0.0001220703125 = 2^-13 */

    // E·log(2)
    mulQint2(r, E, LOG2_Q);
    /* The rounding error in mul_qint_2() is at most 2 ulps.
       Since |E| <= 1074, this is at most 2*ulp_256(1074*log(2))=2^-245,
       and |r| <= 1074*log(2) < 2^9.55.
       We also have the approximation error on log(2), which is < 2^-256.14,
       and multiplied by |E| <= 1074, thus < 2^-246.07.
       This yields an absolute error < 2^-245+2^-246.07 < 2^-244.43.
       The relative rounding error in mul_qint_2() is bounded by 2 ulps thus
       2^-254, and that on log(2) is bounded by 1/2 ulp thus 2^-256, which
       yields (1+2^-254)*(1+2^-256)-1 < 2^-253.67. */

    Qint64 p = new Qint64();
    p_3(p, z); /* relative error < 2^-252.66, and |p| < 0.0001221 */

    /* We have to accumulate r which approximates E*log(2),
       _LOG_INV_3_1[i-90] which approximates -log(_INVERSE_3_1[i-90]),
       _LOG_INV_3_2[j-8128] which approximates -log(_INVERSE_3_2[i-8128]),
       and p which approximates log (z). We start by the smallest
       values to minimize the rounding error. */

    addQint(p, _LOG_INV_3_2[j - 8128], p);
    /* here we have |_LOG_INV_3_2[j-8128]| < 0.0078432 and |p_in| < 0.0001221,
       thus |p| < 0.0079653, and the rounding error of add_qint() is bounded
       by 2 ulps, thus < 2^-261 (absolute) and 2^-254 (relative). */

    addQint(p, _LOG_INV_3_1[i - 90], p);
    /* here we have |_LOG_INV_3_1[i-90]| < 0.34945 and |p_in| < 0.0079653,
       thus |p| < 0.3574153, and the rounding error of add_qint() is bounded
       by 2 ulps, thus < 2^-256 (absolute) and 2^-254 (relative). */

    addQint(r, p, r);
    /* here |p| < 0.3574153 and |r_in| < 2^9.55, thus |r| < 2^9.56.
       The rounding error of add_qint() is bounded by 2 ulps,
       thus < 2^-245 (absolute) or 2^-254 (relative). */

    /* We have the following rounding errors:
       (a) rounding error on E*log(2) which is bounded by 2^-244.43 (absolute)
           and 2^-253.67 (relative)
       (b) error on p_3() bounded by 2^-265.67 (absolute) and 2^-252.66 (rel)
       (c1) approximation error on _LOG_INV_3_1[i-90], bounded by 2^-258
           (absolute) and 2^-256 (relative) [1/2 ulp]
       (c2) approximation error on _LOG_INV_3_2[j-8128], bounded by 2^-263
           (absolute) and 2^-256 (relative) [1/2 ulp]
       (d1) rounding error on _LOG_INV_3_1[i-90] + p, bounded by 2^-256
            (absolute) and 2^-254 (relative)
       (d2) rounding error on _LOG_INV_3_2[j-8128] + p, bounded by 2^-261
            (absolute) and 2^-254 (relative)
       (e) rounding error on p + r, bounded by 2^-245 (absolute) and
           2^-254 (relative)
       We distinguish two cases here: E<>0 and E=0.

       If E<>0, then |r_in| > 0.69314 and |p| < 0.3574153, thus |r| > 0.33572.
       The absolute errors (b), (c1), (c2), (d1), (d2) are bounded by
       2^-265.67 + 2^-258 + 2^-263 + 2^-256 + 2^-261 < 2^-255.63, which thus
       converts to 2^-255.63/0.33572 < 2^-254.05 as relative error.
       Together with the relative error e1=2^-253.67 on E*log(2) and e2=2^-254
       on p+r, this yields with e3=2^-254.05:
       |(1+e1)*(1+e2)*(1+e3)-1| < 2^-252.31.

       If E=0, then the rounding error (a) on E*log(2) vanishes, and likewise
       the rounding error (e) on p + r, since r=0. We further distinguish two
       cases: either i <> {127, 127}, or 127 <= i <= 128.

       If E=0 and i <> {127, 128}: the errors (a) and (e) vanish since r=0.
       Using interval arithmetic to analyze each of the possible (i,j) pairs,
       using the fact that the errors (c1) and (c2) are bounded by 1/2 ulp
       of the corresponding values, and that the errors (d1) and (d2) are
       bounded by 2 ulps of the corresponding result, we find (see routine
       max_rel_err_p3_case1 in the accompanying file qint.sage) that the
       maximal relative error is bounded by 2^-251.88, obtained for i=126
       and j=8256.

       If E=0 and (i=127 or i=128), then we have _INVERSE_3_1[i-90]=1 thus
       _LOG_INV_3_1[i-90]=0, and the errors (c1) and (d1) also vanish.
       It only remains errors (b), (c2) and (d2). We further distinguish
       two sub-cases: either j <> {8191, 8192}, or 8191 <= j <= 8192.

       If E=0, i in {127,128} and j <> {8191, 8192}, then using the same method
       as in case E=0 and i <> {127, 128}, we find that the relative error
       is bounded by 2^-250.74 (attained for j=8193). See routine
       max_rel_err_p3_case2() in the accompanying file dint.sage.

       It remains the case E=0, i in {127,128} and j in {8191, 8192}. In this
       case the errors (c2) and (d2) are also zero, it only remains error (b),
       which is bounded by 2^-252.66 (relative).

       In summary, the relative error is bounded:
       * if E<>0, by 2^-252.31
       * if E=0 and i <> {127, 128}, by 2^-251.88
       * if E=0, i in {127, 128}, j <> {8191, 8192}, by 2^-250.74
       * if E=0, i in {127, 128}, j in {8191, 8192}, by 2^-252.66

       In all cases, the relative error is bounded by 2^-250.74
       where the largest bound comes from the case E=0, i in {127, 128},
       and j <> {8191, 8192}.
    */
  }

  // #define RHO0, RHO1, RHO2, RHO3 inside exp_1()
  private static final double RHO0 = -0x1.74910ee4e8a27p+9;
  // #define RHO1 -0x1.577453f1799a6p+9
  /* We increase the initial value of RHO1 to avoid spurious underflow in
     the result value el. However, it is not possible to obtain a lower
     bound on |el| from the input value rh, thus this modified value of RHO1
     is obtained experimentally. */
  private static final double RHO1 = -0x1.483b8cca421afp+9;
  private static final double RHO2 = 0x1.62e42e709a95bp+9;
  private static final double RHO3 = 0x1.62e4316ea5df9p+9;

  // #define INVLOG2 inside exp_1()
  private static final double INVLOG2 = 0x1.71547652b82fep+12;

  // #define LOG2H and #define LOG2L inside exp_1()
  private static final double LOG2H = 0x1.62e42fefa39efp-13;
  private static final double LOG2L = 0x1.abc9e3b39803fp-68;

  /* Given RHO1 <= rh <= RHO2, |rl/rh| < 2^-23.8899 and |rl| < 2^-14.4187,
     this routine computes an approximation eh+el of exp(rh+rl) such that:

     | (eh+el) / exp(rh+rl) - 1 | < 2^-63.78597.

     Moreover |el/eh| <= 2^-49.2999.

     See Lemma 7 from reference [5].

     The result eh+el is multiplied by s (which is +1 or -1),
     where s=-1 can only happen when x < 0 and y is an integer.
  */
  private static void exp_1(double[] eh, double[] el, double rh, double rl, double s) {
    /* Section 7.12.17 from the C standard (N3220) says: "Relational operators
       may raise the "invalid" floating-point exception when argument
       values are NaNs". We thus first check rh != rh to detect NaNs,
       hoping this will not raise invalid. */
    if (rh != rh || rh > RHO2) {
      // again, first check rh == rh to detect NaNs
      if (rh == rh && rh > RHO3) {
        /* If rh > RHO3, we are sure there is overflow,
           For s=1 we return eh = el = DBL_MAX, which yields
           res_min = res_max = +Inf for rounding up or to nearest,
           and res_min = res_max = DBL_MAX for rounding down or toward zero,
           which will yield the correct rounding.
           For s=-1 we return eh = el = -DBL_MAX, which similarly gives
           res_min = res_max = -Inf or res_min = res_max = -DBL_MAX,
           which is the correct rounding. */

        eh[0] = 0x1.fffffffffffffp+1023 * s;
        el[0] = 0x1.fffffffffffffp+1023 * s;
      }
      else
        /* If RHO2 < rh <= RHO3, we are in the intermediate region
           where there might be overflow or not, thus we set eh = el = NaN,
           which will set res_min = res_max = NaN, the comparison
           res_min == res_max will fail: we defer to the 2nd phase. */
        eh[0] = el[0] = Double.NaN;
      return;
    }

    if (rh < RHO1) {
      if (rh < RHO0)
      {
        eh[0] = +0.0 * s;
        el[0] = 0x1p-1074 * (0.5 * s);
        /* For s=1, we have eh=el=+0 except for rounding up,
           thus res_min=+0 or -0, res_max=+0 in the main code,
           the rounding test succeeds, and we return res_max which is the
           expected result in the underflow case.
           For s=1 and rounding up, we have eh=+0, el=2^-1074,
           thus res_min = res_max = 2^-1074, which is the expected result too.
           For s=-1, we have eh=el=-0 except for rounding down,
           thus res_min=-0 or +0, res_max=-0 in the main code,
           the rounding test succeeds, and we return res_max which is the
           expected result in the underflow case.
           For s=-1 and rounding down, we have eh=-0, el=-2^-1074,
           thus res_min = res_max = -2^-1074, which is the expected result too.
        */
      }
      else /* RHO0 <= rh < RHO1 or s < 0: we defer to the 2nd phase */
        eh[0] = el[0] = Double.NaN;
      return;
    }

    /* Note: if the rounding mode is to nearest, we can save about 2 cycles
       (on an i7-8700) by replacing the computation of k by the following
       classical trick:
       const double magic = 0x1.8p+52;
       double k = __builtin_fma (rh, INVLOG2, magic) - magic;
    */
    double k = Math.rint(rh * INVLOG2);

    double zh, zl;
    zh = Math.fma(LOG2H, -k, rh);
    zl = Math.fma(LOG2L, -k, rl);

    long K = (long) k; /* Note: k is an integer, this is just a conversion. */
    long M = (K >> 12) + 0x3ff;
    long i2 = (K >> 6) & 0x3f;
    long i1 = K & 0x3f;

    double t1h = T1[(int) i2][0], t1l = T1[(int) i2][1],
           t2h = T2[(int) i1][0], t2l = T2[(int) i1][1];
    dMul(eh, el, t2h, t2l, t1h, t1l);

    double[] qh = new double[1], ql = new double[1];
    q_1(qh, ql, zh + zl);

    dMul(eh, el, eh[0], el[0], qh[0], ql[0]);
    double _d;

    /* we should have 1 < M < 2047 here, since we filtered out
       potential underflow/overflow cases at the beginning of this function */

    _d = Double.longBitsToDouble(M << 52);
    _d *= s;
    eh[0] *= _d;
    el[0] *= _d;
  }

  /* put in r an approximation of exp(x), for |x| < 744.45,
     with relative error < 2^-121.70 */
  private static void exp_2(Dint64 r, Dint64 x) {
    Dint64 K = new Dint64(), y = new Dint64();

    if (x.ex >= 10) // underflow or overflow
    {
      cpDint(r, x);
      r.ex = (x.sgn == 0x1) ? -1076 : 1025;
      r.sgn = 0;
      return;
    }

    /* the following multiplication does not need to be fully accurate,
       since it is only used to round x*2^12/log(2) to the nearest integer k */
    mulDint11(K, x, LOG2_INV);
    /* mul_dint_11() is exact, assuming the low part of x is zero, but it is not
       here, thus we ignore low(x)*LOG2_INV < ulp64(744.45)*LOG2_INV < 2^-41.47.
       Since LOG2_INV approximates 2^12/log(2) with absolute error < 2^-52.96
       and |x| < 744.45, the error from LOG2_INV is bounded by 2^-43.41.
       This gives a total error < 2^-41.13. This error is multiplied by LOG2
       below, and divided by 2^12, thus yields an error < 2^-53.65 on K in
       the add_dint() call. */

    long k = dintToi(K); /* k = trunc(K) [rounded towards zero, exact] */

    /* |k| <= 4399162 */

    /* the following multiplication needs to be fully accurate, since we need
       to compute x - k*log(2)/2^12 to full accuracy */
    mulDintInt64(K, LOG2, k);
    /* The rounding error of mul_dint_int64() is bounded by 6 ulps, thus since
       |K| <= 4399162*log(2) < 3049267, the error on K is bounded by 2^-103.41.
       This error is divided by 2^12 below, thus yields < 2^-115.41. */
    K.ex -= 12;
    K.sgn = K.sgn == 0 ? 1 : 0;

    addDint(y, x, K); /* exact because Sterbenz theorem applies */
    /* If all computations were exact above, we would have
       K = -(x/log(2)-eps/2^12)*log(2) with |eps| < 1 and eps of the same
       sign as x, thus x+K = eps*log(2)/2^12, and thus |y| < log(2)/2^12,
       with y of same sign as x.
       But we have a total rounded error < 2^-53.65 + 2^-115.41 < 2^-53.64
       thus |y| < log(2)/2^12 + 2^-53.64 < 0.00016923. */

    long M = k >> 12;
    long i2 = (k >> 6) & 0x3f;
    long i1 = k & 0x3f;

    q_2(r, y); /* relative error bounded by 2^-122.29, with |r| < 1.0002 */

    mulDint(r, T1_2[(int) i2], r);
    /* the rounding error of mul_dint() is bounded by 6 ulps, which translates
       into 6*2^-127 for the relative error;
       the approximation error for T1_2[i2] is bounded by 2^-128 relatively. */

    mulDint(r, T2_2[(int) i1], r);
    /* the rounding error of mul_dint() is bounded by 6 ulps, which translates
       into 6*2^-127 for the relative error;
       the approximation error for T2_2[i2] is bounded by 2^-128 relatively. */

    /* Total relative errors:
       2^-122.29 from q_2()
       6*2^-127 and 2^-128 from the multiplication by T1_2[i2]
       6*2^-127 and 2^-128 from the multiplication by T2_2[i1].
       With e1=2^-122.29, e2=6*2^-127 and e3=2^-128, this gives:
       (1+e1)*(1+e2)^2*(1+e3)^2 - 1 < 2^-121.70. */

    r.ex = r.ex + M; /* exact */
  }

  /* put in r an approximation of exp(x), for |x| < 744.45,
     with relative error < 2^-241.10 */
  private static void exp_3(Qint64 r, Qint64 x) {
    Qint64 K = new Qint64(), y = new Qint64();


    /* the following multiplication does not need to be fully accurate,
       since it is only used to round x*2^12/log(2) to the nearest integer k */
    mulQint11(K, x, LOG2_INV_Q);
    /* mul_qint_11() is exact, assuming the low part of x is zero, but it is not
       here, thus we ignore low(x)*LOG2_INV_Q < ulp64(744.45)*LOG2_INV_Q
       < 2^-41.47.
       Since LOG2_INV_Q approximates 2^12/log(2) with absolute error < 2^-52.96
       and |x| < 744.45, the error from LOG2_INV_Q is bounded by 2^-43.41.
       This gives a total error < 2^-41.47 + 2^-43.41 < 2^-41.13. This error is
       multiplied by LOG2_Q below, thus yields an error < 2^-53.65 on K in
       the add_qint() call. */

    long k = qintToi(K); /* k = trunc(K) [rounded towards zero, exact] */

    /* |k| <= 4399162 */

    /* the following multiplication needs to be fully accurate, since we need
       to compute x - k*log(2)/2^12 to full accuracy */
    mulQint2(K, k, LOG2_Q);
    /* The rounding error of mul_qint_2() is bounded by 2 ulps, thus since
       |K| <= 4399162*log(2) < 3049267, the error on K is bounded by 2^-233.
       This error is divided by 2^12 below, thus yields < 2^-245. */
    K.ex -= 12;
    K.sgn = K.sgn == 0 ? 1 : 0;

    addQint(y, x, K); /* exact because Sterbenz theorem applies */
    /* If all computations were exact above, we would have
       K = -(x/log(2)-eps/2^12)*log(2) with |eps| < 1 and eps of the same
       sign as x, thus x+K = eps*log(2)/2^12, and thus |y| < log(2)/2^12,
       with y of same sign as x.
       But we have a total rounded error < 2^-53.65 + 2^-245 < 2^-53.64
       thus |y| < log(2)/2^12 + 2^-53.64 < 0.00016923. */

    long M = k >> 12;
    long i2 = (k >> 6) & 0x3f;
    long i1 = k & 0x3f;

    q_3(r, y); /* relative error bounded by 2^-241.11, with |r| < 1.0002 */

    mulQint(r, T1_3[(int) i2], r);
    /* the rounding error of mul_qint() is bounded by 14 ulps, which translates
       into 14*2^-255 for the relative error;
       the approximation error for T1_3[i2] is bounded by 2^-256 relatively. */

    mulQint(r, T2_3[(int) i1], r);
    /* the rounding error of mul_qint() is bounded by 14 ulps, which translates
       into 14*2^-127 for the relative error;
       the approximation error for T2_3[i2] is bounded by 2^-128 relatively. */

    /* Total relative errors:
       2^-241.11 from q_3()
       14*2^-255 and 2^-256 from the multiplication by T1_3[i2]
       14*2^-255 and 2^-256 from the multiplication by T2_3[i1].
       With e1=2^-241.11, e2=14*2^-255 and e3=2^-256, this gives:
       (1+e1)*(1+e2)^2*(1+e3)^2 - 1 < 2^-241.10. */

    r.ex = r.ex + M; /* exact */
  }

  /* The following are pairs (m,y) from the set S defined in [4]
     such that m^y is not exact nor a midpoint, but is at relative
     distance < 2^-112.55 from an exact value or a midpoint.
     All these values should fail the 2nd rounding test, and thus enter
     the exact_pow() routine.

     Note: in [4] the 2nd part of the set S is (x,y) = (m,2^F*n)
     for F integer, -5 <= F < 0, n odd integer, 3 <= n <= 34, m odd integer.
     We believe it should be read instead:
     (x,y) = (2^E*m,2^F*n) for E,F integers, -5 <= F < 0, n odd integer,
     3 <= n <= 34, m odd integer.
     The bound 35 from [2,3] was improved to 34 in [4].

     All these inputs are in the F < 0 case.

     The last entry is the denominator q of y as exact rational.
     If (x,y) is a worst case, any (2^(j*q)*x,y) is also one.
     For some x=2^E*n with n odd, if E is not divisible by q=2^-F,
     then no E+j*q will be, so the corresponding entry will be rejected
     by the "check that E is divisible by 2^-F" test.
     The only remaining one is the first one, where 2^-F divides E.
  */

  /*
    Computes x^y and returns 1 if the result fits into 54 bits, i.e. computes
    exactly x^y for exact and midpoint cases.
    Implements Algorithm detectRoundingBoundaryCase from [4].
    Requires that the relative error between x^y and the approximation z of
    x^y is less than 2^-117: z = x^y * (1 + eps) with |eps| < 2^-117.
    Can return 1 only when (x,y) are in the set S from [4]:
    (a) either y is an integer, 2 <= y <= 34, or
    (b) x=2^E*m with m odd and y = 2^F*n with -5 <= F < 0, n odd, 3 <= n <= 34
    exact is non-zero iff x^y is exactly representable in binary64.
    Note: [2] says 2 <= y <= 35 and 3 <= n <= 35, but the value 35 is not
    possible, since 3^35 has 56 bits.
  */
  private static int exactPow(double[] r, double x, double y, Dint64 z,
                              int exact)
  {
    long _s = z.sgn != 0 ? -1 : 1;

    // Check if x = 2^E
    long[] m = new long[1];
    long[] E = new long[1];
    extract(E, m, x); /* x = 2^E*m with m odd */

    /* x is a power of 2 */
    if (m[0] == 1) {
      double G = (double) E[0] * y;

      if (isInt(G) != 0) {
        r[0] = z.sgn != 0 ? -1.0 : 1.0;
        long g = (long) G;
        pow2(r, g);
        return 1;
      }
      return 0;
    }

    if (y < 0.0 || y > 34.0)
      return 0;

    long[] n = new long[1];
    long[] F = new long[1];
    extract(F, n, y); /* y = 2^F*n with n odd */

    /* since y <= 34, if F >= 0, we have 2^F*n <= 34 */

    if (Long.compareUnsigned(n[0], 34) > 0 || F[0] < -5)
      return 0;

    if (F[0] < 0) { /* case (b) */
      /* check that E is divisible by 2^-F */
      if ((E[0] & (~0L >>> (int) (64 + F[0]))) != 0)
        return 0;

      long[] g = new long[1];
      g[0] = (E[0] >> (int) -F[0]) * n[0]; // since F < 0, the shift by -F is ok
      /* g = E*y */
      long[] Gp = new long[1], kp = new long[1];
      round54(Gp, kp, z); /* z is rounded to k*2^G */

      /* In case exact=0, check condition at line 2 from [4]:
         if |2^G*k-z| >= 2^-116*z, then return false
         This test is not needed if the relative error from
         the 2nd phase is less than 2^-116, since if |2^G*k-z| >= 2^-116*z
         the rounding test from the 2nd phase did succeed.
      */
      if (exact == 0) {
        int cnt = Long.numberOfLeadingZeros(kp[0]);
        Dint64 d = new Dint64(kp[0] << cnt, 0, Gp[0] + 63 - cnt, 1 - z.sgn);
        addDint(d, z, d); /* exact by Sterbenz theorem */
        /* multiply d by 2^116 */
        d.ex += 116;
      /* compare in absolute value with z */
        if (cmpDintAbs(d, z) >= 0)
          return 0;
      }

      if (Gp[0] > g[0])
        return 0;

      /* The following code is used when k is a multiple of a power of 2,
         to reduce to 2^X*r with odd r. It checks whether k is an odd number
         multiplied by 2^(g-G). */
      if ((kp[0] & ~(~1L << (int) (g[0] - Gp[0]))) == (1L << (int) (g[0] - Gp[0]))) {
        r[0] = (double) ((kp[0] >>> (int) (g[0] - Gp[0])) * _s);
        pow2(r, g[0]);
        return 1;
      }
      return 0;
    }

    /* case (a) */

    /* no overflow in n << F since 2^F*n <= 34 */
    long t = n[0] << (int) F[0];
    long k = 1;

    /* Compute k = m^t which should fit into 54 bits.
       Invariant: k*m^t */
    while (t != 0) {
      if ((t & 0x1) != 0) {
        // k = m * k;
        U128 p = U128.mul(m[0], k);
        if (p.hi != 0) /* m*k overflows */
          return 0;
        k = p.lo;
      }
      t = t >>> 1;
      // m *= m;
      if (t != 0) {
        U128 p = U128.mul(m[0], m[0]);
        if (p.hi != 0) /* m*m overflows */
          return 0;
        m[0] = p.lo;
      }
    }

    /* check k has at most 54 bits */
    if ((k >>> 54) != 0)
      return 0;

    r[0] = (double) (k * _s);
    long G = E[0] * (n[0] << (int) F[0]);
    pow2(r, G);

    // end:
    return 1;
  }

  // return non-zero if x^y is exact (and exactly representable as a double)
  private static int isExact(double x, double y) {
    /* All cases such that x^y might be exact are:
       (a) |x| = 1
       (b) y integer, 0 <= y <= 33
       (c) y<0: x=1 or (x=2^e and |y|=n*2^-k with 2^k dividing e)
       (d) y>0: y=n*2^f with -5 <= f <= -1 and 1 <= n <= 33
       In cases (b)-(d), the low 42 bits of the encoding of y are zero,
       thus we use that for an early exit test. */

    long v = Double.doubleToRawLongBits(x), w = Double.doubleToRawLongBits(y);
    if ((v << 1) != 0x7fe0000000000000L &&
        (w << 22) != 0)
      return 0;

    if ((v << 1) == 0x7fe0000000000000L) // |x| = 1
      return 1;

    // xmax[y] for 1<=y<=33 is the largest odd m such that m^y fits in 53 bits
    if (y >= 0 && isInt(y) != 0) {
      /* let x = m*2^e with m an odd integer, x^y is exact when
         - y = 0 or y = 1
         - m = 1 or -1 and -1074 <= e*y < 1024
         - if |x| is not a power of 2, 2 <= y <= 33 and
           m^y should fit in 53 bits
      */
      long m = v & 0xfffffffffffffL;
      long e = ((v << 1) >>> 53) - 0x433;
      if (e >= -1074)
        m |= 0x10000000000000L;
      else // subnormal numbers
        e++;
      int t = Long.numberOfTrailingZeros(m);
      m = m >>> t;
      e += t;
      /* For normal numbers, we have x = m*2^e. */
      if (y == 0 || y == 1)
        return 1;
      if (m == 1)
        return -1074 <= y * e && y * e < 1024 ? 1 : 0;
      // now for y < 0 or 33 < y it cannot be exact
      if (y < 0 || 33 < y)
        return 0;
      // now 2 <= y <= 33
      int y_int = (int) y;
      if (Long.compareUnsigned(m, xmax[y_int]) > 0)
        return 0;
      // |x^y| = m^y * 2^(e*y)
      long my = m * m;
      for (int i = 2; i < y_int; i++)
        my = my * m;
      // my = m^y
      t = 64 - Long.numberOfLeadingZeros(m);
      // 2^(t-1) <= m^y < 2^t thus 2^(e*y + t - 1) <= |x^y| < 2^(e*y + t)
      long ez = e * y_int + t;
      if (ez <= -1074 || 1024 < ez)
        return 0;
      // since m is odd, x^y is an odd multiple of 2^(e*y)
      return e * y_int >= -1074 ? 1 : 0;
    }

    long n = w & 0xfffffffffffffL;
    long f = ((w << 1) >>> 53) - 0x433;
    if (f >= -1074)
      n |= 0x10000000000000L;
    else // subnormal numbers
      f++;
    int t = Long.numberOfTrailingZeros(n);
    n = n >>> t;
    f += t;
    // |y| = n*2^f with n odd

    long m = v & 0xfffffffffffffL;
    long e = ((v << 1) >>> 53) - 0x433;
    if (e >= -1074)
      m |= 0x10000000000000L;
    else // subnormal numbers
      e++;
    t = Long.numberOfTrailingZeros(m);
    m = m >>> t;
    e += t;
    // |x| = m*2^e with m odd

    /* if y < 0 and y is not an integer, the only case where x^y might be
       exact is when x = 2^e and n*e*2^f is an integer */
    if (y < 0)
    {
      if (m != 1)
        return 0;
      // now e <> 0 since the case |x|=1 has already been treated
      long ez;
      if (f >= 0)
        // if f >= 12, since n*e <> 0, (n*e)<<f cannot be in [-1074,1024)
        ez = (f < 12) ? (-n * e) << (int) f : 1024;
      else { // f < 0 thus 2^-f should divide e
        t = Long.numberOfTrailingZeros(e);
        if (-f > t)
          return 0; // 2^-f does not divide e
        ez = (-e >> (int) -f) * n;
      }
      return -1074 <= ez && ez < 1024 ? 1 : 0;
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
      double dm = (double) m;
      double s = (double) Math.round(Math.sqrt(dm));
      if (s * s != dm)
        return 0;
      /* The above call of sqrt() might set the inexact flag, but in case
         it happens, m is not a square, thus x^y cannot be exact. */
      m = (long) s; // m remains odd (square root of an odd number)
    }

    // Now |x^y| = (m*2^e)^n with m, n odd integers
    // now for 33 < n it cannot be exact, unless m=1
    if (m > 1)
    {
      if (Long.compareUnsigned(33, n) < 0)
        return 0;
      // now n <= 33
      if (Long.compareUnsigned(m, xmax[(int) n]) > 0)
        return 0;
    }
    // |x^y| = m^n * 2^(e*n) with m odd
    long my = m, n0 = n;
    while (n0-- > 1)
      my = my * m;
    // |x^y| = my * 2^(e*n)
    t = 64 - Long.numberOfLeadingZeros(my); // number of significant bits of m^n
    /* x^y is an odd multiple of 2^(e*n) thus we should have e*n >= -1074,
       we also have 2^(t-1) <= m^n thus 2^(e*n+t-1) <= |x^y| < 2^(e*n+t)
       and we need e*n+t <= 1024 */
    return -1074 <= e * n && e * n + t <= 1024 ? 1 : 0;
  }

  // #define ERR_BND_2 inside cr_pow(), in the normal-range branch of the
  // second-iteration rounding test
  private static final long ERR_BND_2 = 28;

  // #define ERR_BND_3 60 /* floor(62433/2^10) */ inside cr_pow()
  private static final long ERR_BND_3 = 60;

  // Correctly rounded power function
  public static double compute(double x, double y) {
    double s = 1.0; /* sign of the result */
    double x0 = x; // original value of x

    long _x = Double.doubleToRawLongBits(x);
    long _y = Double.doubleToRawLongBits(y);

    if (Long.compareUnsigned(_x, 0x7ff0000000000000L) >= 0
        || Long.compareUnsigned(_y, 0x7ff0000000000000L) >= 0) {

      if (Double.isNaN(x)) {
        // IEEE 754-2019: pow(x,+/-0) = 1 if x is not a signaling NaN
        if (y == 0.0 && isSignaling(x) == 0)
          return 1.0;

        /* pow(sNaN, y) = qNaN. This is implicit in IEEE 754-2019,
           Section 7.2: "the default result of an operation that signals the
           invalid operation exception shall be a quiet NaN" and "These
           operations are: a) any general-computational operation on a signaling
           NaN".

           Moreover, in 6.2.3:
           "An operation that propagates a NaN operand to its result and has a
           single NaN as an input should produce a NaN with the payload of the
           input NaN if representable in the destination format. If two or more
           inputs are NaN, then the payload of the resulting NaN should be
           identical to the payload of one of the input NaNs if representable in
           the destination format. This standard does not specify which of the
           input NaNs will provide the payload."

           Returning x+x has the double effect to quiet the signaling bit
           and to raise the invalid exception if x=sNaN. */
        return x + x;
      }

      if (Double.isNaN(y)) {
        // IEEE 754-2019: pow(1,y) = 1 for any y (even a quiet NaN)
        if (x == 1.0 && isSignaling(y) == 0)
          return 1.0;

        // pow(x, sNaN) = qNaN (see above)
        return y + y;
      }

      /* The C code switches on _x.u here; Java cannot switch on a long, so the
         two cases become an if/else chain, and the switch on _y.u below does the
         same. */

      if (_x == 0x7ff0000000000000L) { // x = +inf
        if (y == 0.0)
          return 1.0;

        if (y < 0.0)
          return 0.0;

        if (y > 0.0)
          return Double.POSITIVE_INFINITY;
      } else if (_x == 0xfff0000000000000L) { // x = -inf
        /* first check y=+/-inf since is_int uses roundeven_finite
           which might raise spurious invalid for Inf input */
        if (_y == 0x7ff0000000000000L)
          return y; // -Inf^Inf = Inf

        if (_y == 0xfff0000000000000L)
          return +0.0; // -Inf^-Inf = +0

        // y is an odd integer
        if (isInt(y) != 0 && isInt(y * 0.5) == 0) {

          // y is a negative odd integer
          if (y < 0.0)
            return -0.0;

          // y is a positive odd integer
          else
            return Double.NEGATIVE_INFINITY;
        }

        // y is a negative even integer or is negative non-integer
        if (y < 0.0)
          return 0.0;

        // y is a positive even integer or is positive non-integer
        if (y > 0.0)
          return Double.POSITIVE_INFINITY;
      }

      if (_y == 0x7ff0000000000000L) { // y = +inf
        if (x == 0.0)
          return 0.0;

        if (x == -1.0 || x == 1.0)
          return 1.0;

        if (-1.0 < x && x < 1.0)
          return 0.0;

        if (x < -1.0 || 1.0 < x)
          return Double.POSITIVE_INFINITY;
      } else if (_y == 0xfff0000000000000L) { // y = -inf
        if (x == 0.0)
          return Double.POSITIVE_INFINITY;

        if (x == -1.0 || x == 1.0)
          return 1.0;

        if (-1.0 < x && x < 1.0)
          return Double.POSITIVE_INFINITY;

        if (x < -1.0 || 1.0 < x)
          return 0.0;
      }
    } // From now on, x and y are finite values

    /* first deal with the case x <= 0 */
    if (x <= 0.0) {
      /* pow(x,+/-0) is 1 if x is not a signaling NaN. */
      if (y == 0.0)
        return 1.0;

      if (_x == 0x0L) { // x = +0.0
        // y is an odd integer
        if (isInt(y) != 0 && isInt(y * 0.5) == 0) {

          // y is a negative odd integer
          if (y < 0.0) {
            // the C source raises FE_DIVBYZERO here
            return Double.POSITIVE_INFINITY;
          }

          // y is a positive odd integer
          return 0.0;
        }

        // y is positive (non-integer or a positive even integer)
        if (y > 0.0)
          return 0.0;

        // y is negative, finite and an even integer or a non-integer
        // the C source raises FE_DIVBYZERO here
        return Double.POSITIVE_INFINITY;
      } else if (_x == 0x8000000000000000L) { // x = -0.0
        // y is an odd integer
        if (isInt(y) != 0 && isInt(y * 0.5) == 0) {

          // y is a negative odd integer
          if (y < 0.0) {
            // the C source raises FE_DIVBYZERO here
            return Double.NEGATIVE_INFINITY;
          }

          // y is a positive odd integer
          return -0.0;
        }

        // y is positive (non-integer or a positive even integer)
        if (y > 0.0)
          return 0.0;


        // y is negative, finite and an even integer or a non-integer
        // the C source raises FE_DIVBYZERO here
        return Double.POSITIVE_INFINITY;
      }

      if (isInt(y) == 0) {
        // the C source sets errno to EDOM and raises FE_INVALID here
        return Double.NaN;
      }

      double[] cs = {1.0, -1.0};

      // set sign to 1 for y even, to -1 for y odd
      long y_parity = Math.abs(y) >= 0x1p53 ? 0 : ((long) y & 0x1);
      s = cs[(int) y_parity];

      // Set x to |x| for the rest of the computation
      x = -x;
    } /* end of case x <= 0 */

    /* This is Algorithm phase_1 from reference [5]. */
    double[] res_h = new double[1], res_l = new double[1];

    double[] lh = new double[1], ll = new double[1];

    // approximate log(x)
    int cancel = log_1(lh, ll, x);

    /* We should avoid a spurious underflow/overflow in y*log(x).
       Underflow: for x<>1, the smallest absolute value of log(x) is obtained
       for x=1-2^-53, with |log(x)| ~ 2^-53. Thus to avoid a spurious underflow
       we require |y| >= 2^-969.
       Overflow: the largest absolute value of log(x) is obtained for x=2^-1074,
       with |log(x)| < 745. Thus to avoid a spurious overflow we require
       |y| < 2^1014. */
    int ey = (int) ((_y >>> 52) & 0x7ff);
    if (ey < 0x36 || ey >= 0x7f5)
      lh[0] = ll[0] = Double.NaN;

    // approximate y * log(x)
    double[] rh = new double[1], rl = new double[1];
    sMul(rh, rl, y, lh[0], ll[0]);

    /* We prove in Lemma 5 from reference [5] that if the exact product y*lh
       satisfies 2^-969 <= |y*lh| <= 709.7827, then 2^-970 <= |rh| <= 709.79,
       |rl| <= 2^-14.4187, |rl/rh| <= 2^-23.8899, |rh+rl| <= 709.79 and:

       |rh + rl - y log(x)| <= emul

       with emul = 2^-63.799 if x is not in (1/sqrt(2), sqrt(2))
       and  emul = 2^-57.580 if 1/sqrt(2) < x < sqrt(2)
    */

    exp_1(res_h, res_l, rh[0], rl[0], s); /* 1 <= res_h < 2 */
    /* See Lemma 7 from reference [5] for the error analysis of exp_1(). */

    /* avoid a spurious underflow: if |rh| < 2^-511, then exp(rh+rl) will
       round to 1; the C source restores the inexact flag here */

    /* The error bounds 2^-63.797 and 2^-57.579 are those from Algorithm
       phase_1 from reference [5]. */
    double res_min, res_max;
    res_min = res_h[0] + Math.fma(err[cancel], -res_h[0], res_l[0]);
    res_max = res_h[0] + Math.fma(err[cancel], res_h[0], res_l[0]);
    /* if res_h < 0, we have res_max < res_min, but since we only check
       equality between res_min and res_max, it does not matter */

    int exact = isExact(x, y);
    // the C source restores the inexact flag here when exact is non-zero

    if (res_min == res_max) {
      /* when res_min * ex is in the subnormal range, exp_1() returns NaN
         to avoid double-rounding issues */
      return res_max;
    }
    /* the idea of returning res_max instead of res_min is due to Laurent
       Théry: it is better in case of underflow since res_max = +0 always. */

    // Easy cases
    if (y == 1.0)
      return s * x;

    if (y == 2.0) {
      double z = x * x;
      return z;
    }

    if (y == 0.5)
      return Math.sqrt(x);

    if (y == 0.0)
      return 1.0;

    long rd; // used in the 2nd and 3rd phases

  // Second iteration of rounding
    Dint64 X = new Dint64(), Y = new Dint64();
    dintFromd(X, x); /* exact: |x| = 2^(X->ex-63) * X->hi (X->lo = 0) */
    dintFromd(Y, y); /* exact: |y| = 2^(Y->ex-63) * Y->hi (Y->lo = 0) */

    X.sgn = 0x0; /* force the sign of X to +1 */

    Dint64 R = new Dint64();
    log_2(R, X); /* relative error bounded by 2^-122.88 */

    mulDint21(R, R, Y);
    /* The rounding error of mul_dint_21() is bounded by 2 ulps, which is at most
       2*2^-127 in terms of relative error. Thus the relative error on R is
       bounded by (1+e1)*(1+e2)-1 with e1=2^-122.88 and e2=2*2^-127,
       which gives: R = y*log|x| * (1+eps1) with |eps1| < 2^-122.72 */

    exp_2(R, R); /* relative error < 2^-121.70:
                    R = exp(R_in) * (1+eps2) with |eps2| < 2^-121.70 */

    /* We thus have R = |x|^y * exp(y*log|x|*eps1) * (1+eps2).
       Since y*log|x| < 744.45, we have |y*log|x|*eps1| < 744.45*2^-122.72
       < eps3 = 2^-113.179 thus the relative error is bounded by
       exp(eps3)*(1+eps2)-1 < 2^-113.17.
       This corresponds to an error of at most 2^-113.17*2^128 < 29126 ulps. */

    /* Remark: since eps3 = 2^-113.179, it would suffice to get about 113 bits
       of accuracy in exp_2(). We tried to reduce the degree of the minimax
       polynomial in q_2() from 7 to 6, or to use only 64 bits for the degree-3
       coefficient, but in both cases the accuracy was too small. */

    // Rounding test

    // 2^R.ex <= R < 2^(R.ex+1)

    /* case R < 2^-1075: underflow case */
    if (R.ex < -1075) {
      return 0.5 * (s * 0x1p-1074);
    }

    if (R.ex < -1022) { /* subnormal case */
      // for x^y = 2^-1022, we can have R < 2^-1022 here

      /* -1075 <= R.ex <= -1023 thus 2^-1075 <= R < 2^-1022 */
      long ex = -(1022 + R.ex); /* 1 <= ex <= 53 */
      // the significand has to be shifted right by ex bits
      long m = (R.lo >>> (int) (10 + ex)) | (R.hi << (int) (54 - ex));

      /* We always have underflow when ex >= 2. However for ex=1,
         where 2^-1023 <= R < 2^-1022, we might not have underflow
         for rounding up if R >= 2^-1022 - 2^-1075, and for rounding
         to nearest for R >= 2^-1022 - 2^-1076. */

      /* In principle, the bound 28 which holds for the normal case below
         should be replaced by ceil(28/2^ex) since the relative error bound
         is the same as for the normal case, but since the round bit is 'shifted'
         by 'ex' bits to the left, we get 'ex' extra bits after the round bit.
         Since ex>=1 we replace 28 by 14. */

      rd = Long.compareUnsigned(m + 14, 2 * 14) > 0 ? 1 : 0;

    } else {
      long lo = (R.lo >>> 10) | (R.hi << 54);
      /* lo contains the 64 bits after the round bit */
      rd = Long.compareUnsigned(lo + ERR_BND_2, 2 * ERR_BND_2) > 0 ? 1 : 0;
      /* The value 28 comes from floor(29126/2^10): rd is 0 when lo <= 28 or
         lo >= 2^64 - 28, which means that the approximation R is at distance
         < 29*2^10 = 29696 ulps of a rounding boundary */
    }

    R.sgn = s == -1.0 ? 1 : 0;

    if (rd != 0)
      // dint_tod should deal with underflow/overflow/errno issues
      return dintTod(R, exact);

    // Detect rounding boundary cases
    double[] e = new double[1];

    if (exactPow(e, x0, y, R, exact) != 0)
      return e[0];

    /* Note: exact and midpoint cases should be filtered by
       exact_pow() above, and should not enter the 2nd iteration.
       Thus check.sh might fail when POW_ITERATION | 2 == 0
       or POW_ITERATION | 4 == 0. */

    // Hard to round cases
    Qint64 qX = new Qint64(), qY = new Qint64();
    qintFromd(qX, x); /* exact */
    qintFromd(qY, y); /* exact */

    qX.sgn = 0x0; /* force the sign of X to +1 */

    Qint64 qR = new Qint64();
    log_3(qR, qX); /* relative error bounded by 2^-250.74 */

    mulQint41(qR, qR, qY);
    /* The rounding error of mul_qint_41() is bounded by 2 ulps, which is at most
       2*2^-255 in terms of relative error. Thus the relative error on qR is
       bounded by (1+e1)*(1+e2)-1 with e1=2^-250.74 and e2=2*2^-255,
       which gives a relative error less than 2^-250.59:
       qR = y*log|x| * (1+eps1) with |eps1| < 2^-250.59 */

    Qint64 qZ = new Qint64();
    exp_3(qZ, qR); /* relative error < 2^-241.10:
                      qZ = exp(qR) * (1+eps2) with |eps2| < 2^-241.10 */

    /* We thus have qZ = |x|^y * exp(y*log|x|*eps1) * (1+eps2).
       Since y*log|x| < 744.45, we have |y*log|x|*eps1| < 744.45*2^-250.59
       < eps3 = 2^-241.049 thus the relative error is bounded by
       exp(eps3)*(1+eps2)-1 < 2^-240.07.
       This corresponds to an error of at most 2^-240.07*2^256 < 62433 ulps. */

    /* extra rounding test */
    long r1 = (qZ.hh << 54) | (qZ.hl >>> 10);
    long r2 = (qZ.hl << 54) | (qZ.lh >>> 10);
    long r3 = (qZ.lh << 54) | (qZ.ll >>> 10);
    rd = !((r1 == 0 && r2 == 0 && Long.compareUnsigned(r3, ERR_BND_3) <= 0)
           || (~r1 == 0 && ~r2 == 0
               && Long.compareUnsigned(r3 + (2 * ERR_BND_3), ERR_BND_3) <= 0)) ? 1 : 0;

    if (rd != 0)
    {
      qZ.sgn = s == -1.0 ? 1 : 0;
      qZ.ll = qZ.ll & (~0L << 10);

      return qintTod(qZ);
    }

    /* We can end up here for x^y very close to 1. For |qR| < 2^-55,
       we have 1-2^-54 < exp(qR) < 1+2^-53, thus exp(qR) rounds either
       to nextbelow(1), to 1 or to nextabove(1). */
    if (qR.ex < -56) /* the upper limb h of qR encodes h/2^63, thus a number
                        in [1, 2) */
      return (qR.sgn == 0x0) ? 1.0 + 0x1p-100 : 1.0 - 0x1p-100;

    /* The C source prints the worst case and calls exit (1) here. */
    throw new IllegalStateException("Unexpected worst-case found: pow("
        + Double.toHexString(x) + ", " + Double.toHexString(y) + ")");
  }

}
