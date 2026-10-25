/*
 * Correctly-rounded tangent function for binary64 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/tan/tan.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_tan -> Tan.compute
 *
 * Copyright (c) 2022-2025 Paul Zimmermann and Tom Hubrecht
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
import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded tangent of a binary64 value, the translation of the CORE-MATH
 * {@code cr_tan}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code dint64_t} becomes {@link Dint64} with the same fields; the
 *       little-endian member of the byte-order {@code #if} is the one the
 *       reference build compiles, so the big-endian one is dropped, and
 *       {@code unsigned __int128} becomes {@link U128}.
 *   <li>{@code fast_extract} takes the {@link Dint64} it is called on instead of
 *       the addresses of its {@code ex} and {@code hi} fields.
 *   <li>{@code double *}, {@code u128 *} and {@code int *} output parameters
 *       become one-element arrays.
 *   <li>{@code subnormalize_dint} switches on {@code fegetround()}. The JVM has
 *       no floating-point environment and round-to-nearest-even is the only
 *       mode, so only the {@code FE_TONEAREST} case is kept; the
 *       {@code FE_DOWNWARD} and {@code FE_UPWARD} cases, which belong to the
 *       directed rounding modes that are out of scope, are dropped.
 *   <li>The {@code x - x} that {@code cr_tan} returns for an infinity to raise
 *       {@code FE_INVALID} becomes {@link Double#NaN}.
 *   <li>{@code Tinv} is {@code static uint64_t} rather than {@code static const}
 *       upstream but is never written, so it is a {@code private static final}
 *       field here. The {@code print_dint} helper behind {@code #if 0} is not
 *       translated.
 *   <li>The {@code CH} and {@code CL} macros become fields, as do the
 *       {@code sgn} array of {@code cos_fast} and the {@code exceptions} table
 *       of {@code cos_accurate}.
 *   <li>{@code uint64_t} shifts and comparisons stay unsigned through
 *       {@code >>>}, {@link Long#compareUnsigned} and
 *       {@link U128#compareUnsigned}.
 * </ul>
 */
public final strictfp class Tan {
  private Tan() {
  }

  // Extract both the mantissa and exponent of a double
  // The two output parameters of the C function are the ex and hi fields of the
  // dint64_t it is called on.
  private static void fastExtract(Dint64 a, double x) {
    long _x = Double.doubleToRawLongBits(x);

    a.ex = (_x >>> 52)&0x7ff;
    a.hi = (_x&(~0L >>> 12)) + (a.ex != 0 ? (1L << 52) : 0);
    a.ex = a.ex - 0x3fe;
  }

  // Return non-zero if a = 0
  private static boolean dintZeroP(Dint64 a) {
    return a.hi == 0;
  }

  private static int cmp(long a, long b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }

  private static int cmpu128(U128 a, U128 b) { return Integer.signum(a.compareUnsigned(b)); }

  private static final Dint64 ZERO = new Dint64(0x0L, 0x0L, -1076, 0);
  /* ONE is a dint64_t representation of 1 */
  private static final Dint64 ONE = new Dint64(0x8000000000000000L, 0x0L, 1, 0);
  // MAGIC is a dint64_t representation of 1/2^11
  private static final Dint64 MAGIC = new Dint64(0x8000000000000000L, 0x0L, -10, 0);

  // Compare the absolute values of a and b
  // Return -1 if |a| < |b|
  // Return  0 if |a| = |b|
  // Return +1 if |a| > |b|
  private static int cmpDintAbs(Dint64 a, Dint64 b) {
    if (dintZeroP(a))
      return dintZeroP(b) ? 0 : -1;
    if (dintZeroP(b))
      return +1;
    int c1 = cmp(a.ex, b.ex);
    return c1 != 0 ? c1 : cmpu128(a.r(), b.r());
  }

  // Copy a dint64_t value
  private static void cpDint(Dint64 r, Dint64 a) {
    r.ex = a.ex;
    r.r(a.r());
    r.sgn = a.sgn;
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
        Dint64 tmp = a; a = b; b = tmp;
        break; // fall through the case |A| > |B|
      }
    }

    // From now on, |A| > |B| thus a.ex >= b.ex

    U128 A = a.r(), B = b.r();
    long k = a.ex - b.ex;

    if (k > 0) {
      /* Warning: the right shift x >> k is only defined for 0 <= k < n
         where n is the bit-width of x. See for example
         https://developer.arm.com/documentation/den0024/a/The-A64-instruction-set/Data-processing-instructions/Shift-operations
         where it is said that k is interpreted modulo n. */
      B = (k < 128) ? B.shr((int)k) : U128.of(0, 0);
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
      long ex = ch != 0 ? Long.numberOfLeadingZeros(ch) : 64 + Long.numberOfLeadingZeros(C.lo);
      /* The error from the truncated part of B (1 ulp) is multiplied by 2^ex,
         thus by 2 ulps when ex <= 1. */
      if (ex > 0) {
        if (k == 1) /* Sterbenz case */
          C = A.shl((int)ex).sub(b.r().shl((int)(ex - 1)));
        else
          C = A.shl((int)ex).sub(B.shl((int)ex));
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
      C = C.shl((int)ex);
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
    r.r(r.r().add(m1.shr(64).add(m2.shr(64))));

    // Ensure that r->hi starts with a 1
    long ex = r.hi >>> 63;
    r.r(r.r().shl((int)(1 - ex)));

    // Exponent and sign
    // if ex=1, then ex(r) = ex(a) + ex(b)
    // if ex=0, then ex(r) = ex(a) + ex(b) - 1
    r.ex = a.ex + b.ex + ex - 1;
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
    r.r(r.r().shl((int)(1 - ex)));

    // Exponent and sign
    r.ex = a.ex + b.ex + ex - 1;
    r.sgn = a.sgn ^ b.sgn;

    /* The ignored part can be as large as 1 ulp before the shift (truncated
       part of lo). After the shift this can be as large as 2 ulps. */
  }

  /* for 0 <= i < 256, Tinv[i] = floor(2^127/(2^63+i*2^55+2^55-1)) */
  private static final long[] Tinv = {
    0xff00ff00ff00ff02L, 0xfe03f80fe03f80ffL, 0xfd08e5500fd08e56L, 0xfc0fc0fc0fc0fc11L,
    0xfb18856506ddaba7L, 0xfa232cf252138ac1L, 0xf92fb2211855a867L, 0xf83e0f83e0f83e11L,
    0xf74e3fc22c700f76L, 0xf6603d980f6603dbL, 0xf57403d5d00f5742L, 0xf4898d5f85bb3952L,
    0xf3a0d52cba872338L, 0xf2b9d6480f2b9d66L, 0xf1d48bcee0d399fcL, 0xf0f0f0f0f0f0f0f2L,
    0xf00f00f00f00f010L, 0xef2eb71fc4345239L, 0xee500ee500ee5010L, 0xed7303b5cc0ed731L,
    0xec979118f3fc4da3L, 0xebbdb2a5c1619c8dL, 0xeae56403ab959010L, 0xea0ea0ea0ea0ea10L,
    0xe939651fe2d8d35dL, 0xe865ac7b7603a198L, 0xe79372e225fe30daL, 0xe6c2b4481cd8568aL,
    0xe5f36cb00e5f36ccL, 0xe525982af70c880fL, 0xe45932d7dc52100fL, 0xe38e38e38e38e38fL,
    0xe2c4a6886a4c2e11L, 0xe1fc780e1fc780e3L, 0xe135a9c97500e137L, 0xe070381c0e070383L,
    0xdfac1f74346c5760L, 0xdee95c4ca037ba58L, 0xde27eb2c41f3d9d2L, 0xdd67c8a60dd67c8bL,
    0xdca8f158c7f91ab9L, 0xdbeb61eed19c5959L, 0xdb2f171df770291aL, 0xda740da740da740fL,
    0xd9ba4256c0366e92L, 0xd901b2036406c80fL, 0xd84a598ec9151f44L, 0xd79435e50d79435fL,
    0xd6df43fca482f00eL, 0xd62b80d62b80d62cL, 0xd578e97c3f5fe552L, 0xd4c77b03531dec0eL,
    0xd4173289870ac52eL, 0xd3680d3680d3680eL, 0xd2ba083b445250acL, 0xd20d20d20d20d20eL,
    0xd161543e28e50275L, 0xd0b69fcbd2580d0cL, 0xd00d00d00d00d00eL, 0xcf6474a8819ec8eaL,
    0xcebcf8bb5b4169ccL, 0xce168a7725080ce2L, 0xcd712752a886d243L, 0xccccccccccccccceL,
    0xcc29786c7607f99fL, 0xcb8727c065c393e1L, 0xcae5d85f1bbd6c96L, 0xca4587e6b74f032aL,
    0xc9a633fcd967300dL, 0xc907da4e871146adL, 0xc86a78900c86a78aL, 0xc7ce0c7ce0c7ce0dL,
    0xc73293d789b9f839L, 0xc6980c6980c6980dL, 0xc5fe740317f9d00dL, 0xc565c87b5f9d4d1cL,
    0xc4ce07b00c4ce07cL, 0xc4372f855d824ca6L, 0xc3a13de60495c774L, 0xc30c30c30c30c30dL,
    0xc2780613c0309e02L, 0xc1e4bbd595f6e948L, 0xc152500c152500c2L, 0xc0c0c0c0c0c0c0c1L,
    0xc0300c0300c0300dL, 0xbfa02fe80bfa02ffL, 0xbf112a8ad278e8deL, 0xbe82fa0be82fa0bfL,
    0xbdf59c91700bdf5aL, 0xbd69104707661aa3L, 0xbcdd535db1cc5b7cL, 0xbc52640bc52640bdL,
    0xbbc8408cd63069a1L, 0xbb3ee721a54d880cL, 0xbab656100bab6562L, 0xba2e8ba2e8ba2e8cL,
    0xb9a7862a0ff46588L, 0xb92143fa36f5e02fL, 0xb89bc36ce3e0453bL, 0xb81702e05c0b8171L,
    0xb79300b79300b794L, 0xb70fbb5a19be3659L, 0xb68d31340e4307d9L, 0xb60b60b60b60b60cL,
    0xb58a485518d1e7e4L, 0xb509e68a9b948220L, 0xb48a39d44685fe97L, 0xb40b40b40b40b40cL,
    0xb38cf9b00b38cf9bL, 0xb30f63528917c80cL, 0xb2927c29da5519d0L, 0xb21642c8590b2165L,
    0xb19ab5c45606f00cL, 0xb11fd3b80b11fd3cL, 0xb0a59b418d749d54L, 0xb02c0b02c0b02c0bL,
    0xafb321a1496fdf0fL, 0xaf3addc680af3adeL, 0xaec33e1f671529a5L, 0xae4c415c9882b931L,
    0xadd5e6323fd48a87L, 0xad602b580ad602b6L, 0xaceb0f891e6551bcL, 0xac7691840ac76919L,
    0xac02b00ac02b00acL, 0xab8f69e28359cd12L, 0xab1cbdd3e2970f60L, 0xaaaaaaaaaaaaaaabL,
    0xaa392f35dc17f00bL, 0xa9c84a47a07f5638L, 0xa957fab5402a55ffL, 0xa8e83f5717c0a8e9L,
    0xa87917088e262b70L, 0xa80a80a80a80a80bL, 0xa79c7b16ea64d422L, 0xa72f05397829cbc2L,
    0xa6c21df6e1625c80L, 0xa655c4392d7b73a8L, 0xa5e9f6ed347f0721L, 0xa57eb50295fad40bL,
    0xa513fd6bb00a5140L, 0xa4a9cf1d96833751L, 0xa44029100a440291L, 0xa3d70a3d70a3d70bL,
    0xa36e71a2cb033129L, 0xa3065e3fae7cd0e0L, 0xa29ecf163bb6500aL, 0xa237c32b16cfd772L,
    0xa1d139855f7268eeL, 0xa16b312ea8fc377dL, 0xa105a932f2ca891fL, 0xa0a0a0a0a0a0a0a1L,
    0xa03c1688732b3032L, 0x9fd809fd809fd80aL, 0x9f747a152d7836d0L, 0x9f1165e7254813e2L,
    0x9eaecc8d53ae2ddfL, 0x9e4cad23dd5f3a20L, 0x9deb06c9194aa416L, 0x9d89d89d89d89d8aL,
    0x9d2921c3d6411308L, 0x9cc8e160c3fb19b9L, 0x9c69169b30446dfaL, 0x9c09c09c09c09c0aL,
    0x9baade8e4a2f6e10L, 0x9b4c6f9ef03a3caaL, 0x9aee72fcf957c10fL, 0x9a90e7d95bc609a9L,
    0x9a33cd67009a33ceL, 0x99d722dabde58f06L, 0x997ae76b50efd00aL, 0x991f1a515885fb37L,
    0x98c3bac74f5db00aL, 0x9868c809868c8099L, 0x980e4156201301c8L, 0x97b425ed097b425fL,
    0x975a750ff68a58afL, 0x97012e025c04b80aL, 0x96a850096a850097L, 0x964fda6c0964fda7L,
    0x95f7cc72d1b887e9L, 0x95a02568095a0257L, 0x9548e4979e0829fdL, 0x94f2094f2094f209L,
    0x949b92ddc02526e5L, 0x9445809445809446L, 0x93efd1c50e726b7cL, 0x939a85c40939a85cL,
    0x93459be6b009345aL, 0x92f113840497889cL, 0x929cebf48bbd90e5L, 0x9249249249249249L,
    0x91f5bcb8bb02d9cdL, 0x91a2b3c4d5e6f809L, 0x9150091500915009L, 0x90fdbc090fdbc091L,
    0x90abcc0242af3009L, 0x905a38633e06c43bL, 0x9009009009009009L, 0x8fb823ee08fb823fL,
    0x8f67a1e3fdc26179L, 0x8f1779d9fdc3a219L, 0x8ec7ab397255e41dL, 0x8e78356d1408e783L,
    0x8e2917e0e702c6ceL, 0x8dda520237694809L, 0x8d8be33f95d71590L, 0x8d3dcb08d3dcb08dL,
    0x8cf008cf008cf009L, 0x8ca29c046514e023L, 0x8c55841c815ed5caL, 0x8c08c08c08c08c09L,
    0x8bbc50c8deb420c0L, 0x8b70344a139bc75bL, 0x8b246a87e19008b2L, 0x8ad8f2fba9386823L,
    0x8a8dcd1feeae465cL, 0x8a42f8705669db46L, 0x89f87469a23920e0L, 0x89ae4089ae4089aeL,
    0x89645c4f6e055decL, 0x891ac73ae9819b50L, 0x88d180cd3a4133d7L, 0x8888888888888889L,
    0x883fddf00883fddfL, 0x87f78087f78087f8L, 0x87af6fd5992d0d40L, 0x8767ab5f34e47ef1L,
    0x872032ac13008720L, 0x86d905447a34acc6L, 0x869222b1acf1ce96L, 0x864b8a7de6d1d608L,
    0x86053c345a0b8473L, 0x85bf37612cee3c9bL, 0x85797b917765ab89L, 0x8534085340853408L,
    0x84eedd357c1b0085L, 0x84a9f9c8084a9f9dL, 0x84655d9bab2f1008L, 0x8421084210842108L,
    0x83dcf94dc7570ce1L, 0x839930523fbe3368L, 0x8355ace3c897db10L, 0x83126e978d4fdf3bL,
    0x82cf750393ac3319L, 0x828cbfbeb9a020a3L, 0x824a4e60b3262bc5L, 0x8208208208208208L,
    0x81c635bc123fdf8eL, 0x81848da8faf0d277L, 0x814327e3b94f462fL, 0x8102040810204081L,
    0x80c121b28bd1ba98L, 0x8080808080808081L, 0x8040201008040201L, 0x8000000000000000L,
  };

  /* put in r an approximation of 1/a, assuming a is not zero,
     with error bounded by 4.003 ulps (relative error < 2^-124.999) */
  private static void invDint(Dint64 r, Dint64 a) {
    long h = a.hi; /* 2^63 <= h < 2^64 */
    /* First compute a 64-bit inverse t of h, such that
       t*h ~ 2^127, see routine inv_dint() in tan.sage.
       We note a = h/2^63, then 1 <= a < 2, and we write x = t/2^64 for
       the approximation of 1/a, with 1/2 <= x < 1. */
    int i = (int)((h>>>55)&0xff);
    long t = Tinv[i];
    /* now t is accurate to about 8 bits, more precisely the integer residual
       2^127 - h*t is bounded by 662027951208051476078044039717322752 < 2^118.995
       (attained for i=0 and h=2^63). The integer residual 2^127 - t*h
       equals 2^127*(1-a*x), thus 0 <= 1-ax < 2^-8.005. */

    /* first Newton iteration */
    U128 e = U128.of(0, 1L << 63).sub(U128.mul(h, t)); // exact
    /* as Tinv was computed, we have 0 < e < 2^119 */
    e = e.shr(55).mul(t);
    t += e.shr(72).lo;
    /* If we had no truncation, the residual 1-ax is squared at each iteration,
       thus we would get 2^127 - h*t < 2^(2*118.995)/2^127 <= 2^110.99.
       The truncation e>>55 induces an error of at most t < 2^64 on the
       value of e after e = (u128) t * (e >> 55), thus of at most 2^-8 on t,
       while the truncation e >> 72 induces an error of at most 1 on t,
       thus the error on t is at most 1+2^-8.
       Since h < 2^64, this can increase by at most 2^64*(1+2^-8) the value
       of 2^127 - h*t:
       2^127 - h*t < 2^110.99 + 2^64*(1+2^-8) < 2^110.991.
    */

    /* second Newton iteration */
    e = U128.of(0, 1L << 63).sub(U128.mul(h, t)); // 0 <= e < 2^111
    e = e.shr(47).mul(t);
    t += e.shr(80).lo;
    /* With the same reasoning as above, the truncation e >> 47 induces an
       error of at most 2^-16 on t, and the truncation e >> 80 an error of
       at most 1, giving a total of 1+2^-16.
       Since h < 2^64, this can increase by at most 2^64*(1+2^-16) the value
       of 2^127 - h*t:
       2^127 - h*t < 2^(2*110.991)/2^127 + 2^64*(1+2^-16) < 2^94.9821. */


    /* third Newton iteration */
    e = U128.of(0, 1L << 63).sub(U128.mul(h, t)); // 0 <= e < 2^95
    e = e.shr(31).mul(t);
    t += e.shr(96).lo;
    /* With the same reasoning as above, the truncation e >> 31 induces an
       error of at most 2^-32 on t, and the truncation e >> 96 an error of
       at most 1, giving a total of 1+2^-32.
       Since h < 2^64, this can increase by at most 2^64*(1+2^-32) the value
       of 2^127 - h*t:
       2^127 - h*t < 2^(2*94.9821)/2^127 + 2^64*(1+2^-32) < 2^64.574.
       This corresponds to:
       1 - a*x < 2^64.574/2^127 = 2^-62.426, thus the relative error is at
       most 2^-62.426. */

    Dint64 q = new Dint64();
    r.hi = t;
    r.lo = 0;
    /* if a->ex = 0, then 1/2 <= a < 1, thus we should have
       1 < 1/a <= 2, thus r->ex = 1 */
    r.ex = 1 - a.ex;
    r.sgn = 1;
    /* we use Newton's iteration: r -> r + r*(1-a*r) */
    mulDint21(q, a, r);     /* -a*r, error <= 2 ulps */
    r.sgn = 0;                /* restore sign */
    addDint(q, ONE, q);     /* 1-a*r, error <= 2 ulps */
    mulDint(q, r, q);        /* r*(1-a*r), error <= 6 ulps */
    addDint(r, r, q);        /* error <= 2 ulps */
    /* If all computations were exact, the residual would be squared,
       thus we would get 1-a*r = 2^(-2*62.426) = 2^-124.852.
       To simplify the error analysis, we assume 1 <= a < 2 and thus
       1/2 <= r <= 1.
       * since |ar| <= 1, the error of at most 2 ulps from mul_dint_21()
         translates to an absolute error of at most 2^-127; this error
         is multiplied by r <= 1, thus contributes to at most 2^-127 in the
         final value of r.
       * since 1-a*r < 2^-62.426, the error of at most 2 ulps in
         add_dint (&q, &ONE, &q) translates to an absolute error of at most
         2^-189; this error is multiplied by r <= 1, thus contributes to at most
         2^-189 in the final value of r.
       * since q < 2^-62.426 and r < 1, the value of q after mul_dint()
         satisfies q < 2^-62.426, thus the error of at most 6 ulps translates
         into an absolute error of at most 2^-187 in the final value of r.
       * since r <= 1, the error of at most 2 ulps in add_dint (r, r, &q)
         translates into an absolute error of at most 2^-127 in the final
         value of r.
       This yields a maximal absolute error of 2^-127+2^-189+2^-187+2^-127
       < 2^-125.999. Since r >= 1/2, this corresponds to a relative error
       bounded by 2^-124.999, or to less than 4.003 ulps, since in the
       binade [1/2,1), the ulp is 2^-128.
    */
  }

  /* put in r an approximation of b/a, assuming a is not zero,
     with relative error < 2^-123.67 */
  private static void divDint(Dint64 r, Dint64 b, Dint64 a) {
    invDint(r, a);    // relative error bounded by 2^-124.999
    mulDint(r, r, b); // error bounded by 6 ulps
    /* The error bound of 6 ulps for mul_dint() corresponds to a maximal
       error of 6*2^-128 in the binade [1/2,1), thus to a maximal relative
       error of 12*2^-128:
       r = b/a * (1 + eps1) * (1 + eps2)
       with |eps1| < 2^-124.999 and |eps2| < 12*2^-128,
       thus r = b/a * (1 + eps) with eps < (1 + 2^-124.999) * (1 + 12*2^-128) - 1
       < 2^-123.67. */
  }

  // Convert a non-zero double to the corresponding dint64_t value
  private static void dintFromd(Dint64 a, double b) {
    fastExtract(a, b);

    /* |b| = 2^(ex-52)*hi */

    int t = Long.numberOfLeadingZeros(a.hi);

    a.sgn = b < 0.0 ? 1 : 0;
    a.hi = a.hi << t;
    a.ex = a.ex - (t > 11 ? t - 12 : 0);
    /* b = 2^ex*hi/2^64 where 1/2 <= hi/2^64 < 1 */
    a.lo = 0;
  }

  private static void subnormalizeDint(Dint64 a) {
    if (a.ex > -1023)
      return;

    long ex = -(1011 + a.ex);

    long hi = a.hi >>> ex;
    long md = (a.hi >>> (ex - 1))&0x1;
    long lo = ((a.hi&(~0L >>> ex)) != 0 || a.lo != 0) ? 1 : 0;

    /* The C code switches on fegetround() and handles FE_TONEAREST,
       FE_DOWNWARD and FE_UPWARD. The JVM has no floating-point environment and
       round-to-nearest-even is the only mode, so only the FE_TONEAREST case is
       translated. The FE_DOWNWARD and FE_UPWARD cases are dropped because they
       belong to the directed rounding modes, which AGENTS.md puts out of scope;
       keeping them would mean carrying a rounding mode that no caller can
       select. */
    hi += lo != 0 ? md : hi&md;

    a.hi = hi << ex;
    a.lo = 0;

    if (a.hi == 0) {
      a.ex++;
      a.hi = (1L << 63);
    }
  }

  // Convert a dint64_t value to a double
  private static double dintTod(Dint64 a) {
    subnormalizeDint(a);

    long ru = (a.hi >>> 11) | (0x3ffL << 52);

    double rd = 0.0;
    if (((a.hi >>> 10)&0x1) != 0)
      rd += 0x1p-53;

    if ((a.hi&0x3ff) != 0 || a.lo != 0)
      rd += 0x1p-54;

    if (a.sgn != 0)
      rd = -rd;

    ru = ru | a.sgn << 63;
    double rf = Double.longBitsToDouble(ru) + rd;

    double ef;

    if (a.ex > -1022) { // The result is a normal double
      if (a.ex > 1024)
        if (a.ex == 1025) {
          rf = rf * 0x1p+1;
          ef = 0x1p+1023;
        } else {
          rf = 0x1.fffffffffffffp+1023;
          ef = 0x1.fffffffffffffp+1023;
        }
      else
        ef = Double.longBitsToDouble(((a.ex + 1022)&0x7ff) << 52);
    } else {
      if (a.ex < -1073) {
        if (a.ex == -1074) {
          rf = rf * 0x1p-1;
          ef = 0x1p-1074;
        } else {
          rf = 0x0.0000000000001p-1022;
          ef = 0x0.0000000000001p-1022;
        }
      } else
        ef = Double.longBitsToDouble(1L << (a.ex + 1073));
    }

    return rf * ef;
  }

  /* This table approximates 1/(2pi) downwards with precision 1280:
     1/(2*pi) ~ T[0]/2^64 + T[1]/2^128 + ... + T[i]/2^((i+1)*64) + ...
     Computed with computeT() from sin.sage. */
  private static final long[] T = {
    0x28be60db9391054aL, // i=0
     0x7f09d5f47d4d3770L,
     0x36d8a5664f10e410L,
     0x7f9458eaf7aef158L,
     0x6dc91b8e909374b8L,
     0x1924bba82746487L, // i=5
     0x3f877ac72c4a69cfL,
     0xba208d7d4baed121L,
     0x3a671c09ad17df90L,
     0x4e64758e60d4ce7dL,
     0x272117e2ef7e4a0eL, // i=10
     0xc7fe25fff7816603L,
     0xfbcbc462d6829b47L,
     0xdb4d9fb3c9f2c26dL,
     0xd3d18fd9a797fa8bL,
     0x5d49eeb1faf97c5eL, // i=15
     0xcf41ce7de294a4baL,
     0x9afed7ec47e35742L,
     0x1580cc11bf1edaeaL,
     0xfc33ef0826bd0d87L, // i=19
  };

  /* Table containing 128-bit approximations of sin2pi(i/2^11) for 0 <= i < 256
     (to nearest).
     Each entry is to be interpreted as (hi/2^64+lo/2^128)*2^ex*(-1)*sgn.
     Generated with computeS() from sin.sage. */
  private static final Dint64[] S = {
    new Dint64(0x0L, 0x0L, 128, 0),
    new Dint64(0xc90fc5f66525d257L, 0x480f7956b6470765L, -8, 0),
    new Dint64(0xc90f87f3380388d5L, 0xcb3ff35bd4d81baaL, -7, 0),
    new Dint64(0x96cb587284b81770L, 0xb767005691b9d9d1L, -6, 0),
    new Dint64(0xc90e8fe6f63c2330L, 0xf1d7d06db39ea9fcL, -6, 0),
    new Dint64(0xfb514b55ccbe541aL, 0xd784e031f9af76d6L, -6, 0),
    new Dint64(0x96c9b5df1877e9b5L, 0xf91ee371d6467dcaL, -5, 0),
    new Dint64(0xafea690fd5912ef3L, 0xf56e3c87ae3c56dfL, -5, 0),
    new Dint64(0xc90aafbd1b33efc9L, 0xc539edcbfda0cf2cL, -5, 0),
    new Dint64(0xe22a7a6729d8e453L, 0x850021e392744a4fL, -5, 0),
    new Dint64(0xfb49b98e8e7807f6L, 0xb21ccebc9caac3L, -5, 0),
    new Dint64(0x8a342eda160bf5aeL, 0xde5b1068d174be9cL, -4, 0),
    new Dint64(0x96c32baca2ae68b4L, 0x37b2dd49d5fca3c0L, -4, 0),
    new Dint64(0xa351cb7fc30bc889L, 0xb56007d16d4ad5a3L, -4, 0),
    new Dint64(0xafe00694866a1b44L, 0xcd34d2751c2e1da7L, -4, 0),
    new Dint64(0xbc6dd52c3a342eb5L, 0xf10bfca3d6464012L, -4, 0),
    new Dint64(0xc8fb2f886ec09f37L, 0x6a17954b2b7c5171L, -4, 0),
    new Dint64(0xd5880deafc18b534L, 0x73d1472472f4a390L, -4, 0),
    new Dint64(0xe214689606bf1676L, 0x438b4a73aecd2541L, -4, 0),
    new Dint64(0xeea037cc04764844L, 0xc4e92d01a2f42935L, -4, 0),
    new Dint64(0xfb2b73cfc106ff68L, 0xf0a0e36a000c7350L, -4, 0),
    new Dint64(0x83db0a7231831d8fL, 0x60e782313f6161afL, -3, 0),
    new Dint64(0x8a2009a6b84d9402L, 0x77724a2b2a669bc4L, -3, 0),
    new Dint64(0x9064b3a76a22640cL, 0x56e0a8b0d177b55dL, -3, 0),
    new Dint64(0x96a9049670cfae65L, 0xf77574094d3c35c4L, -3, 0),
    new Dint64(0x9cecf8962d14c822L, 0x50ffe4f5caa7f1faL, -3, 0),
    new Dint64(0xa3308bc93904ad69L, 0xdec1b7f2768bdafaL, -3, 0),
    new Dint64(0xa973ba526a6850d9L, 0x76f8c63986598c79L, -3, 0),
    new Dint64(0xafb68054d520c60bL, 0xfdd2fc0936594c2dL, -3, 0),
    new Dint64(0xb5f8d9f3cd8945d6L, 0x924bef13600f9852L, -3, 0),
    new Dint64(0xbc3ac352ead90abeL, 0xeb13e106732687f1L, -3, 0),
    new Dint64(0xc27c389609850433L, 0xb228a03916371f6fL, -3, 0),
    new Dint64(0xc8bd35e14da15f0eL, 0xc7396c894bbf7389L, -3, 0),
    new Dint64(0xcefdb7592542e1e9L, 0x6b47b8c44e5b037eL, -3, 0),
    new Dint64(0xd53db9224ae01bcaL, 0x7337412cf70716cbL, -3, 0),
    new Dint64(0xdb7d3761c7b263b6L, 0xbb286d23e11c8337L, -3, 0),
    new Dint64(0xe1bc2e3cf616a7acL, 0x31883b30137c6e62L, -3, 0),
    new Dint64(0xe7fa99d983ee098fL, 0xeeb8f9c33340a2f2L, -3, 0),
    new Dint64(0xee38765d74fe4897L, 0xed16b994af6c18aeL, -3, 0),
    new Dint64(0xf475bfef2551f5b9L, 0x14e1a5488eaeab96L, -3, 0),
    new Dint64(0xfab272b54b9871a2L, 0x704729ae56d78a37L, -3, 0),
    new Dint64(0x8077456b7dc2d967L, 0x3eac8308f1113e5eL, -2, 0),
    new Dint64(0x8395023dd418e919L, 0xdb1f70118c9c2198L, -2, 0),
    new Dint64(0x86b26de5933c2e8eL, 0xc5a9decdfaad4db5L, -2, 0),
    new Dint64(0x89cf8676d7abb55bL, 0x97965c9860c34e44L, -2, 0),
    new Dint64(0x8cec4a05f12739e8L, 0xdcdca90cc73b116aL, -2, 0),
    new Dint64(0x9008b6a763de75b7L, 0xa6e3df5975cca9daL, -2, 0),
    new Dint64(0x9324ca6fe9a04b4eL, 0x899c4de737feec22L, -2, 0),
    new Dint64(0x964083747309d113L, 0xa89a11e07c1feL, -2, 0),
    new Dint64(0x995bdfca28b53a54L, 0x49c4863de522b217L, -2, 0),
    new Dint64(0x9c76dd866c689dccL, 0xe7bc08111d0bfca4L, -2, 0),
    new Dint64(0x9f917abeda4498dfL, 0xf3ff913a4aadb85eL, -2, 0),
    new Dint64(0xa2abb58949f2ced7L, 0xa5dbee6084ee1260L, -2, 0),
    new Dint64(0xa5c58bfbcfd4436aL, 0x69fcb11e19f58619L, -2, 0),
    new Dint64(0xa8defc2cbe2f8fccL, 0xcd12a1f6ab6b095L, -2, 0),
    new Dint64(0xabf80432a65ef190L, 0x8c95c4c91179176bL, -2, 0),
    new Dint64(0xaf10a22459fe32a6L, 0x3feef3bb58b1f10dL, -2, 0),
    new Dint64(0xb228d418ec1869adL, 0x16031a34d4fc855dL, -2, 0),
    new Dint64(0xb5409827b25591f0L, 0xcd73fb5d8d45d302L, -2, 0),
    new Dint64(0xb857ec684627fa4cL, 0x187e26d290714d70L, -2, 0),
    new Dint64(0xbb6ecef285f98a3aL, 0xbddd8a0365d6b1d3L, -2, 0),
    new Dint64(0xbe853dde9658dc60L, 0xdfe1b074e22fc666L, -2, 0),
    new Dint64(0xc19b3744e3262dcdL, 0xad5a41de48f6b26fL, -2, 0),
    new Dint64(0xc4b0b93e20c0213fL, 0xdab4e426409b23a0L, -2, 0),
    new Dint64(0xc7c5c1e34d3055b2L, 0x5cc8c00e4fccd850L, -2, 0),
    new Dint64(0xcada4f4db157cf77L, 0xfa6171200ab2efc3L, -2, 0),
    new Dint64(0xcdee5f96e21b332cL, 0x65a3132adfb7dfd5L, -2, 0),
    new Dint64(0xd101f0d8c18ed1c1L, 0xaadb580a1eba209fL, -2, 0),
    new Dint64(0xd415012d802284f0L, 0xdf4005ef6a64aa02L, -2, 0),
    new Dint64(0xd7278eaf9dcd5b55L, 0x1779df36d1cc8912L, -2, 0),
    new Dint64(0xda399779eb391377L, 0xcbabaeb97af8e8aaL, -2, 0),
    new Dint64(0xdd4b19a78aed6515L, 0xece7f445cecf1e28L, -2, 0),
    new Dint64(0xe05c1353f27b17e5L, 0xebc61ade6ca83cdL, -2, 0),
    new Dint64(0xe36c829aeba6e720L, 0x26a0eecdb4f16266L, -2, 0),
    new Dint64(0xe67c659895943123L, 0x82b0aecadf808123L, -2, 0),
    new Dint64(0xe98bba6965ef725fL, 0xb91caf23416e7e80L, -2, 0),
    new Dint64(0xec9a7f2a2a188aebL, 0x7244ee20f591983bL, -2, 0),
    new Dint64(0xefa8b1f8084ccdfcL, 0x1050cdf22f34182fL, -2, 0),
    new Dint64(0xf2b650f080d0da8dL, 0x587f3fa044e2d27dL, -2, 0),
    new Dint64(0xf5c35a316f1a3c80L, 0x643720de93ba81bdL, -2, 0),
    new Dint64(0xf8cfcbd90af8d57aL, 0x4221dc4ba772598dL, -2, 0),
    new Dint64(0xfbdba405e9c00ccaL, 0xd24d3023da491920L, -2, 0),
    new Dint64(0xfee6e0d6ff6fc5a4L, 0x8b74fe2508ab8fc2L, -2, 0),
    new Dint64(0x80f8c035cfee8d76L, 0xfd958d68e8b49e6bL, -1, 0),
    new Dint64(0x827dc071bfed6ffaL, 0xfb4c92369f0cf008L, -1, 0),
    new Dint64(0x8402702f5b30f2a9L, 0xcb07b25a7b0372a7L, -1, 0),
    new Dint64(0x8586ce7ededc809dL, 0x9d3dc689006896f4L, -1, 0),
    new Dint64(0x870ada70ba4e6d49L, 0x9d52755ece3f70L, -1, 0),
    new Dint64(0x888e93158fb3bb04L, 0x984156f553344306L, -1, 0),
    new Dint64(0x8a11f77e349bc245L, 0xa66d1d936c38c329L, -1, 0),
    new Dint64(0x8b9506bbb28bb922L, 0x575f33366be0afefL, -1, 0),
    new Dint64(0x8d17bfdf47921ac8L, 0xcb590d74f64e77c9L, -1, 0),
    new Dint64(0x8e9a21fa66d9ee8dL, 0xf2be3ecae62789d4L, -1, 0),
    new Dint64(0x901c2c1eb93dee39L, 0x632b9cff5cfee724L, -1, 0),
    new Dint64(0x919ddd5e1ddb8b33L, 0x609c464b3dd676ecL, -1, 0),
    new Dint64(0x931f34caaaa5d23aL, 0x6a1ff8bfe6396e28L, -1, 0),
    new Dint64(0x94a03176acf82d45L, 0xae4ba773da6bf754L, -1, 0),
    new Dint64(0x9620d274aa290339L, 0xe06a955a5b8e301dL, -1, 0),
    new Dint64(0x97a116d7601c3515L, 0xfc8b7184b21f2d50L, -1, 0),
    new Dint64(0x9920fdb1c5d5783dL, 0x9dd1eedf18a2e4dfL, -1, 0),
    new Dint64(0x9aa086170c0a8d86L, 0x9ffa0d23f3c26c62L, -1, 0),
    new Dint64(0x9c1faf1a9db554afL, 0xdab6b478577e7be5L, -1, 0),
    new Dint64(0x9d9e77d020a5bbe6L, 0xdb895384528d0d60L, -1, 0),
    new Dint64(0x9f1cdf4b76138b02L, 0x98dbd3555ebcdefeL, -1, 0),
    new Dint64(0xa09ae4a0bb300a19L, 0x2f895f44a303cc0bL, -1, 0),
    new Dint64(0xa21886e449b78316L, 0xd29d23a624acd00cL, -1, 0),
    new Dint64(0xa395c52ab8829dfcL, 0x2be036401ba87cc2L, -1, 0),
    new Dint64(0xa5129e88dc17976aL, 0x82d9495ead5be348L, -1, 0),
    new Dint64(0xa68f1213c73b5124L, 0x17218792857f4c5aL, -1, 0),
    new Dint64(0xa80b1ee0cb823c27L, 0x3269f4702b88324aL, -1, 0),
    new Dint64(0xa986c40579e11c0aL, 0x8e3bdf8085321556L, -1, 0),
    new Dint64(0xab020097a33da341L, 0xc1654b64a0081b46L, -1, 0),
    new Dint64(0xac7cd3ad58fee7f0L, 0x811f953984eff83eL, -1, 0),
    new Dint64(0xadf73c5ced9db0f3L, 0x9a5318ac6fe94e4dL, -1, 0),
    new Dint64(0xaf7139bcf5349ac6L, 0x9fe5f4ea48965e2cL, -1, 0),
    new Dint64(0xb0eacae4461013edL, 0x63c66682bae74898L, -1, 0),
    new Dint64(0xb263eee9f93e3088L, 0x695a5332090bb09bL, -1, 0),
    new Dint64(0xb3dca4e56b1e54bbL, 0x992d96e5021e3c37L, -1, 0),
    new Dint64(0xb554ebee3bf0b58eL, 0x971f4da709ad4378L, -1, 0),
    new Dint64(0xb6ccc31c5065afeeL, 0x35ebacd79f209137L, -1, 0),
    new Dint64(0xb8442987d22cf576L, 0x9cc3ef36746de3b8L, -1, 0),
    new Dint64(0xb9bb1e4930848eadL, 0xcdb0531c4e58484bL, -1, 0),
    new Dint64(0xbb31a07920c7b256L, 0x55b92083658bb897L, -1, 0),
    new Dint64(0xbca7af309efd7182L, 0xa4b0d21fc5036a5L, -1, 0),
    new Dint64(0xbe1d4988ee67380cL, 0xd1f90f79f46c7e01L, -1, 0),
    new Dint64(0xbf926e9b9a0f2127L, 0x91a1b5eb79658c67L, -1, 0),
    new Dint64(0xc1071d8275561f9bL, 0x721853f8e528a934L, -1, 0),
    new Dint64(0xc27b55579c81f96dL, 0xcdc2bd470675104dL, -1, 0),
    new Dint64(0xc3ef1535754b168dL, 0x3122c2a59efddc37L, -1, 0),
    new Dint64(0xc5625c36af6a222fL, 0xf4ff2895ab6ebe89L, -1, 0),
    new Dint64(0xc6d5297645257e8dL, 0x14d24739de27e2e9L, -1, 0),
    new Dint64(0xc8477c0f7bde8a98L, 0x4ce0246ad4fa74L, -1, 0),
    new Dint64(0xc9b9531de49eb968L, 0x4319e5ad5b0dcb84L, -1, 0),
    new Dint64(0xcb2aadbd5ca47af5L, 0xfaa3dfe675a65ee2L, -1, 0),
    new Dint64(0xcc9b8b0a0deff5d4L, 0x2e663b3c7555a6c3L, -1, 0),
    new Dint64(0xce0bea206fcf9192L, 0x3c540a9eec47af38L, -1, 0),
    new Dint64(0xcf7bca1d476c516dL, 0xa81290bdbaad62e4L, -1, 0),
    new Dint64(0xd0eb2a1da855fefdL, 0xb9302788604e88f1L, -1, 0),
    new Dint64(0xd25a093ef50f2482L, 0x721fc87ba1d42456L, -1, 0),
    new Dint64(0xd3c8669edf98d680L, 0x87967926fdcecec4L, -1, 0),
    new Dint64(0xd536415b69fe4c54L, 0x1df22346611c6b4bL, -1, 0),
    new Dint64(0xd6a39892e6e04764L, 0x3090d44db12c418cL, -1, 0),
    new Dint64(0xd8106b63fa0048a0L, 0xa573f2aa90434ba5L, -1, 0),
    new Dint64(0xd97cb8ed98cb93f5L, 0x2e349483e3fb2a6aL, -1, 0),
    new Dint64(0xdae8804f0ae6015bL, 0x362cb974182e3030L, -1, 0),
    new Dint64(0xdc53c0a7eab49b35L, 0x3ccca3982328ed8bL, -1, 0),
    new Dint64(0xddbe791825e8099eL, 0x1a5bd9269d408d7eL, -1, 0),
    new Dint64(0xdf28a8bffe06ca56L, 0xcce2634be2bf54dfL, -1, 0),
    new Dint64(0xe0924ec008f734fdL, 0x8aa895d5bf3e84eaL, -1, 0),
    new Dint64(0xe1fb6a3931894b38L, 0xf7a1f9bd9ba13b6bL, -1, 0),
    new Dint64(0xe363fa4cb8005482L, 0x7b32c72e31824e51L, -1, 0),
    new Dint64(0xe4cbfe1c329c453aL, 0xd40e9e6b989f89e5L, -1, 0),
    new Dint64(0xe63374c98e22f0b4L, 0x2872ce1bfc7ad1cdL, -1, 0),
    new Dint64(0xe79a5d770e6905dcL, 0xf1b65cc5fd780262L, -1, 0),
    new Dint64(0xe900b7474edad637L, 0x431626c10485bddaL, -1, 0),
    new Dint64(0xea66815d4304e6c8L, 0xcc39cfcc29960b1L, -1, 0),
    new Dint64(0xebcbbadc371c4aaaL, 0x1d90f780ae951140L, -1, 0),
    new Dint64(0xed3062e7d086c6f0L, 0xc71debc372b6f9d4L, -1, 0),
    new Dint64(0xee9478a40e62bf86L, 0x2a24164daec85ccbL, -1, 0),
    new Dint64(0xeff7fb354a0eecb1L, 0x527233b40d3432bbL, -1, 0),
    new Dint64(0xf15ae9c037b1d8f0L, 0x6c48e9e3420b0f1eL, -1, 0),
    new Dint64(0xf2bd4369e6c126d3L, 0x7f232aee178c6323L, -1, 0),
    new Dint64(0xf41f0757c2889e84L, 0x3c7f10db458c337cL, -1, 0),
    new Dint64(0xf58034af92b102a7L, 0x93fa6107c4327527L, -1, 0),
    new Dint64(0xf6e0ca977bc6ac45L, 0xe1079824233fef46L, -1, 0),
    new Dint64(0xf840c835ffbfed66L, 0xa9a56012067c570cL, -1, 0),
    new Dint64(0xf9a02cb1fe833a0dL, 0x8da894471de1a18L, -1, 0),
    new Dint64(0xfafef732b66d1742L, 0x343fbf4a7d42af3L, -1, 0),
    new Dint64(0xfc5d26dfc4d5cfdaL, 0x27c07c911290b8d1L, -1, 0),
    new Dint64(0xfdbabae12696eea4L, 0x2377c3799c052faL, -1, 0),
    new Dint64(0xff17b25f38907dadL, 0xa9c6ba50490539fL, -1, 0),
    new Dint64(0x803a06415c170525L, 0x6f53873e2f1477ffL, 0, 0),
    new Dint64(0x80e7e43a61f5b6cbL, 0x5ca183dc973abc22L, 0, 0),
    new Dint64(0x819572af6decac84L, 0x9fba97fdf0c4d24cL, 0, 0),
    new Dint64(0x8242b1357110d372L, 0x6fb2123fedfa6e22L, 0, 0),
    new Dint64(0x82ef9f618dc5b70eL, 0x91a965931f1a200aL, 0, 0),
    new Dint64(0x839c3cc917ff6cb4L, 0xbfd79717f2880abfL, 0, 0),
    new Dint64(0x8448890195846099L, 0x246efcff30cb064aL, 0, 0),
    new Dint64(0x84f483a0be2f0403L, 0x51917cac857fd5f5L, 0, 0),
    new Dint64(0x85a02c3c7c2f5ca5L, 0x327888fe4b62687bL, 0, 0),
    new Dint64(0x864b826aec4c74e5L, 0x85043222c9bdd18dL, 0, 0),
    new Dint64(0x86f685c25e25acf5L, 0x7e0b9b07548471a2L, 0, 0),
    new Dint64(0x87a135d95473ec89L, 0x4e091160e2430712L, 0, 0),
    new Dint64(0x884b9246854ab50bL, 0x4f14c8afe4560291L, 0, 0),
    new Dint64(0x88f59aa0da591421L, 0xb892ca8361d8c84cL, 0, 0),
    new Dint64(0x899f4e7f712a765eL, 0xc88302a31afce54aL, 0, 0),
    new Dint64(0x8a48ad799b6759f3L, 0x660558a02136130aL, 0, 0),
    new Dint64(0x8af1b726df15e13cL, 0x545f7d79ead8fa19L, 0, 0),
    new Dint64(0x8b9a6b1ef6da4502L, 0x21a6675f51580bc4L, 0, 0),
    new Dint64(0x8c42c8f9d2372644L, 0x101a5adbcb9ffb43L, 0, 0),
    new Dint64(0x8cead04f95cdbf66L, 0x4d49cbaf15aecd80L, 0, 0),
    new Dint64(0x8d9280b89b9df49bL, 0xde2d43c6b67a7cbeL, 0, 0),
    new Dint64(0x8e39d9cd73464364L, 0xbba4cfecbff54867L, 0, 0),
    new Dint64(0x8ee0db26e24390f8L, 0xaf0e2345f3bd24b4L, 0, 0),
    new Dint64(0x8f87845de430d777L, 0x9311a82459aa0f72L, 0, 0),
    new Dint64(0x902dd50bab06b1b7L, 0xb144016c7a30b39aL, 0, 0),
    new Dint64(0x90d3ccc99f5ac58bL, 0x9d1072e09b72292L, 0, 0),
    new Dint64(0x91796b31609f0c54L, 0x6714fe6925b78cc4L, 0, 0),
    new Dint64(0x921eafdcc560f9c5L, 0x33d0a284a8c954adL, 0, 0),
    new Dint64(0x92c39a65db88809dL, 0x1f8481e704e4a767L, 0, 0),
    new Dint64(0x93682a66e896f544L, 0xb17821911e71c16eL, 0, 0),
    new Dint64(0x940c5f7a69e5ce1cL, 0x1489a97671a42L, 0, 0),
    new Dint64(0x94b0393b14e54156L, 0xd6c7af02d5c16fd9L, 0, 0),
    new Dint64(0x9553b743d75ac03fL, 0xac0106650f4ef023L, 0, 0),
    new Dint64(0x95f6d92fd79f4fbaL, 0xd9f8e1a446e973b9L, 0, 0),
    new Dint64(0x96999e9a74ddbde3L, 0xa7a7556c3b33abc1L, 0, 0),
    new Dint64(0x973c071f4750b49cL, 0xc0a03934f0cce19bL, 0, 0),
    new Dint64(0x97de125a2080a8edL, 0xd243aa0843a2c144L, 0, 0),
    new Dint64(0x987fbfe70b81a708L, 0x19cec845ac87a5c6L, 0, 0),
    new Dint64(0x99210f624d30facbL, 0xc4b992a37fb9b9bdL, 0, 0),
    new Dint64(0x99c200686472b4a8L, 0x1ab42d43235757b6L, 0, 0),
    new Dint64(0x9a6292960a6f0ab0L, 0x7e92c655656e6b85L, 0, 0),
    new Dint64(0x9b02c58832cf95c0L, 0x698b94f50326a043L, 0, 0),
    new Dint64(0x9ba298dc0bfc6a88L, 0x9a5614e8ffbeac6fL, 0, 0),
    new Dint64(0x9c420c2eff590e5fL, 0xc7fd954194e6d8aaL, 0, 0),
    new Dint64(0x9ce11f1eb18147b1L, 0x3e93627de8fd5779L, 0, 0),
    new Dint64(0x9d7fd1490285c9e3L, 0xe25e39549638ae68L, 0, 0),
    new Dint64(0x9e1e224c0e28bc94L, 0x2cad377d5c9c35d8L, 0, 0),
    new Dint64(0x9ebc11c62c1a1dfbL, 0xcc141e10c6460c8bL, 0, 0),
    new Dint64(0x9f599f55f0340061L, 0xa88d5f46834bbf8dL, 0, 0),
    new Dint64(0x9ff6ca9a2ab6a26dL, 0x22cc118a0c118aa0L, 0, 0),
    new Dint64(0xa0939331e8846237L, 0x7cec6df5bea167cfL, 0, 0),
    new Dint64(0xa12ff8bc735d8af6L, 0x71acea2819360c35L, 0, 0),
    new Dint64(0xa1cbfad9521bfd1bL, 0x166c36e7bb3c402fL, 0, 0),
    new Dint64(0xa267992848eeb0c0L, 0x3b5167ee359a234eL, 0, 0),
    new Dint64(0xa302d34959951243L, 0x9443372e20d4377cL, 0, 0),
    new Dint64(0xa39da8dcc39a38e5L, 0xca9a8a720d4c69cL, 0, 0),
    new Dint64(0xa4381983048ff747L, 0xbf623cf5301a2ddeL, 0, 0),
    new Dint64(0xa4d224dcd849c5b0L, 0x23d251cc8d7975ccL, 0, 0),
    new Dint64(0xa56bca8b391785dbL, 0x189d39ffe11aaa2bL, 0, 0),
    new Dint64(0xa6050a2f60002049L, 0x8c33ebf3aa8501fbL, 0, 0),
    new Dint64(0xa69de36ac4fbfadcL, 0x9b3ad6e4022183d9L, 0, 0),
    new Dint64(0xa73655df1f2f489eL, 0x149f6e75993468a3L, 0, 0),
    new Dint64(0xa7ce612e65243291L, 0x6b2a39f856a69781L, 0, 0),
    new Dint64(0xa86604facd04d969L, 0x3463a2c2e6e9cc55L, 0, 0),
    new Dint64(0xa8fd40e6ccd52ffdL, 0x6cc14c4f53e2e82dL, 0, 0),
    new Dint64(0xa99414951aacae5eL, 0xd147625fda929af8L, 0, 0),
    new Dint64(0xaa2a7fa8acefdd63L, 0xb714ee81b53b4b9dL, 0, 0),
    new Dint64(0xaac081c4ba89ba8aL, 0xe1b3dfc4dbda9bfdL, 0, 0),
    new Dint64(0xab561a8cbb24f410L, 0xf17cee69b0d2ecdeL, 0, 0),
    new Dint64(0xabeb49a46764fd15L, 0x1becda8089c1a94cL, 0, 0),
    new Dint64(0xac800eafb91ef9a9L, 0xf86ba0dde982fb59L, 0, 0),
    new Dint64(0xad146952eb9282afL, 0x44bf16268608db96L, 0, 0),
    new Dint64(0xada859327ba24151L, 0x9d30d4cfeb04f1fbL, 0, 0),
    new Dint64(0xae3bddf3280c620dL, 0x3d53817865422565L, 0, 0),
    new Dint64(0xaecef739f1a2df10L, 0xf74d099042e8f326L, 0, 0),
    new Dint64(0xaf61a4ac1b83a1deL, 0xa89a9b8f726b95bfL, 0, 0),
    new Dint64(0xaff3e5ef2b507c06L, 0x8c679e67fc462d51L, 0, 0),
    new Dint64(0xb085baa8e966f6daL, 0xe4cad00d5c94bcd2L, 0, 0),
    new Dint64(0xb117227f6117f9f9L, 0x8d8be132d576e614L, 0, 0),
    new Dint64(0xb1a81d18e0df4889L, 0x24784f32c3e3e5bdL, 0, 0),
    new Dint64(0xb238aa1bfa9ad507L, 0x8cc7d4bd05ffd5aeL, 0, 0),
    new Dint64(0xb2c8c92f83c1eb87L, 0xac9f7ebbc469ef59L, 0, 0),
    new Dint64(0xb35879fa959c323cL, 0x5d6635109164f740L, 0, 0),
    new Dint64(0xb3e7bc248d78802eL, 0xa156468ef6c18c60L, 0, 0),
    new Dint64(0xb4768f550ce389fdL, 0x4a85350f69018c55L, 0, 0),
  };

  /* Table containing 128-bit approximations of cos2pi(i/2^11) for 0 <= i < 256
     (to nearest).
     Each entry is to be interpreted as (hi/2^64+lo/2^128)*2^ex*(-1)*sgn.
     Generated with computeC() from sin.sage. */
  private static final Dint64[] C = {
    new Dint64(0x8000000000000000L, 0x0L, 1, 0),
    new Dint64(0xffffb10b10e80e95L, 0x3031437d7eccb9dfL, 0, 0),
    new Dint64(0xfffec42c7454926bL, 0x38e310779edfec68L, 0, 0),
    new Dint64(0xfffd3964bc6275baL, 0x69fff9ae0dedb047L, 0, 0),
    new Dint64(0xfffb10b4dc96dabbL, 0xb47903f7a19f8ee2L, 0, 0),
    new Dint64(0xfff84a1e29de8571L, 0x8cc193c5d508e13fL, 0, 0),
    new Dint64(0xfff4e5a25a8d095bL, 0x43366df666fd54ffL, 0, 0),
    new Dint64(0xfff0e343865bbb13L, 0x5428ed0647c9e5d1L, 0, 0),
    new Dint64(0xffec4304266865d9L, 0x5657552366961732L, 0, 0),
    new Dint64(0xffe704e71533c508L, 0x53aa9423bb0adc21L, 0, 0),
    new Dint64(0xffe128ef8e9fc17aL, 0x7d209f32d42d864eL, 0, 0),
    new Dint64(0xffdaaf212fed72dbL, 0x4fd8f038449ec436L, 0, 0),
    new Dint64(0xffd3977ff7bae4e9L, 0x664649b4d541b9c5L, 0, 0),
    new Dint64(0xffcbe2104600a0a9L, 0x5595ca3f421ae09cL, 0, 0),
    new Dint64(0xffc38ed6dc0ef98bL, 0x1c676208aa3be545L, 0, 0),
    new Dint64(0xffba9dd8dc8b1e83L, 0xccfed60a91097c48L, 0, 0),
    new Dint64(0xffb10f1bcb6bef1dL, 0x421e8edaaf59453eL, 0, 0),
    new Dint64(0xffa6e2a58df6947dL, 0xd2c665c2da3e7844L, 0, 0),
    new Dint64(0xff9c187c6abade6aL, 0x1e1862cca089938bL, 0, 0),
    new Dint64(0xff90b0a7098f6443L, 0x2dabd3195a05710fL, 0, 0),
    new Dint64(0xff84ab2c738d6a03L, 0x519c314973ccae6bL, 0, 0),
    new Dint64(0xff780814130c893cL, 0x3ea4f30adda3016fL, 0, 0),
    new Dint64(0xff6ac765b39e1e19L, 0x1b9d5851979f28fbL, 0, 0),
    new Dint64(0xff5ce92982087867L, 0x50a7bb6a6ee3b0f1L, 0, 0),
    new Dint64(0xff4e6d680c41d0a9L, 0xf668633f1ab858aL, 0, 0),
    new Dint64(0xff3f542a416b0134L, 0xb085c1828f69296aL, 0, 0),
    new Dint64(0xff2f9d7971ca0364L, 0x27e31939e2eec09cL, 0, 0),
    new Dint64(0xff1f495f4ec430d7L, 0xf5971326a3540ea9L, 0, 0),
    new Dint64(0xff0e57e5ead848d1L, 0x1f1901544271c3f8L, 0, 0),
    new Dint64(0xfefcc917b99839a5L, 0xe0abd3a9b64df725L, 0, 0),
    new Dint64(0xfeea9cff8fa2ae54L, 0xec34413e87ef2740L, 0, 0),
    new Dint64(0xfed7d3a8a29c603bL, 0x2f88b949a72ff96cL, 0, 0),
    new Dint64(0xfec46d1e89292cf0L, 0x41390efdc726e9efL, 0, 0),
    new Dint64(0xfeb0696d3ae4f04dL, 0xb7b6cc53c3abc817L, 0, 0),
    new Dint64(0xfe9bc8a1105c22a5L, 0xd3af6ee4f2101c20L, 0, 0),
    new Dint64(0xfe868ac6c3043b2eL, 0xb4f70c910505e10L, 0, 0),
    new Dint64(0xfe70afeb6d33d6a2L, 0x2907cf2b3f6feac2L, 0, 0),
    new Dint64(0xfe5a381c8a1aa224L, 0xd54faa364b7da8f6L, 0, 0),
    new Dint64(0xfe432367f5b90a62L, 0x87b8875373a818a4L, 0, 0),
    new Dint64(0xfe2b71dbecd7aefcL, 0x8598c2c429caf7L, 0, 0),
    new Dint64(0xfe1323870cfe9a3dL, 0x90cd1d959db674efL, 0, 0),
    new Dint64(0xfdfa3878546c3d28L, 0x9bfe5c51e91cbdcdL, 0, 0),
    new Dint64(0xfde0b0bf220c2fd4L, 0xe276d247626a23fdL, 0, 0),
    new Dint64(0xfdc68c6b356db62fL, 0x499ddb331d19539dL, 0, 0),
    new Dint64(0xfdabcb8caeba091bL, 0xfac7397cc07a6470L, 0, 0),
    new Dint64(0xfd906e340eaa6401L, 0xd6e270740a186977L, 0, 0),
    new Dint64(0xfd747472367dd6c5L, 0x61beb8cd2696fc78L, 0, 0),
    new Dint64(0xfd57de5867eedc39L, 0x6c696582f346fd91L, 0, 0),
    new Dint64(0xfd3aabf84528b50bL, 0xeae6bd951c1dabbeL, 0, 0),
    new Dint64(0xfd1cdd63d0bc8735L, 0x863b87258f11ad7eL, 0, 0),
    new Dint64(0xfcfe72ad6d9641f2L, 0xa06fab9f9d106709L, 0, 0),
    new Dint64(0xfcdf6be7def1464cL, 0xa4e064308f4999f4L, 0, 0),
    new Dint64(0xfcbfc926484cd43aL, 0xa3e22b4d38917e73L, 0, 0),
    new Dint64(0xfc9f8a7c2d603c60L, 0x5d582cac7cb4391cL, 0, 0),
    new Dint64(0xfc7eaffd720ed673L, 0x2880268f2e62955L, 0, 0),
    new Dint64(0xfc5d39be5a5bbc4bL, 0x1c0d254b6c8da4bdL, 0, 0),
    new Dint64(0xfc3b27d38a5d49abL, 0x256778ffcb5c1769L, 0, 0),
    new Dint64(0xfc187a52063060c2L, 0x9433b49289417ea2L, 0, 0),
    new Dint64(0xfbf5314f31eb7375L, 0x25aafd7fdba12c5fL, 0, 0),
    new Dint64(0xfbd14ce0d191516eL, 0x7190c94899dff1b8L, 0, 0),
    new Dint64(0xfbaccd1d0903bb09L, 0xe63ae8632b84473cL, 0, 0),
    new Dint64(0xfb87b21a5bf5b917L, 0x75df66f0ec3dd459L, 0, 0),
    new Dint64(0xfb61fbefadddb985L, 0x61ce9d5ef5a81487L, 0, 0),
    new Dint64(0xfb3baab441e770f7L, 0xb4b54683879c9c17L, 0, 0),
    new Dint64(0xfb14be7fbae58156L, 0x2172a361fd2a722fL, 0, 0),
    new Dint64(0xfaed376a1b42e559L, 0x2079880c450348acL, 0, 0),
    new Dint64(0xfac5158bc4f4211fL, 0x4a188aa367f90ab1L, 0, 0),
    new Dint64(0xfa9c58fd796837d4L, 0x10655ecd5cc771d8L, 0, 0),
    new Dint64(0xfa7301d859796671L, 0x1fe196a53fb5b237L, 0, 0),
    new Dint64(0xfa491035e55da3a3L, 0xd24377c77a591e24L, 0, 0),
    new Dint64(0xfa1e842ffc96e4e0L, 0x431c393c7f62da65L, 0, 0),
    new Dint64(0xf9f35de0dde328abL, 0xba5dbf4510eddc8fL, 0, 0),
    new Dint64(0xf9c79d63272c4628L, 0x4504ae08d19b2980L, 0, 0),
    new Dint64(0xf99b42d1d57781ebL, 0x78685d850f80ecdcL, 0, 0),
    new Dint64(0xf96e4e4844d4e82aL, 0x80e8c17bf80e8f02L, 0, 0),
    new Dint64(0xf940bfe2304e6c45L, 0xc0e2a1352ed7f292L, 0, 0),
    new Dint64(0xf91297bbb1d6cdbeL, 0x68fc6e4d6a920bd2L, 0, 0),
    new Dint64(0xf8e3d5f1423842a0L, 0x9701914c7f8fbcd7L, 0, 0),
    new Dint64(0xf8b47a9fb902e76cL, 0xac9f07f54ff5bc14L, 0, 0),
    new Dint64(0xf88485e44c7af48aL, 0xb36a9dfaadafc1e1L, 0, 0),
    new Dint64(0xf853f7dc9186b952L, 0xc7adc6b4988891bbL, 0, 0),
    new Dint64(0xf822d0a67b9c5cb5L, 0xa776175bd284fe05L, 0, 0),
    new Dint64(0xf7f110605caf6390L, 0xa76f7efc19aed41cL, 0, 0),
    new Dint64(0xf7beb728e51dfcb8L, 0x730785813f78aa1eL, 0, 0),
    new Dint64(0xf78bc51f239e12c6L, 0x214cffcee9dd33caL, 0, 0),
    new Dint64(0xf7583a62852a23b2L, 0x4becad887680c197L, 0, 0),
    new Dint64(0xf7241712d4edde49L, 0xf99107e50d631330L, 0, 0),
    new Dint64(0xf6ef5b503c328589L, 0x50ca117eb18beed7L, 0, 0),
    new Dint64(0xf6ba073b424b19e8L, 0x2c791f59cc1ffc23L, 0, 0),
    new Dint64(0xf6841af4cc8048a4L, 0xce8c455197cdf8a7L, 0, 0),
    new Dint64(0xf64d969e1dfc2119L, 0x119d358de0493956L, 0, 0),
    new Dint64(0xf6167a58d7b59026L, 0x9dc7e5954c5a8f24L, 0, 0),
    new Dint64(0xf5dec646f85ba1c6L, 0xc8c615e72768d6b5L, 0, 0),
    new Dint64(0xf5a67a8adc4088caL, 0xed0dd4bf62edd13fL, 0, 0),
    new Dint64(0xf56d97473d446cdaL, 0x275a2bbb2bab6c8aL, 0, 0),
    new Dint64(0xf5341c9f32bffeb9L, 0x8da64484aaa0febcL, 0, 0),
    new Dint64(0xf4fa0ab6316ed2ecL, 0x163c5c7f03b718c5L, 0, 0),
    new Dint64(0xf4bf61b00b5982b7L, 0x890ac4aafa6a37bfL, 0, 0),
    new Dint64(0xf48421b0efbf939bL, 0xf8f9d3b87d11fd52L, 0, 0),
    new Dint64(0xf4484add6b01254bL, 0x667e06866c07c369L, 0, 0),
    new Dint64(0xf40bdd5a6688662fL, 0x5019794a1f5896e5L, 0, 0),
    new Dint64(0xf3ced94d28b2ce8aL, 0x18ef535a7ffa7a3dL, 0, 0),
    new Dint64(0xf3913edb54ba2242L, 0x50f29b4b49f31c37L, 0, 0),
    new Dint64(0xf3530e2aea9d3966L, 0xd981acdcf6bc3e4L, 0, 0),
    new Dint64(0xf314476247088f74L, 0xa5486bdc455d56a2L, 0, 0),
    new Dint64(0xf2d4eaa8233e997dL, 0x431be53f92ece9e6L, 0, 0),
    new Dint64(0xf294f82394ffe320L, 0xebadcdbf915e8f6cL, 0, 0),
    new Dint64(0xf2546ffc0e72f286L, 0xaf0eed81e8c51e55L, 0, 0),
    new Dint64(0xf21352595e0bf350L, 0xe7112e89103cc0c7L, 0, 0),
    new Dint64(0xf1d19f63ae7428a2L, 0x844e6a35ddc2b713L, 0, 0),
    new Dint64(0xf18f574386712643L, 0x8f6bac72988088b0L, 0, 0),
    new Dint64(0xf14c7a21c8cbd0f4L, 0x2730081c758fb42bL, 0, 0),
    new Dint64(0xf1090827b43725fdL, 0x67127db35b287316L, 0, 0),
    new Dint64(0xf0c5017ee336ca0fL, 0xc4e557b119ef3185L, 0, 0),
    new Dint64(0xf08066514c055f7eL, 0x973ea9903ed5125fL, 0, 0),
    new Dint64(0xf03b36c9407aa3e8L, 0x992d39ec5c561d28L, 0, 0),
    new Dint64(0xeff573116df1555dL, 0x62aef7b55319d1d4L, 0, 0),
    new Dint64(0xefaf1b54dd2cdf0fL, 0xf03a18a5e16ab641L, 0, 0),
    new Dint64(0xef682fbef23ecda6L, 0x767c0e8ad33bc085L, 0, 0),
    new Dint64(0xef20b07b6c6c0b37L, 0xe2398bf0eeb28cdeL, 0, 0),
    new Dint64(0xeed89db66611e307L, 0x86f8c20fb664b01bL, 0, 0),
    new Dint64(0xee8ff79c548acd0fL, 0xa1d2c3d018a9279fL, 0, 0),
    new Dint64(0xee46be5a0813016bL, 0x7872773830d368beL, 0, 0),
    new Dint64(0xedfcf21cabacd3b1L, 0xfee6a1eebfa13b4aL, 0, 0),
    new Dint64(0xedb29311c504d652L, 0x11815196b9fbf5dfL, 0, 0),
    new Dint64(0xed67a1673455c601L, 0x7289102076a125e5L, 0, 0),
    new Dint64(0xed1c1d4b344c3d4fL, 0xddffe98c4f8aa031L, 0, 0),
    new Dint64(0xecd006ec59ea306fL, 0xa8392eb238578ab0L, 0, 0),
    new Dint64(0xec835e79946a3145L, 0x7e610231ac1d6181L, 0, 0),
    new Dint64(0xec3624222d227bd1L, 0x278047ae3dd0889L, 0, 0),
    new Dint64(0xebe85815c767cb00L, 0x1e99ccb9adc62ca6L, 0, 0),
    new Dint64(0xeb99fa84606ff5ffL, 0xdae311e656e0661L, 0, 0),
    new Dint64(0xeb4b0b9e4f345617L, 0x39e39c6c2ab3655dL, 0, 0),
    new Dint64(0xeafb8b944453f52fL, 0x3383bbb5156bf1d7L, 0, 0),
    new Dint64(0xeaab7a9749f584feL, 0x24db98ad3a0647a1L, 0, 0),
    new Dint64(0xea5ad8d8c3a91f05L, 0x4a0ca5ea449b1c83L, 0, 0),
    new Dint64(0xea09a68a6e49cd62L, 0x15ad45b4a1b5e823L, 0, 0),
    new Dint64(0xe9b7e3de5fdedc8bL, 0xcd24d4bd1056c826L, 0, 0),
    new Dint64(0xe9659107077cf60fL, 0x89a92b199adfbafaL, 0, 0),
    new Dint64(0xe912ae372d27045dL, 0xacb1c26a06e5ae02L, 0, 0),
    new Dint64(0xe8bf3ba1f1aedfbbL, 0xf8972affb3d98e1fL, 0, 0),
    new Dint64(0xe86b397ace95c46fL, 0x9fec1e78c4376186L, 0, 0),
    new Dint64(0xe816a7f595ec9232L, 0xbfe8378abfb87b6fL, 0, 0),
    new Dint64(0xe7c187467233d508L, 0xdbfb0fe56c6f80feL, 0, 0),
    new Dint64(0xe76bd7a1e63b9786L, 0x125129529d48a92fL, 0, 0),
    new Dint64(0xe715993ccd02fe9cL, 0xe2ba81b9ce96e02eL, 0, 0),
    new Dint64(0xe6becc4c5997af06L, 0x82fcedb4c6434d76L, 0, 0),
    new Dint64(0xe667710616f4fc59L, 0xdd2a3e32c3859960L, 0, 0),
    new Dint64(0xe60f879fe7e2e1e5L, 0x7613b68f6ab03130L, 0, 0),
    new Dint64(0xe5b7105006d4c560L, 0x9b695cd67c93bd79L, 0, 0),
    new Dint64(0xe55e0b4d05c80388L, 0x5a7c210a3a15e7eaL, 0, 0),
    new Dint64(0xe50478cdce2246bcL, 0xe1f5a58c80292554L, 0, 0),
    new Dint64(0xe4aa5909a08fa7b4L, 0x122785ae67f5515dL, 0, 0),
    new Dint64(0xe44fac3814e09856L, 0x20d63b5b9e3cd6acL, 0, 0),
    new Dint64(0xe3f4729119e798d9L, 0x56992551ae074e99L, 0, 0),
    new Dint64(0xe398ac4cf556b732L, 0xd1197dc12c63176L, 0, 0),
    new Dint64(0xe33c59a4439cd8ecL, 0x36563e2ffad8351aL, 0, 0),
    new Dint64(0xe2df7acff7c2cf83L, 0xd6fe4dd22e60a4a2L, 0, 0),
    new Dint64(0xe28210095b483751L, 0xfd39138aa2d508edL, 0, 0),
    new Dint64(0xe224198a0e002123L, 0xe0521df01a1be6f5L, 0, 0),
    new Dint64(0xe1c5978c05ed8691L, 0xf4e8a8372f8c5810L, 0, 0),
    new Dint64(0xe1668a498f1f892cL, 0xe2f9d4600f4d0325L, 0, 0),
    new Dint64(0xe106f1fd4b8d7c96L, 0x6ba8a9d9ba877899L, 0, 0),
    new Dint64(0xe0a6cee232f2bb9cL, 0x6d6c98fe79817946L, 0, 0),
    new Dint64(0xe046213392aa486cL, 0x55ff6038a5197367L, 0, 0),
    new Dint64(0xdfe4e92d0d8a37f5L, 0x720588ff6547d884L, 0, 0),
    new Dint64(0xdf83270a9bbee890L, 0xab01350f013d78ddL, 0, 0),
    new Dint64(0xdf20db088aa60404L, 0x64a58b2f103485ddL, 0, 0),
    new Dint64(0xdebe05637ca94cfbL, 0x4b19aa71fec3ae6dL, 0, 0),
    new Dint64(0xde5aa65869193805L, 0x4248f15548f69caL, 0, 0),
    new Dint64(0xddf6be249c075037L, 0xd597b10a01676659L, 0, 0),
    new Dint64(0xdd924d05b620678aL, 0x739c45b982193b5eL, 0, 0),
    new Dint64(0xdd2d5339ac8692fdL, 0x49c6e0ea76cbcaacL, 0, 0),
    new Dint64(0xdcc7d0fec8aaf2aaL, 0xb2069fd0b482b4e8L, 0, 0),
    new Dint64(0xdc61c693a82745d5L, 0xaca8017e375b64e5L, 0, 0),
    new Dint64(0xdbfb34373c974b0eL, 0xccb7fd40d543f4a1L, 0, 0),
    new Dint64(0xdb941a28cb71ec87L, 0x2c19b63253da43fcL, 0, 0),
    new Dint64(0xdb2c78a7ede238a9L, 0x5a98479cbef2ecbcL, 0, 0),
    new Dint64(0xdac44ff490a02710L, 0x5b267c1bcff0ab62L, 0, 0),
    new Dint64(0xda5ba04ef3c929f4L, 0xe257bde73d83dc1aL, 0, 0),
    new Dint64(0xd9f269f7aab88c29L, 0x28e81dcb6dab91acL, 0, 0),
    new Dint64(0xd988ad2f9bdf9bbbL, 0xc4e4dc69fc2fff6fL, 0, 0),
    new Dint64(0xd91e6a38009da15aL, 0x1bb35ad6d2e74b67L, 0, 0),
    new Dint64(0xd8b3a1526517a48bL, 0x1ed1a8ff78f1b632L, 0, 0),
    new Dint64(0xd84852c0a80ffcdbL, 0x24b9fe00663574a4L, 0, 0),
    new Dint64(0xd7dc7ec4fabdb011L, 0xced12d2899b803dbL, 0, 0),
    new Dint64(0xd77025a1e0a39d8bL, 0xcb78e80e67ba1b8L, 0, 0),
    new Dint64(0xd703479a2f6776ccL, 0x6cb3bfd65b38562bL, 0, 0),
    new Dint64(0xd695e4f10ea88570L, 0x83f082b570611d7L, 0, 0),
    new Dint64(0xd627fde9f7d63e7eL, 0x7afbefc05e9f7d99L, 0, 0),
    new Dint64(0xd5b992c8b606a351L, 0x7190b755535d4f18L, 0, 0),
    new Dint64(0xd54aa3d165cc7018L, 0x7d00ae97abaa4096L, 0, 0),
    new Dint64(0xd4db3148750d1819L, 0xf630e8b6dac83e69L, 0, 0),
    new Dint64(0xd46b3b72a2d68fc9L, 0xdc4663a3168698d2L, 0, 0),
    new Dint64(0xd3fac294ff34e4d0L, 0xb77d4f6bd0ee8591L, 0, 0),
    new Dint64(0xd389c6f4eb07a41cL, 0xa8faac741a6394dcL, 0, 0),
    new Dint64(0xd31848d817d70e16L, 0xeeeaddb72f00e0ddL, 0, 0),
    new Dint64(0xd2a6488487a91918L, 0x4300fd1c1ce507e5L, 0, 0),
    new Dint64(0xd233c6408cd64236L, 0x981ba7e42537275fL, 0, 0),
    new Dint64(0xd1c0c252c9de2c86L, 0xda7485a5aeffeb4cL, 0, 0),
    new Dint64(0xd14d3d02313c0eedL, 0x744fea20e8abef92L, 0, 0),
    new Dint64(0xd0d93696053af098L, 0x77a18eb13d2ecde5L, 0, 0),
    new Dint64(0xd064af55d7c9b43eL, 0x6b8a685f6cb61c21L, 0, 0),
    new Dint64(0xcfefa7898a4ef23cL, 0xdaf200dd81212d10L, 0, 0),
    new Dint64(0xcf7a1f794d7ca1b1L, 0xdfcb60445c1bf973L, 0, 0),
    new Dint64(0xcf04176da12390acL, 0x4d27090f10c454eL, 0, 0),
    new Dint64(0xce8d8faf5406ab8bL, 0xf5babff66def7892L, 0, 0),
    new Dint64(0xce16888783ae13b3L, 0x93e391861a034684L, 0, 0),
    new Dint64(0xcd9f023f9c3a059eL, 0x23af31db7179a4aaL, 0, 0),
    new Dint64(0xcd26fd2158358e7dL, 0x649474e36b8db9d3L, 0, 0),
    new Dint64(0xccae7976c0691177L, 0x83e907fbd7aaf0b0L, 0, 0),
    new Dint64(0xcc35778a2bac9ca1L, 0xf839ce18e08bfb50L, 0, 0),
    new Dint64(0xcbbbf7a63eba0dd5L, 0x70cbb7f3343451beL, 0, 0),
    new Dint64(0xcb41fa15ebff0777L, 0x2293661be51140abL, 0, 0),
    new Dint64(0xcac77f24736eb553L, 0xd9944be1631846d8L, 0, 0),
    new Dint64(0xca4c871d625361a9L, 0x5328edeb3e6784deL, 0, 0),
    new Dint64(0xc9d1124c931fda7aL, 0x8335241be1693225L, 0, 0),
    new Dint64(0xc95520fe2d40a74bL, 0x83b0e96e1249c2b0L, 0, 0),
    new Dint64(0xc8d8b37ea4ed0f62L, 0xb562c00b34ee771L, 0, 0),
    new Dint64(0xc85bca1abaf7f0a7L, 0x65862939b83382e0L, 0, 0),
    new Dint64(0xc7de651f7ca06749L, 0x2b31bc86877fd2cL, 0, 0),
    new Dint64(0xc76084da43624634L, 0xd5c149509e9059f1L, 0, 0),
    new Dint64(0xc6e22998b4c6608eL, 0xcfe6c1b1a6b4e2a4L, 0, 0),
    new Dint64(0xc66353a8c232a43cL, 0xe993503baf5afb41L, 0, 0),
    new Dint64(0xc5e40358a8ba05a7L, 0x43da25d99267326bL, 0, 0),
    new Dint64(0xc56438f6f0ec3ccaL, 0xab4906075507e74L, 0, 0),
    new Dint64(0xc4e3f4d26ea553b6L, 0xdd40950cf1ed92faL, 0, 0),
    new Dint64(0xc463373a40dd06a3L, 0x9dd768f30ca8e85cL, 0, 0),
    new Dint64(0xc3e2007dd175f5a4L, 0xa87e78136665cdb2L, 0, 0),
    new Dint64(0xc36050ecd50ca830L, 0x8ac9e1386e4cbabbL, 0, 0),
    new Dint64(0xc2de28d74ac6628bL, 0x74c8f010d986a9e0L, 0, 0),
    new Dint64(0xc25b888d7c1fcd38L, 0xb7041e9bc8c18b0dL, 0, 0),
    new Dint64(0xc1d8705ffcbb6e90L, 0xbdf0715cb8b20bd7L, 0, 0),
    new Dint64(0xc154e09faa2ff69aL, 0x17858573216e0a22L, 0, 0),
    new Dint64(0xc0d0d99dabd65d44L, 0x2bda5328933c854aL, 0, 0),
    new Dint64(0xc04c5bab7297d322L, 0x6dd06968e0ed1957L, 0, 0),
    new Dint64(0xbfc7671ab8bb84c6L, 0xe4e62d86dd136e78L, 0, 0),
    new Dint64(0xbf41fc3d81b430dbL, 0xd46655d6b012455L, 0, 0),
    new Dint64(0xbebc1b6619ed9116L, 0x2715ef03f8543355L, 0, 0),
    new Dint64(0xbe35c4e716999630L, 0x29d7f7b67d43b177L, 0, 0),
    new Dint64(0xbdaef913557d76f0L, 0xac85320f528d6d5dL, 0, 0),
    new Dint64(0xbd27b83dfcbe9279L, 0x2ea36923d5d8e213L, 0, 0),
    new Dint64(0xbca002ba7aaf25eaL, 0x4a48496734be336dL, 0, 0),
    new Dint64(0xbc17d8dc859ad583L, 0x727c405ffc73af56L, 0, 0),
    new Dint64(0xbb8f3af81b93095cL, 0xfce8d84068e825b6L, 0, 0),
    new Dint64(0xbb062961823b1ddcL, 0x5120e35e1c1a250cL, 0, 0),
    new Dint64(0xba7ca46d46946802L, 0x33201477347447d8L, 0, 0),
    new Dint64(0xb9f2ac703cca0db3L, 0x39db32d014440024L, 0, 0),
    new Dint64(0xb96841bf7ffcb21aL, 0x9de1e3b22b8bf4dbL, 0, 0),
    new Dint64(0xb8dd64b0720df647L, 0xa726f4f0828585c9L, 0, 0),
    new Dint64(0xb8521598bb6bce26L, 0x1c041d1ea5fb3fdbL, 0, 0),
    new Dint64(0xb7c654ce4adba9f2L, 0x2e7a35723f3ed035L, 0, 0),
    new Dint64(0xb73a22a755457448L, 0x7f86f63bb23f496aL, 0, 0),
    new Dint64(0xb6ad7f7a557e64f2L, 0xeb2d28ef943dc88cL, 0, 0),
    new Dint64(0xb6206b9e0c13a892L, 0xea7c015f12b987f7L, 0, 0),
    new Dint64(0xb592e7697f14dd4aL, 0x737dd2824b608d13L, 0, 0),
  };

  /* The following is a degree-7 polynomial with odd coefficients
     approximating sin2pi(x) for -2^-24 < x < 2^-11+2^-24
     with relative error 2^-77.306.
     Generated with sin_fast.sollya. */
  private static final double[] PSfast = {
    0x1.921fb54442d18p+2, 0x1.1a62645446203p-52, // degree 1 (h+l)
    -0x1.4abbce625be53p5,                        // degree 3
    0x1.466bc678d8d63p6,                         // degree 5
    -0x1.331554ca19669p6,                        // degree 7
  };
  /* The following is a degree-6 polynomial with even coefficients
     approximating cos2pi(x) for -2^-24 < x < 2^-11+2^-24
     with relative error 2^-75.188.
     Generated with cos_fast.sollya. */
  private static final double[] PCfast = {
    0x1p+0, -0x1.923015cp-77,                    // degree 0
    -0x1.3bd3cc9be45dep4,                        // degree 2
    0x1.03c1f080ad892p6,                         // degree 4
    -0x1.55a5c590f9e6ap6,                        // degree 6
  };

  /* The following is a degree-11 polynomial with odd coefficients
     approximating sin2pi(x) for 0 <= x < 2^-11 with relative error 2^-127.75.
     Generated with sin_accurate.sollya. */
  private static final Dint64[] PS = {
    new Dint64(0xc90fdaa22168c234L, 0xc4c6628b80dc1cd1L, 3, 0), // 1
    new Dint64(0xa55de7312df295f5L, 0x5dc72f712aa57db4L, 6, 1), // 3
    new Dint64(0xa335e33bad570e92L, 0x3f33be0021aa54d2L, 7, 0), // 5
    new Dint64(0x9969667315ec2d9dL, 0xe59d6ab8509a2025L, 7, 1), // 7
    new Dint64(0xa83c1a43bf1c6485L, 0x7d5f8f76fa7d74edL, 6, 0), // 9
    new Dint64(0xf16ab2898eae62f9L, 0xa7f0339113b8b3c5L, 4, 1), // 11
  };
  /* The following is a degree-10 polynomial with even coefficients
     approximating cos2pi(x) for 0 <= x < 2^-11 with relative error 2^-137.246.
     Generated with cos_accurate.sollya. */
  private static final Dint64[] PC = {
    new Dint64(0x8000000000000000L, 0x0L, 1, 0), // degree 0
    new Dint64(0x9de9e64df22ef2d2L, 0x56e26cd9808c1949L, 5, 1), // 2
    new Dint64(0x81e0f840dad61d9aL, 0x9980f00630cb655eL, 7, 0), // 4
    new Dint64(0xaae9e3f1e5ffcfe2L, 0xa508509534006249L, 7, 1), // 6
    new Dint64(0xf0fa83448dd1e094L, 0xe0603ce7044eebaL, 6, 0),  // 8
    new Dint64(0xd368f6f4207cfe49L, 0xec63157807ebffaL, 5, 1),  // 10
  };

  /* Table generated with ./buildSC 15 using accompanying buildSC.c.
     For each i, 0 <= i < 256, xi=i/2^11+SC[i][0], with
     SC[i][1] and SC[i][2] approximating sin2pi(xi) and cos2pi(xi)
     respectively, both with 53+15 bits of accuracy. */
  private static final double[][] SC = {
     {0x0p+0, 0x0p+0, 0x1p+0}, /* 0 */
     {-0x1.c0f6cp-35, 0x1.921f892b900fep-9, 0x1.ffff621623fap-1}, /* 1 */
     {-0x1.9c7935ep-35, 0x1.921f0ea27ce01p-8, 0x1.fffd8858eca2ep-1}, /* 2 */
     {-0x1.d14d1acp-34, 0x1.2d96af779b0bbp-7, 0x1.fffa72c986392p-1}, /* 3 */
     {-0x1.dba8f6a8p-33, 0x1.921d1ce2d0a1cp-7, 0x1.fff62169dddaap-1}, /* 4 */
     {0x1.a6b7cdfp-32, 0x1.f6a29bdb7377p-7, 0x1.fff0943c02419p-1}, /* 5 */
     {0x1.b49618dp-33, 0x1.2d936d1506f3dp-6, 0x1.ffe9cb44829cp-1}, /* 6 */
     {-0x1.398d6fcp-35, 0x1.5fd4d1e21de6dp-6, 0x1.ffe1c687174b1p-1}, /* 7 */
     {-0x1.e9e9a8c8p-31, 0x1.9215597791e0ap-6, 0x1.ffd886097afcfp-1}, /* 8 */
     {-0x1.34e844cp-32, 0x1.c454f2e9480c7p-6, 0x1.ffce09ce95933p-1}, /* 9 */
     {-0x1.989a8a4p-32, 0x1.f693709b94f92p-6, 0x1.ffc251dfbac0cp-1}, /* 10 */
     {0x1.04a9b99p-30, 0x1.146860e69a571p-5, 0x1.ffb55e40a5c43p-1}, /* 11 */
     {-0x1.56947cp-36, 0x1.2d865748774adp-5, 0x1.ffa72efff95d1p-1}, /* 12 */
     {-0x1.c348768p-35, 0x1.46a396d34121ap-5, 0x1.ff97c420a8451p-1}, /* 13 */
     {0x1.9e80552p-32, 0x1.5fc00e6e4c65cp-5, 0x1.ff871dacd8761p-1}, /* 14 */
     {0x1.3f11d74p-34, 0x1.78dbaa97099ebp-5, 0x1.ff753bb18af95p-1}, /* 15 */
     {0x1.c039af4p-33, 0x1.91f65fc0abc0ap-5, 0x1.ff621e370ca7ap-1}, /* 16 */
     {0x1.53e1f8p-35, 0x1.ab101bf74ac2ep-5, 0x1.ff4dc54b00181p-1}, /* 17 */
     {0x1.114a649p-29, 0x1.c428d7de920e9p-5, 0x1.ff3830f2e9043p-1}, /* 18 */
     {0x1.adf0ef4p-31, 0x1.dd40723a3cdfbp-5, 0x1.ff21614b9d9adp-1}, /* 19 */
     {-0x1.d21f5918p-30, 0x1.f656e1e9e59cdp-5, 0x1.ff09565e83d77p-1}, /* 20 */
     {-0x1.4f54d708p-30, 0x1.07b612d6be078p-4, 0x1.fef0102c634e3p-1}, /* 21 */
     {-0x1.1efec9ap-30, 0x1.1440118ba7bdp-4, 0x1.fed58ecf342dap-1}, /* 22 */
     {0x1.cc17ba88p-29, 0x1.20c96cf0a7eedp-4, 0x1.feb9d24646fa6p-1}, /* 23 */
     {0x1.121dbe4p-33, 0x1.2d5209628edfp-4, 0x1.fe9cdacf99cffp-1}, /* 24 */
     {-0x1.9ecf61p-34, 0x1.39d9f103bf7f7p-4, 0x1.fe7ea854e6b08p-1}, /* 25 */
     {-0x1.04ede8ep-31, 0x1.466116c629e5cp-4, 0x1.fe5f3af4ee201p-1}, /* 26 */
     {-0x1.1821cecp-31, 0x1.52e773c9920c7p-4, 0x1.fe3e92c0e4108p-1}, /* 27 */
     {0x1.cdec726p-31, 0x1.5f6d02131f0b2p-4, 0x1.fe1cafc7f1a24p-1}, /* 28 */
     {-0x1.edece4dp-31, 0x1.6bf1b2653648cp-4, 0x1.fdf99233c230cp-1}, /* 29 */
     {-0x1.2aa4d1cp-31, 0x1.787585bc45f0fp-4, 0x1.fdd53a01d11d9p-1}, /* 30 */
     {0x1.d461592p-32, 0x1.84f871e32cf68p-4, 0x1.fdafa74f16482p-1}, /* 31 */
     {0x1.f0cbd728p-29, 0x1.917a71d3d2956p-4, 0x1.fd88da29f302ep-1}, /* 32 */
     {-0x1.583247p-30, 0x1.9dfb6c9865b06p-4, 0x1.fd60d2e14a6b1p-1}, /* 33 */
     {-0x1.2e81bf4p-30, 0x1.aa7b706bfdbbap-4, 0x1.fd3791484ff5p-1}, /* 34 */
     {-0x1.13941418p-28, 0x1.b6fa680a05c27p-4, 0x1.fd0d15a4b8471p-1}, /* 35 */
     {0x1.71098ffp-30, 0x1.c3785eba12b42p-4, 0x1.fce15fceddccfp-1}, /* 36 */
     {-0x1.c3519e8p-32, 0x1.cff53302f059p-4, 0x1.fcb4703b969e1p-1}, /* 37 */
     {0x1.2f522a5p-27, 0x1.dc70fb84af16ep-4, 0x1.fc8646987fc1dp-1}, /* 38 */
     {-0x1.ae9bed8p-33, 0x1.e8eb7f8a589e2p-4, 0x1.fc56e3b91ca3ap-1}, /* 39 */
     {0x1.f8868b2p-30, 0x1.f564e87d2330fp-4, 0x1.fc264701f9a09p-1}, /* 40 */
     {-0x1.b07985f8p-29, 0x1.00ee8835051f4p-3, 0x1.fbf47105f7439p-1}, /* 41 */
     {0x1.cbdaa94p-30, 0x1.072a05e1d4d8ep-3, 0x1.fbc16172a9e36p-1}, /* 42 */
     {0x1.37c5b908p-28, 0x1.0d64df9619f0dp-3, 0x1.fb8d18b635327p-1}, /* 43 */
     {-0x1.068b5fc8p-28, 0x1.139f09bc617f5p-3, 0x1.fb5797351da85p-1}, /* 44 */
     {-0x1.8ea66818p-29, 0x1.19d8919fa4ec8p-3, 0x1.fb20dc7da8affp-1}, /* 45 */
     {0x1.6278ceb8p-28, 0x1.2011719d50b87p-3, 0x1.fae8e8bd4427fp-1}, /* 46 */
     {-0x1.096df84p-29, 0x1.264993433763ap-3, 0x1.faafbcbfca356p-1}, /* 47 */
     {0x1.9b2534fp-29, 0x1.2c810967bbf7p-3, 0x1.fa7557d8d987ep-1}, /* 48 */
     {0x1.215b4ep-34, 0x1.32b7bfa25c91bp-3, 0x1.fa39bac71954bp-1}, /* 49 */
     {-0x1.94db891p-30, 0x1.38edb9d29b39dp-3, 0x1.f9fce56700a6dp-1}, /* 50 */
     {0x1.7727f7b8p-29, 0x1.3f22f7c3cce3ap-3, 0x1.f9bed7b8c8d8cp-1}, /* 51 */
     {-0x1.0cb33038p-29, 0x1.45576971dd53p-3, 0x1.f97f925d53c83p-1}, /* 52 */
     {-0x1.9071106p-31, 0x1.4b8b175c71e22p-3, 0x1.f93f14feb8022p-1}, /* 53 */
     {0x1.62741e78p-29, 0x1.51bdfa7ea30d5p-3, 0x1.f8fd5fe3efac8p-1}, /* 54 */
     {0x1.f8e16d0cp-28, 0x1.57f00e80e6e12p-3, 0x1.f8ba733a1ceb1p-1}, /* 55 */
     {-0x1.76acbcap-31, 0x1.5e2143b7bc1c2p-3, 0x1.f8764fad5e9bfp-1}, /* 56 */
     {-0x1.0a0f73ap-30, 0x1.6451a76411746p-3, 0x1.f830f4ad232d8p-1}, /* 57 */
     {0x1.ca11d1bcp-28, 0x1.6a8135d7bd143p-3, 0x1.f7ea625eb5af7p-1}, /* 58 */
     {-0x1.02f23628p-29, 0x1.70afd74071191p-3, 0x1.f7a299d3f182ap-1}, /* 59 */
     {0x1.b34dcb8p-29, 0x1.76dda08544b5cp-3, 0x1.f7599a1ac7ecdp-1}, /* 60 */
     {0x1.161ff4p-32, 0x1.7d0a7bf2d4abap-3, 0x1.f70f64322da74p-1}, /* 61 */
     {-0x1.c49b8b4p-31, 0x1.83366ddb3de23p-3, 0x1.f6c3f7e7c2707p-1}, /* 62 */
     {0x1.21da851p-29, 0x1.8961743b1429p-3, 0x1.f6775552a6ba2p-1}, /* 63 */
     {0x1.ac63edap-30, 0x1.8f8b851098588p-3, 0x1.f6297cef0cdd6p-1}, /* 64 */
     {0x1.27ef489cp-27, 0x1.95b4a5b9f2cebp-3, 0x1.f5da6e7820551p-1}, /* 65 */
     {0x1.ae8937p-30, 0x1.9bdcc07900146p-3, 0x1.f58a2b0689c82p-1}, /* 66 */
     {0x1.eb48c7ep-29, 0x1.a203e4a4f950ep-3, 0x1.f538b1d392049p-1}, /* 67 */
     {-0x1.bfd282fp-29, 0x1.a829ffaad0d79p-3, 0x1.f4e603d51f1aap-1}, /* 68 */
     {0x1.7ccf638p-29, 0x1.ae4f1fa80e1b5p-3, 0x1.f492204c5ef9ep-1}, /* 69 */
     {-0x1.2435c578p-28, 0x1.b4732b72ebc86p-3, 0x1.f43d0890e1e72p-1}, /* 70 */
     {0x1.0293fecp-30, 0x1.ba9634155f866p-3, 0x1.f3e6bbb6c2ea4p-1}, /* 71 */
     {-0x1.7bb1f92p-29, 0x1.c0b82461f65ep-3, 0x1.f38f3ae6f9afcp-1}, /* 72 */
     {0x1.27aaebcp-29, 0x1.c6d906faacf65p-3, 0x1.f3368589e17a2p-1}, /* 73 */
     {-0x1.2e2bcd5p-27, 0x1.ccf8c3f74a6c9p-3, 0x1.f2dc9cfb5fa74p-1}, /* 74 */
     {-0x1.6f070acp-30, 0x1.d31773ba218a8p-3, 0x1.f2817fd4d045bp-1}, /* 75 */
     {0x1.469adfcp-29, 0x1.d935004779e57p-3, 0x1.f2252f59c122dp-1}, /* 76 */
     {0x1.4f51c18p-32, 0x1.df5164301377ap-3, 0x1.f1c7abdeaa3efp-1}, /* 77 */
     {0x1.78e44dap-29, 0x1.e56ca4202807cp-3, 0x1.f168f51c5d5d5p-1}, /* 78 */
     {0x1.49bb5f8p-32, 0x1.eb86b4a1b7e9bp-3, 0x1.f1090bc4b68p-1}, /* 79 */
     {-0x1.67ba541p-28, 0x1.f19f9369d5e93p-3, 0x1.f0a7effdc937fp-1}, /* 80 */
     {0x1.c0cab95p-29, 0x1.f7b74ab7219d2p-3, 0x1.f045a1219e594p-1}, /* 81 */
     {-0x1.2b77e32p-30, 0x1.fdcdc0ca3288dp-3, 0x1.efe220cf5c751p-1}, /* 82 */
     {-0x1.e0d8cbp-33, 0x1.01f18054c8362p-2, 0x1.ef7d6e54c347dp-1}, /* 83 */
     {-0x1.ecd5b9cp-29, 0x1.04fb7f6d35d68p-2, 0x1.ef178a6f9a987p-1}, /* 84 */
     {0x1.eb24de5p-29, 0x1.0804e1d369ff2p-2, 0x1.eeb074934fdfp-1}, /* 85 */
     {0x1.4a897c4p-30, 0x1.0b0d9d7b0d042p-2, 0x1.ee482e14bcdep-1}, /* 86 */
     {0x1.336c376p-30, 0x1.0e15b555e7becp-2, 0x1.eddeb6908ca8cp-1}, /* 87 */
     {-0x1.3952d9p-31, 0x1.111d25efd48b8p-2, 0x1.ed740e7eb8dd6p-1}, /* 88 */
     {0x1.fc2a5d4p-31, 0x1.1423ef5c7e1bdp-2, 0x1.ed0835dc24e89p-1}, /* 89 */
     {0x1.a88ed37p-29, 0x1.172a0eb8361dap-2, 0x1.ec9b2d0ec8288p-1}, /* 90 */
     {-0x1.8ca4cb94p-27, 0x1.1a2f7b10b6d7p-2, 0x1.ec2cf55d6117cp-1}, /* 91 */
     {0x1.0144524p-27, 0x1.1d3446fd0cd3fp-2, 0x1.ebbd8c1d62f96p-1}, /* 92 */
     {-0x1.abf810cp-28, 0x1.203855b85f89ap-2, 0x1.eb4cf57454132p-1}, /* 93 */
     {0x1.5d4c5d58p-28, 0x1.233bbcca40561p-2, 0x1.eadb2e40746cap-1}, /* 94 */
     {-0x1.a1b0c58p-29, 0x1.263e685b1d714p-2, 0x1.ea68396d87754p-1}, /* 95 */
     {-0x1.77c8dacp-29, 0x1.294061d2eb611p-2, 0x1.e9f41597393c8p-1}, /* 96 */
     {0x1.915540ep-30, 0x1.2c41a580014cfp-2, 0x1.e97ec348fb87fp-1}, /* 97 */
     {-0x1.abb6d9bp-28, 0x1.2f422b2d0990cp-2, 0x1.e90843c55b996p-1}, /* 98 */
     {-0x1.b8ee5d58p-28, 0x1.3241f8cea2836p-2, 0x1.e890962268c49p-1}, /* 99 */
     {-0x1.1cd29828p-28, 0x1.35410a8396266p-2, 0x1.e817baf85c094p-1}, /* 100 */
     {-0x1.e216afp-32, 0x1.383f5e08283e2p-2, 0x1.e79db2a188b0ap-1}, /* 101 */
     {-0x1.24afc3p-31, 0x1.3b3cef6993c0bp-2, 0x1.e7227dbf82004p-1}, /* 102 */
     {-0x1.aa1657cp-31, 0x1.3e39be4767224p-2, 0x1.e6a61c62d5274p-1}, /* 103 */
     {-0x1.c5b65fap-30, 0x1.4135c898485bbp-2, 0x1.e6288ee07fea5p-1}, /* 104 */
     {0x1.23e8978p-32, 0x1.44310de3c284bp-2, 0x1.e5a9d54bbd26cp-1}, /* 105 */
     {-0x1.2b1d77ap-29, 0x1.472b8976d498dp-2, 0x1.e529f06cb187dp-1}, /* 106 */
     {-0x1.daaa348p-31, 0x1.4a253cb97efd1p-2, 0x1.e4a8e007231a2p-1}, /* 107 */
     {-0x1.322f5708p-28, 0x1.4d1e2260c3422p-2, 0x1.e426a500f6e33p-1}, /* 108 */
     {0x1.64758e8p-29, 0x1.50163eca0b337p-2, 0x1.e3a33e996b722p-1}, /* 109 */
     {0x1.12486278p-28, 0x1.530d89a17e007p-2, 0x1.e31eae3fb917bp-1}, /* 110 */
     {-0x1.6c3416ccp-27, 0x1.5603fcf8cd8a3p-2, 0x1.e298f502a579bp-1}, /* 111 */
     {0x1.ab481ffp-29, 0x1.58f9a896aa209p-2, 0x1.e2121016e14fcp-1}, /* 112 */
     {-0x1.6eb838bp-29, 0x1.5bee77aaf890bp-2, 0x1.e18a032eb4df5p-1}, /* 113 */
     {-0x1.d159b8p-32, 0x1.5ee2734efeef5p-2, 0x1.e100ccaa6bd78p-1}, /* 114 */
     {-0x1.a42e4ap-34, 0x1.61d595bedeabcp-2, 0x1.e0766d944915ep-1}, /* 115 */
     {-0x1.43d0dcp-30, 0x1.64c7dd5cc0cd1p-2, 0x1.dfeae63903034p-1}, /* 116 */
     {-0x1.8c7bdb7p-27, 0x1.67b9453ca2122p-2, 0x1.df5e378482eaep-1}, /* 117 */
     {0x1.1c0ead6p-30, 0x1.6aa9d844c980ap-2, 0x1.ded05f6a23a52p-1}, /* 118 */
     {0x1.7d526p-31, 0x1.6d99867e90d92p-2, 0x1.de4160e97b2e2p-1}, /* 119 */
     {0x1.924e0368p-28, 0x1.7088555d3c816p-2, 0x1.ddb13afb14e37p-1}, /* 120 */
     {-0x1.74b7c3ep-30, 0x1.73763c09fba09p-2, 0x1.dd1fef5335416p-1}, /* 121 */
     {-0x1.7943adp-30, 0x1.766340685c982p-2, 0x1.dc8d7ccf2567ap-1}, /* 122 */
     {0x1.79dd614p-29, 0x1.794f5f7522b88p-2, 0x1.dbf9e402aa5c3p-1}, /* 123 */
     {0x1.7b64f32p-30, 0x1.7c3a939c32d81p-2, 0x1.db652607e0db1p-1}, /* 124 */
     {-0x1.2bea5ce8p-28, 0x1.7f24db825141cp-2, 0x1.dacf43268b5bp-1}, /* 125 */
     {0x1.733c024p-30, 0x1.820e3b8bf15ap-2, 0x1.da383a7aed887p-1}, /* 126 */
     {-0x1.eac0fc94p-27, 0x1.84f6a51d077b3p-2, 0x1.d9a00efd84537p-1}, /* 127 */
     {0x1.aca37338p-27, 0x1.87de2f4704f98p-2, 0x1.d906bbf17f4dap-1}, /* 128 */
     {-0x1.910c4fp-30, 0x1.8ac4b7dc0d986p-2, 0x1.d86c4862b5d6ep-1}, /* 129 */
     {-0x1.33bb86p-31, 0x1.8daa52b4dc041p-2, 0x1.d7d0b0374a559p-1}, /* 130 */
     {-0x1.69e1507p-27, 0x1.908ef408ad22p-2, 0x1.d733f5e71c3bcp-1}, /* 131 */
     {0x1.cffacf08p-27, 0x1.9372ab7784d36p-2, 0x1.d696161d786c9p-1}, /* 132 */
     {-0x1.8629d9fp-26, 0x1.965552b0849abp-2, 0x1.d5f7190eeae23p-1}, /* 133 */
     {0x1.415p-30, 0x1.99371687c64f3p-2, 0x1.d556f5155d9ddp-1}, /* 134 */
     {-0x1.bd37aad8p-27, 0x1.9c17cf40715cbp-2, 0x1.d4b5b2caf8386p-1}, /* 135 */
     {0x1.d02cde7p-26, 0x1.9ef79ea4d995dp-2, 0x1.d4134ac5eb246p-1}, /* 136 */
     {-0x1.10547acp-30, 0x1.a1d653d9adf5ep-2, 0x1.d36fc7d291602p-1}, /* 137 */
     {-0x1.01a1a228p-27, 0x1.a4b40f9c0120bp-2, 0x1.d2cb22b45236bp-1}, /* 138 */
     {0x1.3ce2bacp-29, 0x1.a790ce2056b9ap-2, 0x1.d2255c3ae11a5p-1}, /* 139 */
     {-0x1.ccb4a6p-32, 0x1.aa6c828db4ea8p-2, 0x1.d17e774d4e3e2p-1}, /* 140 */
     {0x1.5db4bp-29, 0x1.ad47321f29847p-2, 0x1.d0d672bc0b122p-1}, /* 141 */
     {0x1.32f6a6ep-29, 0x1.b020d7a285e23p-2, 0x1.d02d4fb84d334p-1}, /* 142 */
     {0x1.cf8e39bcp-26, 0x1.b2f97c27f7494p-2, 0x1.cf830c2248c5ep-1}, /* 143 */
     {0x1.8927bbp-30, 0x1.b5d10129a750ap-2, 0x1.ced7af22cb105p-1}, /* 144 */
     {-0x1.3dec3c1p-28, 0x1.b8a77f8d0bbc5p-2, 0x1.ce2b32e50d6cdp-1}, /* 145 */
     {-0x1.26ba536p-28, 0x1.bb7cf08f0290dp-2, 0x1.cd7d98fcf3b1ep-1}, /* 146 */
     {0x1.23c568ep-29, 0x1.be51524e3aa53p-2, 0x1.cccee1da3d56ep-1}, /* 147 */
     {-0x1.f3b3afp-29, 0x1.c1249c1f5f2f6p-2, 0x1.cc1f0f95e1e24p-1}, /* 148 */
     {-0x1.1286a47p-28, 0x1.c3f6d2ef7054bp-2, 0x1.cb6e20ff37e81p-1}, /* 149 */
     {0x1.641214ep-29, 0x1.c6c7f594003d9p-2, 0x1.cabc165bf1b6p-1}, /* 150 */
     {0x1.0cda7c9p-27, 0x1.c997ff2bffccbp-2, 0x1.ca08f0dee434cp-1}, /* 151 */
     {-0x1.5557ac9p-28, 0x1.cc66e7b42e8f1p-2, 0x1.c954b28bca62ep-1}, /* 152 */
     {0x1.555eb62p-28, 0x1.cf34bccc567a1p-2, 0x1.c89f57f6e20f3p-1}, /* 153 */
     {-0x1.4e0e361p-28, 0x1.d2016cbb5e39ap-2, 0x1.c7e8e59999e1fp-1}, /* 154 */
     {0x1.446da1ep-29, 0x1.d4cd039d0ed05p-2, 0x1.c731585f970ebp-1}, /* 155 */
     {0x1.103d328p-29, 0x1.d797767638decp-2, 0x1.c678b3174afe1p-1}, /* 156 */
     {0x1.5814d6p-28, 0x1.da60c7ae9dc22p-2, 0x1.c5bef522be6fbp-1}, /* 157 */
     {-0x1.5e2321ep-29, 0x1.dd28f054cbb3fp-2, 0x1.c5042052c8c42p-1}, /* 158 */
     {-0x1.a259ffep-29, 0x1.dfeff54854631p-2, 0x1.c44833611bc7dp-1}, /* 159 */
     {-0x1.4f28d8p-31, 0x1.e2b5d34665b35p-2, 0x1.c38b2f278ea7ep-1}, /* 160 */
     {-0x1.de571p-36, 0x1.e57a86d137f2p-2, 0x1.c2cd1493d05c2p-1}, /* 161 */
     {0x1.e0d8d14p-29, 0x1.e83e0ffb7bfb4p-2, 0x1.c20de3a08ea07p-1}, /* 162 */
     {-0x1.12a858ep-28, 0x1.eb0067e48baf4p-2, 0x1.c14d9e2bd511ep-1}, /* 163 */
     {0x1.9a17403p-27, 0x1.edc19997a4431p-2, 0x1.c08c413089b2ep-1}, /* 164 */
     {0x1.68c8636p-29, 0x1.f0819163d1bcp-2, 0x1.bfc9d21568f32p-1}, /* 165 */
     {0x1.4cc5eb8p-29, 0x1.f3405a482e11dp-2, 0x1.bf064dd580fc9p-1}, /* 166 */
     {-0x1.fce7cd8p-27, 0x1.f5fde8f3f11d4p-2, 0x1.be41b798f6b97p-1}, /* 167 */
     {-0x1.af8169p-29, 0x1.f8ba4c98a9816p-2, 0x1.bd7c0b1a7f14bp-1}, /* 168 */
     {0x1.6e39e2p-33, 0x1.fb7575d1ea75p-2, 0x1.bcb54cac5dde5p-1}, /* 169 */
     {0x1.30f9256p-28, 0x1.fe2f665dcd168p-2, 0x1.bbed7bd1e17bp-1}, /* 170 */
     {0x1.626de2p-31, 0x1.00740ca0d5fbbp-1, 0x1.bb2499f9fe7a3p-1}, /* 171 */
     {0x1.5cc703p-30, 0x1.01cfc8afeea0ep-1, 0x1.ba5aa650dd495p-1}, /* 172 */
     {-0x1.6191e6p-32, 0x1.032ae54fe4057p-1, 0x1.b98fa2065a5e6p-1}, /* 173 */
     {-0x1.6b1485p-31, 0x1.0485624c328c8p-1, 0x1.b8c38d39737bcp-1}, /* 174 */
     {-0x1.11fbc3ap-29, 0x1.05df3e66a716dp-1, 0x1.b7f668a580fdp-1}, /* 175 */
     {-0x1.0eca7fp-27, 0x1.07387825589ecp-1, 0x1.b728352c44517p-1}, /* 176 */
     {-0x1.8073bc9ep-25, 0x1.089109ef1284dp-1, 0x1.b658f630112edp-1}, /* 177 */
     {-0x1.9dcf0adp-27, 0x1.09e9051603e29p-1, 0x1.b588a13ab750fp-1}, /* 178 */
     {-0x1.06ea9fp-29, 0x1.0b405820e78e7p-1, 0x1.b4b740d3cc07bp-1}, /* 179 */
     {-0x1.36a8d0cp-30, 0x1.0c9704a1ea4e5p-1, 0x1.b3e4d40f5524dp-1}, /* 180 */
     {0x1.63d1f3p-30, 0x1.0ded0bc01a533p-1, 0x1.b3115a3a628afp-1}, /* 181 */
     {0x1.f3181f14p-26, 0x1.0f4270e4787bfp-1, 0x1.b23cd1314c779p-1}, /* 182 */
     {-0x1.f269b78p-29, 0x1.109723e75c5cfp-1, 0x1.b167430cfebdbp-1}, /* 183 */
     {0x1.1d84dc08p-27, 0x1.11eb36bc9db52p-1, 0x1.b090a4915ee88p-1}, /* 184 */
     {-0x1.08e60068p-27, 0x1.133e9ba0061d8p-1, 0x1.afb8fe69a6527p-1}, /* 185 */
     {0x1.cda72abp-27, 0x1.14915d557a7c9p-1, 0x1.aee049bc0aeep-1}, /* 186 */
     {-0x1.f32f95p-30, 0x1.15e36dfb6bb55p-1, 0x1.ae068f6991699p-1}, /* 187 */
     {0x1.138092dp-28, 0x1.1734d6f34d7fp-1, 0x1.ad2bc96c1e1f5p-1}, /* 188 */
     {0x1.6b382dd4p-26, 0x1.188595ae376a5p-1, 0x1.ac4ff962bdb6dp-1}, /* 189 */
     {-0x1.f12fafap-28, 0x1.19d59f592a587p-1, 0x1.ab7326685eb57p-1}, /* 190 */
     {-0x1.2909e5ap-28, 0x1.1b2500aed7ac6p-1, 0x1.aa954823cf815p-1}, /* 191 */
     {-0x1.d66a8978p-25, 0x1.1c73aa0150cf9p-1, 0x1.a9b668fb0503fp-1}, /* 192 */
     {0x1.311ea86p-27, 0x1.1dc1b7db74db1p-1, 0x1.a8d675d9c6cc8p-1}, /* 193 */
     {-0x1.41c02b8p-31, 0x1.1f0f08a1a06a4p-1, 0x1.a7f5853bb4309p-1}, /* 194 */
     {-0x1.ca1f4edp-26, 0x1.205ba57211271p-1, 0x1.a71391146958fp-1}, /* 195 */
     {-0x1.910ce77p-28, 0x1.21a7988f8326bp-1, 0x1.a63092626202fp-1}, /* 196 */
     {0x1.2bfadbeep-25, 0x1.22f2dc71afab6p-1, 0x1.a54c8cd9fd0d9p-1}, /* 197 */
     {-0x1.5f1c02a8p-27, 0x1.243d5df4afb93p-1, 0x1.a4678dbbe5e73p-1}, /* 198 */
     {-0x1.db12b9p-30, 0x1.2587347f493a4p-1, 0x1.a38184db0df23p-1}, /* 199 */
     {-0x1.7b29ep-30, 0x1.26d05490f2f61p-1, 0x1.a29a7a2f40b49p-1}, /* 200 */
     {-0x1.b3ddca4p-29, 0x1.2818be6930629p-1, 0x1.a1b26d8f070d7p-1}, /* 201 */
     {0x1.e112744p-29, 0x1.2960730ff2bcdp-1, 0x1.a0c95e3df5e0ep-1}, /* 202 */
     {-0x1.5269766p-28, 0x1.2aa76dafcbbf4p-1, 0x1.9fdf4fae1df6fp-1}, /* 203 */
     {-0x1.09777e1p-28, 0x1.2bedb1b6b4e15p-1, 0x1.9ef43f6cbe162p-1}, /* 204 */
     {0x1.ae2051fp-28, 0x1.2d333e4617f25p-1, 0x1.9e082e148680ep-1}, /* 205 */
     {-0x1.36f6ced8p-27, 0x1.2e780cb47180ep-1, 0x1.9d1b207f383c3p-1}, /* 206 */
     {-0x1.23fdc6bp-28, 0x1.2fbc23fba2f44p-1, 0x1.9c2d1197130a7p-1}, /* 207 */
     {0x1.bc540ep-33, 0x1.30ff7fd6d967dp-1, 0x1.9b3e0478b961bp-1}, /* 208 */
     {-0x1.cfb4ed7p-28, 0x1.32421da0bf0e9p-1, 0x1.9a4dfb1c89326p-1}, /* 209 */
     {0x1.55802aecp-26, 0x1.3384042a92b1dp-1, 0x1.995cf06920d11p-1}, /* 210 */
     {0x1.60719e4p-28, 0x1.34c52608e3a92p-1, 0x1.986aee6d6837ep-1}, /* 211 */
     {-0x1.cbf2e48p-30, 0x1.36058ac8863b6p-1, 0x1.9777ef832c986p-1}, /* 212 */
     {0x1.9061c32p-27, 0x1.374533ab707dp-1, 0x1.9683f2ad7e2ecp-1}, /* 213 */
     {-0x1.da84dfep-27, 0x1.3884160f9488fp-1, 0x1.958f000fdd50ap-1}, /* 214 */
     {0x1.92e8a74p-29, 0x1.39c23eba6b22ap-1, 0x1.94990dd9cee51p-1}, /* 215 */
     {-0x1.bff5d9ap-29, 0x1.3affa20756bddp-1, 0x1.93a225056084ap-1}, /* 216 */
     {0x1.4c462p-36, 0x1.3c3c4498e98ebp-1, 0x1.92aa41fbb951cp-1}, /* 217 */
     {-0x1.e4613e9p-28, 0x1.3d782261dff62p-1, 0x1.91b167e92d706p-1}, /* 218 */
     {0x1.0eb2964p-30, 0x1.3eb33ed579bbep-1, 0x1.90b794146043cp-1}, /* 219 */
     {-0x1.60abec2p-29, 0x1.3fed94c834d8ap-1, 0x1.8fbcca9583479p-1}, /* 220 */
     {0x1.6954977p-27, 0x1.4127281ddac03p-1, 0x1.8ec1085083553p-1}, /* 221 */
     {0x1.a16fec2p-29, 0x1.425ff1f841235p-1, 0x1.8dc452ca328d3p-1}, /* 222 */
     {-0x1.27bcdd3p-27, 0x1.4397f44aa44f2p-1, 0x1.8cc6a8771e165p-1}, /* 223 */
     {-0x1.60dded4p-28, 0x1.44cf317a563dbp-1, 0x1.8bc8076122736p-1}, /* 224 */
     {-0x1.9a8f405cp-26, 0x1.4605a2b02d705p-1, 0x1.8ac875232f3efp-1}, /* 225 */
     {0x1.32777dcp-27, 0x1.473b532bc5a67p-1, 0x1.89c7e8713120cp-1}, /* 226 */
     {-0x1.1418a7bp-26, 0x1.4870306ca20e2p-1, 0x1.88c670a0ea774p-1}, /* 227 */
     {-0x1.fed182ep-28, 0x1.49a44886b534p-1, 0x1.87c401fdf05e5p-1}, /* 228 */
     {0x1.86144d8p-27, 0x1.4ad796ea1410cp-1, 0x1.86c0a04dbacc5p-1}, /* 229 */
     {0x1.1bc2e6p-33, 0x1.4c0a14640d2afp-1, 0x1.85bc51aa114c2p-1}, /* 230 */
     {-0x1.f53d2fep-28, 0x1.4d3bc5aaa8cd5p-1, 0x1.84b7121b30a13p-1}, /* 231 */
     {-0x1.2e100ap-30, 0x1.4e6cab91556bep-1, 0x1.83b0e0e6b6cccp-1}, /* 232 */
     {-0x1.fa58c62p-29, 0x1.4f9cc1c69fddep-1, 0x1.82a9c1c1ab463p-1}, /* 233 */
     {0x1.bb491ep-33, 0x1.50cc09fdcbd92p-1, 0x1.81a1b3342f858p-1}, /* 234 */
     {0x1.a11541p-28, 0x1.51fa82c3aa029p-1, 0x1.8098b67ea8509p-1}, /* 235 */
     {0x1.ab0a5d3p-27, 0x1.53282b20b96b6p-1, 0x1.7f8ecc791953p-1}, /* 236 */
     {-0x1.cba0438p-28, 0x1.5454fe43a7d7cp-1, 0x1.7e83f96af78ap-1}, /* 237 */
     {-0x1.0dd83a4p-29, 0x1.5581033a81573p-1, 0x1.7d783712e20ecp-1}, /* 238 */
     {-0x1.e9a8299p-28, 0x1.56ac33fbb8253p-1, 0x1.7c6b8acf90fa6p-1}, /* 239 */
     {0x1.225c4aap-29, 0x1.57d6939d4b513p-1, 0x1.7b5df1da18065p-1}, /* 240 */
     {-0x1.82e66ep-27, 0x1.59001b9e64d79p-1, 0x1.7a4f72157cfdfp-1}, /* 241 */
     {0x1.51a6a354p-26, 0x1.5a28d5b36d597p-1, 0x1.794002a7c9023p-1}, /* 242 */
     {0x1.13917f4p-26, 0x1.5b50b4e10bec1p-1, 0x1.782faf6dc7ba2p-1}, /* 243 */
     {0x1.49310ccp-30, 0x1.5c77bc15ab4efp-1, 0x1.771e75c43942ep-1}, /* 244 */
     {0x1.24d493cp-30, 0x1.5d9dee9de49dbp-1, 0x1.760c529bc17bp-1}, /* 245 */
     {-0x1.04638f7p-26, 0x1.5ec347044e0f4p-1, 0x1.74f94b0af972p-1}, /* 246 */
     {-0x1.3f41b28p-29, 0x1.5fe7cb834600cp-1, 0x1.73e55936a516p-1}, /* 247 */
     {-0x1.a5f6f5cp-30, 0x1.610b7515d1562p-1, 0x1.72d083b8214ebp-1}, /* 248 */
     {0x1.19fb2ep-28, 0x1.622e459eafbc1p-1, 0x1.71bac8c7b0592p-1}, /* 249 */
     {-0x1.56d2c2bp-28, 0x1.6350396fe4e62p-1, 0x1.70a42bec51665p-1}, /* 250 */
     {-0x1.3c156c2p-28, 0x1.64715385bed93p-1, 0x1.6f8caa4969708p-1}, /* 251 */
     {-0x1.f23e576p-29, 0x1.659191d2fd57fp-1, 0x1.6e7445d74f711p-1}, /* 252 */
     {0x1.1e4be38p-30, 0x1.66b0f41d484c4p-1, 0x1.6d5afecd4938dp-1}, /* 253 */
     {-0x1.397cc8d8p-27, 0x1.67cf76eac73dfp-1, 0x1.6c40d89625f63p-1}, /* 254 */
     {-0x1.202f686p-28, 0x1.68ed1e0990551p-1, 0x1.6b25cf728c35p-1}, /* 255 */
  };

  private static void aMul(double[] hi, double[] lo, double a, double b) {
    hi[0] = a * b;
    lo[0] = Math.fma(a, b, -hi[0]);
  }

  /* Multiply a double with a double double : a * (bh + bl)
     with error bounded by ulp(lo) */
  private static void sMul(double[] hi, double[] lo, double a, double bh,
                           double bl) {
    aMul(hi, lo, a, bh); /* exact */
    lo[0] = Math.fma(a, bl, lo[0]);
    /* the error is bounded by ulp(lo), where |lo| < |a*bl| + ulp(hi) */
  }

  // Returns (ah + al) * (bh + bl) - (al * bl)
  // We can ignore al * bl when assuming al <= ulp(ah) and bl <= ulp(bh)
  private static void dMul(double[] hi, double[] lo, double ah, double al,
                           double bh, double bl) {
    double t;
    double[] s = new double[1];

    aMul(hi, s, ah, bh);
    t = Math.fma(al, bh, s[0]);
    lo[0] = Math.fma(ah, bl, t);
  }

  private static void fastTwoSum(double[] hi, double[] lo, double a, double b) {
    double e;

    hi[0] = a + b;
    e = hi[0] - a; /* exact */
    lo[0] = b - e; /* exact */
  }

  /* Put in h+l an approximation of sin2pi(xh+xl),
     for -2^-24 <= xh+xl < 2^-11 + 2^-24,
     and |xl| < 2^-52*|xh|, with relative error < 2^-71.61
     (see evalPSfast_all(K=8) in tan.sage).
     Assume uh + ul approximates (xh+xl)^2. */
  private static void evalPSfast(double[] h, double[] l, double xh, double xl, double uh,
                                 double ul) {
    double[] t = new double[1];
    h[0] = PSfast[4]; // degree 7
    h[0] = Math.fma(h[0], uh, PSfast[3]); // degree 5
    h[0] = Math.fma(h[0], uh, PSfast[2]); // degree 3
    sMul(h, l, h[0], uh, ul);
    fastTwoSum(h, t, PSfast[0], h[0]);
    l[0] += PSfast[1] + t[0];
    // multiply by xh+xl
    dMul(h, l, h[0], l[0], xh, xl);
  }

  /* Put in h+l an approximation of cos2pi(xh+xl),
     for -2^-24 <= xh+xl < 2^-11 + 2^-24,
     and |xl| < 2^-52.36, with relative error < 2^-69.96
     (see evalPCfast(rel=true) in tan.sage).
     Assume uh + ul approximates (xh+xl)^2. */
  private static void evalPCfast(double[] h, double[] l, double uh, double ul) {
    double[] t = new double[1];
    h[0] = PCfast[4]; // degree 6
    h[0] = Math.fma(h[0], uh, PCfast[3]); // degree 4
    h[0] = Math.fma(h[0], uh, PCfast[2]); // degree 2
    sMul(h, l, h[0], uh, ul);
    fastTwoSum(h, t, PCfast[0], h[0]);
    l[0] += PCfast[1] + t[0];
  }

  /* Put in Y an approximation of sin2pi(X), for 0 <= X < 2^-11,
     where X2 approximates X^2.
     Absolute error bounded by 2^-132.999 with 0 <= Y < 0.003068
     (see evalPS() in sin.sage), and relative error bounded by
     2^-124.648 (see evalPSrel(K=8) in sin.sage). */
  private static void evalPS(Dint64 Y, Dint64 X, Dint64 X2) {
    mulDint21(Y, X2, PS[5]); // degree 11
    addDint(Y, Y, PS[4]);    // degree 9
    mulDint(Y, Y, X2);
    addDint(Y, Y, PS[3]);    // degree 7
    mulDint(Y, Y, X2);
    addDint(Y, Y, PS[2]);    // degree 5
    mulDint(Y, Y, X2);
    addDint(Y, Y, PS[1]);    // degree 3
    mulDint(Y, Y, X2);
    addDint(Y, Y, PS[0]);    // degree 1
    mulDint(Y, Y, X);        // multiply by X
  }

  /* Put in Y an approximation of cos2pi(X), for 0 <= X < 2^-11,
     where X2 approximates X^2.
     Absolute/relative error bounded by 2^-125.999 with 0.999995 < Y <= 1
     (see evalPC() in sin.sage). */
  private static void evalPC(Dint64 Y, Dint64 X2) {
    mulDint21(Y, X2, PC[5]); // degree 10
    addDint(Y, Y, PC[4]);    // degree 8
    mulDint(Y, Y, X2);
    addDint(Y, Y, PC[3]);    // degree 6
    mulDint(Y, Y, X2);
    addDint(Y, Y, PC[2]);    // degree 4
    mulDint(Y, Y, X2);
    addDint(Y, Y, PC[1]);    // degree 2
    mulDint(Y, Y, X2);
    addDint(Y, Y, PC[0]);    // degree 0
  }

  // normalize X such that X->hi has its most significant bit set (if X <> 0)
  private static void normalize(Dint64 X) {
    int cnt;
    if (X.hi != 0) {
      cnt = Long.numberOfLeadingZeros(X.hi);
      if (cnt != 0) {
        X.hi = (X.hi << cnt) | (X.lo >>> (64 - cnt));
        X.lo = X.lo << cnt;
      }
      X.ex -= cnt;
    } else if (X.lo != 0) {
      cnt = Long.numberOfLeadingZeros(X.lo);
      X.hi = X.lo << cnt;
      X.lo = 0;
      X.ex -= 64 + cnt;
    }
  }

  /* Approximate X/(2pi) mod 1. If Xin is the input value, and Xout the
     output value, we have:
     |Xout - (Xin/(2pi) mod 1)| < 2^-126.67*|Xout|
     Assert X is normalized at input, and normalize X at output.
  */
  private static void reduce(Dint64 X) {
    int e = (int)X.ex;
    U128 u;

    if (e <= 1) { // |X| < 2
      /* multiply by T[0]/2^64 + T[1]/2^128, where
         |T[0]/2^64 + T[1]/2^128 - 1/(2pi)| < 2^-130.22 */
      u = U128.mul(X.hi, T[1]);
      long tiny = u.lo;
      X.lo = u.hi;
      u = U128.mul(X.hi, T[0]);
      X.lo += u.lo;
      X.hi = u.hi + (Long.compareUnsigned(X.lo, u.lo) < 0 ? 1 : 0);
      /* hi + lo/2^64 + tiny/2^128 = hi_in * (T[0]/2^64 + T[1]/2^128) thus
         |hi + lo/2^64 + tiny/2^128 - hi_in/(2*pi)| < hi_in * 2^-130.22
         Since X is normalized at input, hi_in >= 2^63, and since T[0] >= 2^61,
         we have hi >= 2^(63+61-64) = 2^60, thus the normalize() below
         perform a left shift by at most 3 bits */
      e = (int)X.ex;
      normalize(X);
      e = e - (int)X.ex;
      // put the upper e bits of tiny into X->lo
      if (e != 0)
        X.lo |= tiny >>> (64 - e);
      /* The error is bounded by 2^-130.22 (relative) + ulp(lo) (absolute).
         Since now X->hi >= 2^63, the absolute error of ulp(lo) converts into
         a relative error of less than 2^-127.
         This yields a maximal relative error of:
         (1 + 2^-130.22) * (1 + 2^-127) - 1 < 2^-126.852.
      */
      return;
    }

    // now 2 <= e <= 1024

    /* The upper 64-bit word X->hi corresponds to hi/2^64*2^e, if multiplied by
       T[i]/2^((i+1)*64) it yields hi*T[i]/2^128 * 2^(e-i*64).
       If e-64i <= -128, it contributes to less than 2^-128;
       if e-64i >= 128, it yields an integer, which is 0 modulo 1.
       We thus only consider the values of i such that -127 <= e-64i <= 127,
       i.e., (-127+e)/64 <= i <= (127+e)/64.
       Up to 4 consecutive values of T[i] can contribute (only 3 when e is a
       multiple of 64). */
    int i = (e < 127) ? 0 : (e - 127 + 64 - 1) / 64; // ceil((e-127)/64)
    // 0 <= i <= 15
    long[] c = new long[5];
    u = U128.mul(X.hi, T[i+3]); // i+3 <= 18
    c[0] = u.lo;
    c[1] = u.hi;
    u = U128.mul(X.hi, T[i+2]);
    c[1] += u.lo;
    c[2] = u.hi + (Long.compareUnsigned(c[1], u.lo) < 0 ? 1 : 0);
    u = U128.mul(X.hi, T[i+1]);
    c[2] += u.lo;
    c[3] = u.hi + (Long.compareUnsigned(c[2], u.lo) < 0 ? 1 : 0);
    u = U128.mul(X.hi, T[i]);
    c[3] += u.lo;
    c[4] = u.hi + (Long.compareUnsigned(c[3], u.lo) < 0 ? 1 : 0);

    /* up to here, the ignored part hi*(T[i+4]+T[i+5]+...) can contribute by
       less than 2^64 in c[0], thus less than 1 in c[1] */

    int f = e - 64 * i; // hi*T[i]/2^128 is multiplied by 2^f
    /* {c, 5} = hi*(T[i]+T[i+1]/2^64+T[i+2]/2^128+T[i+3]/2^192) */
    /* now shift c[0..4] by f bits to the left */
    long tiny;
    if (f < 64) {
      X.hi = (c[4] << f) | (c[3] >>> (64 - f));
      X.lo = (c[3] << f) | (c[2] >>> (64 - f));
      tiny = (c[2] << f) | (c[1] >>> (64 - f));
      /* the ignored part was less than 1 in c[1],
         thus less than 2^(f-64) <= 1/2 in tiny */
    } else if (f == 64) {
      X.hi = c[3];
      X.lo = c[2];
      tiny = c[1];
      /* the ignored part was less than 1 in c[1],
         thus less than 1 in tiny */
    } else /* 65 <= f <= 127: this case can only occur when e >= 65 */ {
      int g = f - 64; /* 1 <= g <= 63 */
      /* we compute an extra term */
      u = U128.mul(X.hi, T[i+4]); // i+4 <= 19
      u = u.shr(64);
      c[0] += u.lo;
      c[1] += Long.compareUnsigned(c[0], u.lo) < 0 ? 1 : 0;
      c[2] += (Long.compareUnsigned(c[0], u.lo) < 0 && c[1] == 0) ? 1 : 0;
      c[3] += (Long.compareUnsigned(c[0], u.lo) < 0 && c[1] == 0 && c[2] == 0) ? 1 : 0;
      c[4] += (Long.compareUnsigned(c[0], u.lo) < 0 && c[1] == 0 && c[2] == 0
               && c[3] == 0) ? 1 : 0;
      X.hi = (c[3] << g) | (c[2] >>> (64 - g));
      X.lo = (c[2] << g) | (c[1] >>> (64 - g));
      tiny = (c[1] << g) | (c[0] >>> (64 - g));
      /* the ignored part was less than 1 in c[0],
         thus less than 1/2 in tiny */
    }
    /* The approximation error between X/in(2pi) mod 1 and
       X->hi/2^64 + X->lo/2^128 + tiny/2^192 is:
       (a) the ignored part in tiny, which is less than ulp(tiny),
           thus less than 1/2^192;
       (b) the ignored terms hi*T[i+4] + ... or hi*T[i+5] + ...,
           which accumulate to less than ulp(tiny) too, thus
           less than 1/2^192.
       Thus the approximation error is less than 2^-191 (absolute).
    */
    X.ex = 0;
    normalize(X);
    /* the worst case (for 2^25 <= x < 2^1024) is X->ex = -61, attained
       for |x| = 0x1.6ac5b262ca1ffp+851 */
    if (X.ex < 0) // put the upper -ex bits of tiny into low bits of lo
      X.lo |= tiny >>> (int)(64 + X.ex);
    /* Since X->ex >= -61, it means X >= 2^-62 before the normalization,
       thus the maximal absolute error of 2^-191 yields a relative error
       bounded by 2^-191/2^-62 = 2^-129.
       There is an additional truncation error (for tiny) of at most 1 ulp
       of X->lo, thus at most 2^-127.
       The relative error is thus bounded by 2^-126.67. */
  }

  /* Given Xin:=X with 0 <= Xin < 1, return i and modify X such that
     Xin = i/2^11 + Xout, with 0 <= Xout < 2^-11.
     This operation is exact. */
  private static int reduce2(Dint64 X) {
    if (X.ex <= -11)
      return 0;
    int sh = (int)(64 - 11 - X.ex);
    int i = (int)(X.hi >>> sh);
    X.hi = X.hi&((1L << sh) - 1);
    normalize(X);
    return i;
  }

  /* h+l <- c1/2^64 + c0/2^128, rounded towards zero */
  private static void setDd(double[] h, double[] l, long c1, long c0) {
    long e, f, g;
    if (c1 != 0) {
      e = Long.numberOfLeadingZeros(c1);
      if (e != 0) {
        c1 = (c1 << e) | (c0 >>> (64 - e));
        c0 = c0 << e;
      }
      f = 0x3fe - e;
      h[0] = Double.longBitsToDouble((f << 52) | ((c1 << 1) >>> 12));
      c0 = (c1 << 53) | (c0 >>> 11);
      if (c0 != 0) {
        g = Long.numberOfLeadingZeros(c0);
        if (g != 0)
          c0 = c0 << g;
        l[0] = Double.longBitsToDouble(((f - 53 - g) << 52) | ((c0 << 1) >>> 12));
      } else
        l[0] = 0;
    } else if (c0 != 0) {
      e = Long.numberOfLeadingZeros(c0);
      f = 0x3fe - 64 - e;
      c0 = c0 << (e+1); // most significant bit shifted out
      /* put the upper 52 bits of c0 into h */
      h[0] = Double.longBitsToDouble((f << 52) | (c0 >>> 12));
      /* put the lower 12 bits of c0 into l */
      c0 = c0 << 52;
      if (c0 != 0) {
        g = Long.numberOfLeadingZeros(c0);
        c0 = c0 << (g+1);
        l[0] = Double.longBitsToDouble(((f - 64 - g) << 52) | (c0 >>> 12));
      } else
        l[0] = 0;
    } else
      h[0] = l[0] = 0;
    /* Since we truncate from two 64-bit words to a double-double,
       we have another truncation error of less than 2^-106, thus
       the absolute error is bounded as follows:
       | h + l - frac(x/(2pi)) | < 2^-75.999 + 2^-106 < 2^-75.998 */
  }

  // #define CH and CL inside reduce_fast() in the C source
  // | CH+CL - 1/(2pi) | < 2^-110.523
  private static final double CH = 0x1.45f306dc9c883p-3;
  private static final double CL = -0x1.6b01ec5417056p-57;

  /* Assuming 0x1.7137449123ef6p-26 < x < +Inf,
     return i and set h,l such that i/2^11+h+l approximates frac(x/(2pi)):
     if x <= 0x1.921fb54442d18p+2:
     | i/2^11 + h + l - frac(x/(2pi)) | < 2^-104.815,
     with |h| < 2^-11 and |l| < 2^-52.36.

     Otherwise only the absolute error is bounded:

     | i/2^11 + h + l - frac(x/(2pi)) | < 2^-105.999,
     with 0 <= h < 2^-11 and |l| < 2^-53.
  */
  private static int reduceFast(double[] h, double[] l, double x) {
    if (x <= 0x1.921fb54442d17p+2) { // x < 2*pi
      /* | CH+CL - 1/(2pi) | < 2^-110.523 */
      aMul(h, l, CH, x);            // exact
      l[0] = Math.fma(CL, x, l[0]);
      /* The error in the above fma() is at most ulp(l),
         where |l| <= CL*|x|+|l_in|.
         Let xmax = 0x1.921fb54442d17p+2.
         We have x <= xmax and we can check that CH * xmax < 1.
         Then h <= 1 and |l_in| <= ulp(0.5) = 2^-53,
         where l_in is the value of l after a_mul.
         Then |l| <= |CL|*xmax + 2^-53 < 2^-52.361.
         The rounding error of the fma() is bounded by
         ulp(l) <= ulp(2^-52.361) = 2^-105.
         The error due to the approximation of 1/(2pi)
         is bounded by 2^-110.523*xmax <= 2^-107.871.
         Adding both errors yields:
         |h + l - x/(2pi)| < 2^-105 + 2^-107.871 < 2^-104.815.
      */
    } else { // x > 0x1.921fb54442d17p+2
      long t = Double.doubleToRawLongBits(x);
      int e = (int)((t >>> 52)&0x7ff); /* 1025 <= e <= 2046 */
      /* We have 2^(e-1023) <= x < 2^(e-1022), thus
         ulp(x) is a multiple of 2^(e-1075), for example
         if x is just above 2*pi, e=1025, 2^2 <= x < 2^3,
         and ulp(x) is a multiple of 2^-50.
         On the other side 1/(2pi) ~ T[0]/2^64 + T[1]/2^128 + T[2]/2^192 + ...
         Let i be the smallest integer such that 2^(e-1075)/2^(64*(i+1))
         is not an integer, i.e., e - 1139 - 64i < 0, i.e.,
         i >= (e-1138)/64. */
      long m = (1L << 52) | (t&0xfffffffffffffL);
      long[] c = new long[3];
      U128 u, v;
      // x = m/2^53 * 2^(e-1022)
      if (e <= 1074) { // 1025 <= e <= 1074: 2^2 <= x < 2^52
        v = U128.mul(m, T[2]);
        u = U128.mul(m, T[1]);
        c[0] = u.lo + v.hi;
        c[1] = u.hi + (Long.compareUnsigned(c[0], u.lo) < 0 ? 1 : 0);
        /* There can be no overflow in (u >> 64) + (c[0] < u) since
           u <= (2^64-1)*T[1] thus (u >> 64) < T[1], and T[1]+1
           does not overflow. */
        u = U128.mul(m, T[0]);
        c[1] += u.lo;
        c[2] = u.hi + (Long.compareUnsigned(c[1], u.lo) < 0 ? 1 : 0);
        /* Up to here, we multiplied exactly m by T[0] and T[1] and
           took into account the upper part of m*T[2]:
           c[2]*2^128+c[1]*2^64+c[0] = m*(T[1]+2^64*T[0])+floor(m*T[2]/2^64)
           |c[2]*2^128+c[1]*2^64+c[0]-m/(2pi)*2^128|
              < frac(m*T[2]/2^64) + (T[3]+1)/2^128
              < (2^64-1)/2^64 + (2^64-1)/2^128 < 1
           thus:
           | (c[2]*2^128+c[1]*2^64+c[0])*2^(e-1203) - x/(2pi) | < 2^(e-1203)
           | c[2]*2^(e-1075)+c[1]*2^(e-1139)+c[0]*2^(e-1203) - x/(2pi) |
             < 2^(e-1203)
           The low 1075-e bits of c[2] contribute to frac(x/(2pi)),
           and the ignored part is bounded by 1 with respect to c[0]. */
        e = 1075 - e; // 1 <= e <= 50
        // e is the number of low bits of c[2] contributing to frac(x/(2pi))
      } else { // 1075 <= e <= 2046, 2^52 <= x < 2^1024
        int i = (e - 1138 + 63) / 64; // i = ceil((e-1138)/64), 0 <= i <= 15
        /* m*T[i] contributes to f = 1139 + 64*i - e bits to frac(x/(2pi))
           with 1 <= f <= 64
           m*T[i+1] contributes a multiple of 2^(-f-64),
                    and at most to 2^(53-f)
           m*T[i+2] contributes a multiple of 2^(-f-128),
                    and at most to 2^(-11-f)
           m*T[i+3] contributes a multiple of 2^(-f-192),
                    and at most to 2^(-75-f) <= 2^-76
        */
        v = U128.mul(m, T[i+3]);
        u = U128.mul(m, T[i+2]);
        c[0] = u.lo + v.hi;
        /* There can be no overflow in (u >> 64) + (c[0] < u) since
           u <= (2^64-1)*T[i+2] thus (u >> 64) < T[i+2], and T[i+2]+1
           does not overflow. */
        c[1] = u.hi + (Long.compareUnsigned(c[0], u.lo) < 0 ? 1 : 0);
        u = U128.mul(m, T[i+1]);
        c[1] += u.lo;
        c[2] = u.hi + (Long.compareUnsigned(c[1], u.lo) < 0 ? 1 : 0);
        u = U128.mul(m, T[i]);
        c[2] += u.lo;
        e = 1139 + (i<<6) - e; // 1 <= e <= 64
        /* Like in the previous case, the ignored part due to the
           ignored low part of m*T[i+3], and to the further terms
           m*T[i+4], ..., is at most 1 relative to c[0]. */
      }
      // e is the number of bits of c[2] to ignore
      if (e == 64) {
        /* we ignore all bits of c[0]: the ignored part with respect to
           the new value of c[0] is (2^64-1)/2^64 + 1/2^64 < 1 */
        c[0] = c[1];
        c[1] = c[2];
      } else {
        /* we ignore the low bits of c[0]: the ignored part with respect to
           the new value of c[0] is (2^64-1)/2^64 + 1/2^64 < 1 */
        c[0] = (c[1] << (64 - e)) | c[0] >>> e;
        c[1] = (c[2] << (64 - e)) | c[1] >>> e;
      }
      /* In all cases the ignored contribution from low(x*T[2]) and
         x*(T[i+3] + T[i+4] + ...) is less than 1 with respect to c[0],
         thus since we consider (c[1]*2^64+c[0])/2^128, it is < 2^-128. */
      setDd(h, l, c[1], c[0]);
      /* set_dd() ensures |h| < 1 and |l| < ulp(h) <= 2^-53,
         with truncation error < 2^-106, thus the absolute error is
         bounded by 2^-106 + 2^-128 < 2^-105.999:
         |h + l - fracx/(2pi)| < 2^-105.999. */
    }

    /* In case x < 2pi we have:
       |h + l - x/(2pi)| < 2^-104.815,
    and in case 2pi < x we have:
    |h + l - frac(x/(2pi))| < 2^-105.999,
    thus in all cases:
    |h + l - frac(x/(2pi))| < 2^-104.815.

    Since we exclude |h+l| < 2^-37, the induced error
    relative to sin2pi(h+l) is bounded by
    2^-104.815/sin2pi(2^-37) < 2^-70.466. */

    double i = Math.floor(h[0] * 0x1p11);
    h[0] = Math.fma(i, -0x1p-11, h[0]);
    return (int)i;
  }

  /* h + l <- (bh + bl) / (ah + al) with |bl|, |al| < 2^-49.47
     with relative error bounded by 2^-96.99:
     | h + l - (bh + bl) / (ah + al) | < 2^-96.99 * |h + l|. */
  private static void fastDiv(double[] h, double[] l, double bh, double bl, double ah, double al) {
    /* We use here Karp-Markstein's trick for division:
       let b = bh+bl, a = ah+al, y = o(1/a), and z = o(b*y),
       then the approximation of b/a is z' = z + y*(b-a*z):
       b-a*z' = b-a*(z + y*(b-a*z)) = (b-a*z)*(1-a*y).
       We distinguish two errors:
       * the mathematical error, assuming z + y*(b-a*z) is computed exactly
         (but taking into account that y is not exactly 1/a, and z is not
         exactly b/a
       * the rounding errors in z + y*(b-a*z)
       For the error analysis, we assume 1 <= ah, bh < 2 for now.
    */

    double y = 1.0 / ah;
    /* y = 1/ah / (1 + eps1) with |eps1| < 2^-52.
       |1-ah*y| < |eps1| < 2^-52. */
    h[0] = bh * y;
    /* h = bh * y / (1 + eps2) with |eps2| < 2^-52
         = bh/ah / (1 + eps1) / (1 + eps2)
       thus writing z = h:
       bh = ah*z * (1 + eps1) * (1 + eps2)
       |bh - ah*z| < ah*z * (eps1 + eps2 + eps1*eps2)
       Since ah < 2 and z <= bh*y < 2, we have:
       |bh - ah*z| < 4 * (2*2^-52 + 2^-104) < 2^-48.999.
       It follows |bh-ah*z'| < (bh-ah*z)*(1-ah*y) < 2^-48.999*2^-52 < 2^-100.999
       Dividing by ah>=1 yields: |bh/ah-z'| < 2^-100.999 too.
       We assume the same bound hold for b,a: |b/a-z'| < 2^-100.999.
    */

    double eh = Math.fma(ah, -h[0], bh);
    /* from the analysis above, we have |eh| < 2^-48.999 thus the rounding error
       is bounded by ulp(2^-48.999) = 2^-101 */
    double el = Math.fma(al, -h[0], bl);
    /* here |al|, |bl| < 2^-49.47 and |h| < 2, thus |el| < 3*2^-49.47 and
       the rounding error is bounded by ulp(3*2^-49.47) = 2^-100. */
    l[0] = y * (eh + el);
    /* we have |eh+el| < 2^-48.999+3*2^-49.47 < 2^-47.33 thus the rounding error
       on eh+el is bounded by ulp(2^-47.33) = 2^-100.
       Then since y <= 1, the rounding error on l is bounded by 2^-100 too.
       The total rounding error is thus bounded by:
       2^-101+3*2^-100 < 2^-98.19.
       Adding the mathematical error yields:
       2^-100.999 + 2^-98.19 < 2^-97.99:
       | h + l - (bh + bl) / (ah + al) | < 2^-97.99.

       This was assuming 1 <= a,b < 2, thus with 1/2 <= h+l <= 2.
       For the relative error, this corresponds to 2^-97.99/(1/2) = 2^-96.99
       where 1/2 is the smallest possible value of h+l. */
  }

  // static const double sgn[2] inside tan_fast() in the C source
  private static final double[] sgn = {1.0, -1.0};

  /* given a finite input x with |x| > 0x1.d12ed0af1a27ep-27,
     put in h + l an approximation of tan(x),
     return the maximal absolute error err such that
     | h + l - tan(x) | < err */
  private static double tanFast(double[] h, double[] l, double x) {
    int neg = x < 0 ? 1 : 0;
    boolean is_tan = true;
    double absx = neg != 0 ? -x : x;

    /* now absx > 0x1.d12ed0af1a27ep-27 */
    int i = reduceFast(h, l, absx);
    /* | i/2^11 + h + l - frac(x/(2pi)) | < 2^-104.815 */

    // if i >= 2^10: 1/2 <= frac(x/(2pi)) < 1 thus pi <= x <= 2pi
    // we use tan(pi+x) = tan(x)
    i = i&0x3ff;
    // | i/2^11 + h + l - frac(x/(2pi)) | mod 1/2 < 2^-104.815

    // now i < 2^10
    // if i >= 2^9: 1/4 <= frac(x/(2pi)) < 1/2 thus pi/2 <= x <= pi
    // we use tan(pi/2+x) = -cot(x)
    is_tan = is_tan ^ ((i >> 9) != 0);
    neg = neg ^ (i >> 9);
    i = i&0x1ff;
    // | i/2^11 + h + l - frac(x/(2pi)) | mod 1/4 < 2^-104.815

    // now 0 <= i < 2^9
    // if i >= 2^8: 1/8 <= frac(x/(2pi)) < 1/4
    // we use tan(pi/2-x) = cot(x)
    if ((i&0x100) != 0) // case pi/4 <= x_red <= pi/2
      {
        is_tan = !is_tan;
        i = 0x1ff - i;
        /* 0x1p-11 - h is exact below: indeed, reduce_fast first computes
           a first value of h (say h0, with 0 <= h0 < 1), then i = floor(h0*2^11)
           and h1 = h0 - 2^11*i with 0 <= h1 < 2^-11.
           If i >= 2^8 here, this implies h0 >= 1/2^3, thus ulp(h0) >= 2^-55:
           h0 and h1 are integer multiples of 2^-55.
           Thus h1 = k*2^-55 with 0 <= k < 2^44 (since 0 <= h1 < 2^-11).
           Then 0x1p-11 - h = (2^44-k)*2^-55 is exactly representable.
           We can have a huge cancellation in 0x1p-11 - h, for example for
           x = 0x1.61a3db8c8d129p+1023 where we have before this operation
           h = 0x1.ffffffffff8p-12, and h = 0x1p-53 afterwards. */
        h[0] = 0x1p-11 - h[0];
        l[0] = -l[0];
      }

    /* The analysis of reduce_fast() proves that if we except i=0 and h<2^-37,
       then the error of reduce_fast() relative to both sin2pi(R) and cos2pi(R)
       is bounded by 2^-70.466:
       | R - frac(x/(2pi)) mod 1/4 | < 2^-70.466 * |sin2pi(R)|
       | R - frac(x/(2pi)) mod 1/4 | < 2^-70.466 * |cos2pi(R)|
       where R = i/2^11 + h + l, thus since the derivative of sin2pi and
       cos2pi is bounded by 2*pi and 2*pi*2^-70.466 < 2^-67.814:
       | sin2pi(R) - sin(x mod pi/2) | < 2^-67.814 * |sin2pi(R)|.
       | cos2pi(R) - cos(x mod pi/2) | < 2^-67.814 * |cos2pi(R)|.
       For i=0 and h<2^-37, we defer to the slow path. */
    if (i == 0 && h[0] < 0x1p-37)
      return 0x1p0;

    /* Now 0 <= i < 256 and 0 <= h+l < 2^-11
       with | i/2^11 + h + l - frac(x/(2pi)) | cmod 1/4 < 2^-104.815
       If is_tan=1, tan |x| = tan2pi (R + err1) with |err1| < 2^-104.815,
       if is_tan=0, tan |x| = cot2pi (R + err1) with |err1| < 2^-104.815.
       In both cases R = i/2^11 + h + l, 0 <= R < 1/4.
    */
    double[] sh = new double[1], sl = new double[1];
    double[] ch = new double[1], cl = new double[1];
    /* since the SC[] table evaluates at i/2^11 + SC[i][0] and not at i/2^11,
       we must subtract SC[i][0] from h+l */
    /* Here h = k*2^-55 with 0 <= k < 2^44, and SC[i][0] is an integer
       multiple of 2^-62, with |SC[i][0]| < 2^-24, thus SC[i][0] = m*2^-62
       with |m| < 2^38. It follows h-SC[i][0] = (k*2^7 + m)*2^-62 with
       2^51 - 2^38 < k*2^7 + m < 2^51 + 2^38, thus h-SC[i][0] is exact. */
    h[0] -= SC[i][0];
    /* the following fast_two_sum() guarantees that |l| <= ulp(h) thus
       |l| <= 2^-52 |h| at input of evalPSfast() and evalPCfast() */
    fastTwoSum(h, l, h[0], l[0]);
    // now -2^-24 < h < 2^-11+2^-24
    // from comments in reduce_fast() we have |l| < 2^-52.36
    double[] uh = new double[1], ul = new double[1];
    aMul(uh, ul, h[0], h[0]);
    ul[0] = Math.fma(h[0] + h[0], l[0], ul[0]);
    // uh+ul approximates (h+l)^2
    evalPSfast(sh, sl, h[0], l[0], uh[0], ul[0]);
    /* the relative error of evalPSfast() is less than 2^-71.61 from
       routine evalPSfast_all(K=8) in tan.sage:
       | sh + sh - sin2pi(h+l) | < 2^-71.61 * |sin2pi(h+l)| */
    evalPCfast(ch, cl, uh[0], ul[0]);
    /* the relative error of evalPCfast() is less than 2^-69.96 from
       routine evalPCfast() in sin.sage:
       | ch + cl - cos2pi(h+l) | < 2^-69.96 * |cos2pi(h+l)| */

    double[] sh0 = new double[1], sl0 = new double[1];
    double[] ch0 = new double[1], cl0 = new double[1];
    double[] h1 = new double[1], l1 = new double[1];
    sMul(sh0, sl0, SC[i][2], sh[0], sl[0]);
    sMul(ch0, cl0, SC[i][1], ch[0], cl[0]);
    fastTwoSum(h1, l1, ch0[0], sh0[0]);
    l1[0] += sl0[0] + cl0[0];
    /* relative error bounded by 2^-67.777
       from global_error(is_sin=true,rel=true) in tan.sage:
       | h1 + l1 - sin2pi (R) | < 2^-67.777 * |sin2pi(R)|
       with in addition |l1| < 2^-49.47 */

    double[] h2 = new double[1], l2 = new double[1];
    sMul(ch, cl, SC[i][2], ch[0], cl[0]);
    sMul(sh, sl, SC[i][1], sh[0], sl[0]);
    fastTwoSum(h2, l2, ch[0], -sh[0]);
    l2[0] += cl[0] - sl[0];
    /* relative error bounded by 2^-68.073
       from global_error(is_sin=false,rel=true) in sin.sage:
       | h2 + l2 - cos2pi (R) | < 2^-68.073 * |cos2pi(R)|
       (where cos|x| has to be replaced by sin|x| for is_tan=0)
       with in addition |l2| < 2^-49.62 */

    /* here we have |l1|, |l1| < 2^-49.47 */
    if (is_tan)
      fastDiv(h, l, h1[0], l1[0], h2[0], l2[0]);
      /* |h_out+l_out - (h_in+l_in)/(hh+ll)| < 2^-96.99 * |h_out+l_out| */
    else
      fastDiv(h, l, h2[0], l2[0], h1[0], l1[0]);

    /* In summary we have when is_tan=1:
       h1+l1 = sin2pi(R) * (1 + eps1) with |eps1| < 2^-67.777
       h2+l2 = cos2pi(R) * (1 + eps2) with |eps2| < 2^-68.073
       sin2pi(R) = sin(x mod pi/2) * (1 + eps3) with |eps3| < 2^-67.814
       cos2pi(R) = cos(x mod pi/2) * (1 + eps4) with |eps4| < 2^-67.814
       h+l = (h1+l1)/(h2+l2) * (1 + eps5) with |eps5| < 2^-96.99
       This yields:
       h+l = tan(x mod pi/2) * (1+eps1)*(1+eps3)*(1+eps5)/(1+eps2)/(1+eps4)
       The largest value is obtained when eps1,eps3,eps5 are maximum, and
       eps2,eps4 minimum, and we get:
       h+l = tan(x mod pi/2) * (1+eps) with |eps| < 2^-65.864
       (the same bound holds for eps1,eps3,eps5 minimum and eps2,eps4 maximum).
       When is_tan=0, we get the same reasoning with inverse ratio, but the
       bounds are the same.
    */

    h[0] *= sgn[neg];
    l[0] *= sgn[neg];
    return h[0] * 0x1.1ap-66; // 2^-65.864 < 0x1.1ap-66
  }

  // static const double exceptions[2][3] inside tan_accurate() in the C source
  private static final double[][] exceptions = {
    /* the following has 78 identical bits after the round bit */
    {0x1.dffffffffff1fp-22, 0x1.e000000000151p-22, 0x1.fffffffffffffp-76},
    /* the following has 72 identical bits after the round bit */
    {0x1.dfffffffffc7cp-21, 0x1.e000000000546p-21, -0x1.658bcedb6e1d4p-147},
  };

  /* Assume x is a regular number, and |x| > 0x1.d12ed0af1a27ep-27. */
  private static double tanAccurate(double x) {
    double absx = (x > 0) ? x : -x;

    Dint64 X = new Dint64();
    dintFromd(X, absx);

    /* reduce argument */
    reduce(X);

    // now |X - x/(2pi) mod 1| < 2^-126.67*X, with 0 <= X < 1.

    boolean is_tan = true;
    boolean neg = x < 0;

    // Write X = i/2^11 + r with 0 <= r < 2^11.
    int i = reduce2(X); // exact

    i = i&0x3ff; // if pi <= x < 2*pi, tan(x) = tan(x-pi)

    // now i < 2^10

    if ((i&0x200) != 0) // pi/2 <= x < pi: tan(x) = -1/tan(x-pi/2)
    {
      is_tan = false;
      neg = !neg;
      i = i&0x1ff;
    }

    // now 0 <= i < 2^9

    if ((i&0x100) != 0)
      // pi/4 <= x < pi/2: tan(x) = 1/tan(pi/2-x)
    {
      is_tan = !is_tan;
      X.sgn = 1; // negate X
      addDint(X, MAGIC, X); // X -> 2^-11 - X
      // here: 256 <= i <= 511
      i = 0x1ff - i;
      // now 0 <= i < 256
    }

    // now 0 <= i < 256 and 0 <= X < 2^-11

    /* If is_tan=1, tan |x| = tan2pi (R * (1 + eps))
          (cases 0 <= x < pi/4 and 3pi/4 <= x < pi)
       if is_tan=0, tan |x| = cot2pi (R * (1 + eps))
          (case pi/4 <= x < 3pi/4)
       In both cases R = i/2^11 + X, 0 <= R < 1/4, and |eps| < 2^-126.67.
    */

    Dint64 U = new Dint64(), V = new Dint64(), X2 = new Dint64();
    mulDint(X2, X, X);       // X2 approximates X^2
    evalPC(U, X2);    // cos2pi(X)
    /* since 0 <= X < 2^-11, we have 0.999 < U <= 1 */
    evalPS(V, X, X2); // sin2pi(X)
    /* since 0 <= X < 2^-11, we have 0 <= V < 0.0005 */

    // sin2pi(R) ~ sin2pi(i/2^11)*cos2pi(X)+cos2pi(i/2^11)*sin2pi(X)
    Dint64 Sin = new Dint64(), UU = new Dint64(), VV = new Dint64();
    mulDint(UU, S[i], U);
    /* since 0 <= S[i] < 0.705 and 0.999 < Uin <= 1, we have
       0 <= U < 0.705 */
    mulDint(VV, C[i], V);
    addDint(Sin, UU, VV);
    /* For the error analysis, we distinguish the case i=0.
       For i=0, we have S[i]=0 and C[1]=1, thus V is the value computed
       by evalPS() above, with relative error < 2^-124.648.

       For 1 <= i < 256, analyze_sin_case1(rel=true) from sin.sage gives a
       relative error bound of -122.797 (obtained for i=1).
       In all cases, the relative error for the computation of
       sin2pi(i/2^11)*cos2pi(X)+cos2pi(i/2^11)*sin2pi(X) is bounded by -122.797
       not taking into account the approximation error in R:
       |S - sin2pi(R)| < |S| * 2^-122.797.

       For the approximation error in R, we have:
       sin |x| = sin2pi (R * (1 + eps))
       R = i/2^11 + X, 0 <= R < 1/4, and |eps| < 2^-126.67.
       Thus sin|x| = sin2pi(R+R*eps)
       = sin2pi(R)+R*eps*2*pi*cos2pi(theta), theta in [R,R+R*eps]
       Since 2*pi*R/sin(2*pi*R) < pi/2 for R < 1/4, it follows:
       | sin|x| - sin2pi(R) | < pi/2*R*|sin(2*pi*R)|
       | sin|x| - sin2pi(R) | < 2^-126.018 * |sin2pi(R)|.

       Adding both errors we get:
       | sin|x| - S | < |S| * 2^-122.797 + 2^-126.018 * |sin2pi(R)|
       < |S| * 2^-122.797 + 2^-126.018 * |S| * (1 + 2^-122.797)
       < |S| * 2^-122.650.
    */

    // cos2pi(R) ~ cos2pi(i/2^11)*cos2pi(X)-sin2pi(i/2^11)*sin2pi(X)
    Dint64 Cos = new Dint64();
    mulDint(U, C[i], U);
    mulDint(V, S[i], V);
    V.sgn = 1 - V.sgn; // negate V
    addDint(Cos, U, V);
    /* For 0 <= i < 256, analyze_sin_case2(rel=true) from sin.sage gives a
       relative error bound of -123.540 (obtained for i=0):
       |C - cos2pi(R)| < |C| * 2^-123.540.

       For the approximation error in R, we have:
       sin |x| = cos2pi (R * (1 + eps))
       R = i/2^11 + X, 0 <= R < 1/4, and |eps| < 2^-126.67.
       Thus sin|x| = cos2pi(R+R*eps)
                   = cos2pi(R)-R*eps*2*pi*sin2pi(theta), theta in [R,R+R*eps]
       Since we have R < 1/4, we have cos2pi(R) >= sqrt(2)/2,
       and it follows:
       | sin|x|/cos2pi(R) - 1 | < 2*pi*R*eps/(sqrt(2)/2)
                                < pi/2*eps/sqrt(2)          [since R < 1/4]
                                < 2^-126.518.
       Adding both errors we get:
       | cos|x| - C | < |C| * 2^-123.540 + 2^-126.518 * |cos2pi(R)|
                      < |C| * 2^-123.540 + 2^-126.518 * |C| * (1 + 2^-123.540)
                      < |C| * 2^-123.367.
    */

    /* Now if is_tan=1 we compute S/C, otherwise we compute C/S. */
    if (is_tan)
      divDint(U, Sin, Cos);
    else
      divDint(U, Cos, Sin);

    /* Relative error on S < eps1 = 2^-122.650
       Relative error on C < eps2 = 2^-123.367
       Relative error of div_dint() < eps3 = 2^-123.67
       Total relative error < (1+eps1)*(1+eps2)*(1+eps3)-1 < 2^-121.578,
       this is less than 2^-121.578/2^-128 < 86 ulps. */

    long err = 86;
    long hi0, hi1, lo0, lo1;
    lo0 = U.lo - err;
    hi0 = U.hi - (Long.compareUnsigned(lo0, U.lo) > 0 ? 1 : 0);
    lo1 = U.lo + err;
    hi1 = U.hi + (Long.compareUnsigned(lo1, U.lo) < 0 ? 1 : 0);
    /* check the upper 54 bits are equal */
    if ((hi0 >>> 10) != (hi1 >>> 10))
      {
        for (int j = 0; j < 2; j++)
          {
            if (Math.abs(x) == exceptions[j][0])
              return (x > 0) ? exceptions[j][1] + exceptions[j][2]
                : -exceptions[j][1] - exceptions[j][2];
          }
        /* if we go here, we have a hard-to-round case, but since all hard-to-round
           cases are known and pass all tests, we are ok */
      }

    if (neg)
      U.sgn = 1 - U.sgn;

    double y = dintTod(U);

    return y;
  }

  public static double compute(double x) {
    long t = Double.doubleToRawLongBits(x);
    int e = (int)((t >>> 52)&0x7ff);

    if (e == 0x7ff) { /* NaN, +Inf and -Inf. */
      if ((t << 1) == 0x7ffL << 53) { // +/-Inf
        return Double.NaN; // x - x in the C source, raises invalid
      }
      return x + x; // NaN
    }

    /* now x is a regular number */

    /* For |x| <= 0x1.d12ed0af1a27ep-27, tan(x) rounds to x (to nearest):
       we can assume x >= 0 without loss of generality since tan(-x) = -tan(x),
       we have x < tan(x) < x + x^3/3 for say 0 < x <= 1 thus
       |tan(x) - x| < x^3/3.
       Write x = c*2^e with 1/2 <= c < 1.
       Then ulp(x)/2 = 2^(e-54), and x^3/3 = c^3/3*2^(3e), thus
       x^3/3 < ulp(x)/2 rewrites as c^3/3*2^(3e) < 2^(e-54),
       or c^3*2^(2e+54) < 3 (1).
       For e <= -27, since c^3 < 1, we have c^3*2^(2e+54) < 1 < 3.
       For e=-26, (1) rewrites 4*c^3 < 3 which yields c <= 0x1.d12ed0af1a27ep-1.
    */
    long ux = t&0x7fffffffffffffffL;
    if (Long.compareUnsigned(ux, 0x3e4d12ed0af1a27eL) <= 0) { // |x| <= 0x1.d12ed0af1a27ep-27
      if (x == 0)
        return x;
      // Taylor expansion of tan(x) is x + x^3/3 around zero
        /* We have underflow exactly when 0 < |x| < 2^-1022:
           for RNDU, tan(2^-1022-2^-1074) would round to 2^-1022-2^-1075
           with unbounded exponent range */
      return Math.fma(x, 0x1p-54, x);
    }

    double[] h = new double[1], l = new double[1];
    double err;
    err = tanFast(h, l, x);
    double left  = h[0] + (l[0] - err), right = h[0] + (l[0] + err);
    if (left == right)
      return left;

    return tanAccurate(x);
  }
}
