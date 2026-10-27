/*
 * Correctly rounded arcsine of a binary64 value, in half-revolution units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/asinpi/asinpi.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_asinpi -> AsinPi.compute
 *
 * Copyright (c) 2023-2025 Alexei Sibidanov and Paul Zimmermann
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

import io.github.ntysdd.coremath.internal.U128;

/**
 * Correctly rounded {@code asin(x)/pi} of a binary64 value, the translation of
 * the CORE-MATH {@code cr_asinpi}.
 *
 * <p>Upstream works in 128-bit fixed point. The argument is scaled to a 64-bit
 * significand {@code sm}, and the sign, exponent and rounding bit are put back
 * on the result of a {@code u128} computation whose scaling by {@code 1/pi} is
 * the part that differs from {@code asin.c}, between the
 * {@code // asinpi_begin} and {@code // asinpi_end} markers. Three branches
 * cover {@code |x| >= 1} (with {@code +/-1} and NaN special cases),
 * {@code |x| < 2^-6} (a degree-11 Taylor polynomial with the 63-bit table
 * {@code sAcc} for the {@code pasin} variant) and {@code 2^-6 <= |x| < 1}
 * (the rotation by a multiple of {@code pi/64} from the tables {@code s} and
 * {@code sh}, refined by one Newton step on {@code sqrt(1-x^2)}).
 * {@code asinpiAcc} redoes the reduction with {@code pasin} when the two-sided
 * bound does not settle the rounding, and {@code asinpiTiny} and
 * {@code asinpiSmall} cover {@code |x| < 2^-53} and {@code |x| < 2^-26}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The whole rounding-mode machinery is dropped:
 *       {@code get_rounding_mode}, {@code get_arm_rounding_mode}, the
 *       {@code fpcr_bitfield} struct, the {@code #include <x86intrin.h>} and
 *       the {@code #include <arm64intr.h>} that only they need, and with them
 *       the {@code rm} local. Java has round-to-nearest-even and nothing else,
 *       so {@code rm == FE_TONEAREST} holds at every use: the two
 *       {@code nz = clz (fi.bh) + (rm == FE_TONEAREST)} become
 *       {@code nz = clz + 1}, the two {@code rnd} selections keep only their
 *       first arm, and the {@code FE_DOWNWARD}, {@code FE_UPWARD} and
 *       {@code else} arms are removed with a comment saying so.
 *   <li>The {@code _BitInt(128)} variant of the {@code #if} on clang 14 / gcc 14
 *       is dropped, the reference build takes the {@code unsigned __int128}
 *       arm.
 *   <li>{@code u128} and {@code u128_u} become {@link U128}: {@code .bl} and
 *       {@code .bh} are {@link U128#lo} and {@link U128#hi}, and the checks on
 *       the byte order of the union go away because the halves are named
 *       explicitly. {@code shl (&a, n)} and {@code shr (&a, n)} mutate, but
 *       {@link U128} is immutable, so they become {@code a = a.shl (n)} and
 *       {@code a = a.shr (n)}.
 *   <li>The file-local 128-bit helpers stay private static methods of this
 *       class, with the C names: {@code muuh}, {@code mh}, {@code imul},
 *       {@code mUU}, {@code muU}, {@code sqrU} and {@code pasin}. They are
 *       built on {@link U128#mul(long, long)} and
 *       {@link Math#multiplyHigh}, and on {@link U128#addLow} for the
 *       "accumulate a 64-bit high half" steps.
 *   <li>{@code (i128)dc >> ss} is an arithmetic shift of a 128-bit value, which
 *       {@link U128#shr} does not offer: the private helper {@code asr} carries
 *       it, for a value that is the sign extension of an {@code int64_t}.
 *   <li>{@code (u128)dc} where {@code dc} is a negative {@code int64_t} is
 *       {@code U128.of(dc, dc >> 63)}, the sign extension the C cast gives.
 *       {@code (u128)sm * sm} is {@link U128#mul(long, long)} and
 *       {@code fi.a *= (u64)(64u - indx)} is {@link U128#mul(long)}.
 *   <li>The three {@code CORE_MATH_SUPPORT_ERRNO} assignments are dropped, as
 *       are the two {@code CORE_MATH_CHECK_INEXACT} blocks that the reference
 *       build does not compile. {@code feraiseexcept (FE_INVALID)} goes with
 *       them, and {@code __builtin_nan ("")} is {@link Double#NaN}.
 *   <li>{@code __builtin_expect} is dropped, and
 *       {@code __attribute__((cold))} on {@code asinpi_acc}.
 *   <li>{@code #define EXCEPTIONS 18} has no Java counterpart that could size
 *       the array, so the loop runs to {@code exceptions.length}.
 *   <li>{@code static const} tables declared inside the functions become
 *       fields. Three names occur twice: {@code b} of {@code pasin} and of
 *       {@code cr_asinpi} (the second is {@code bCr}), {@code ch}
 *       ({@code chPas} for the {@code u128} one of {@code pasin},
 *       {@code chAcc} for the one of {@code asinpi_acc}, {@code ch} for
 *       {@code cr_asinpi}) and {@code s} ({@code sAcc} for the one of
 *       {@code asinpi_acc}).
 *   <li>{@code u128 Sm = (u128)(sm>>1)<<64} is {@code U128.of(0, sm >>> 1)},
 *       the closing of the shifts of {@code sm2} is {@code U128.shl}, and
 *       {@code fi.bl}/{@code fi.bh} pairs that the C code writes one at a time
 *       become a single {@link U128#of(long, long)}.
 *   <li>{@code #define X1} is never read by the C code; it is kept as a field
 *       so that the two files still line up.
 * </ul>
 */
public final strictfp class AsinPi {
  private AsinPi() {
  }

  // #define INV_PI_H in asinpi.c
  private static final long INV_PI_H = 0x517cc1b727220a94L;

  // #define INV_PI_L in asinpi.c
  private static final long INV_PI_L = 0xfe13abe8fa9a6ee0L;

  // #define ONE_OVER_PIH in asinpi.c
  private static final double ONE_OVER_PIH = 0x1.45f306dc9c883p-2;

  // #define ONE_OVER_PIL in asinpi.c
  private static final double ONE_OVER_PIL = -0x1.6b01ec5417056p-56;

  // #define X1 inside asinpi_acc(), unused by the C code
  private static final double X1 = 0x1.fff8133aa33e4p-1;

  // static const double c1h, c1l, c3 inside asinpi_small()
  private static final double c1h = 0x1.45f306dc9c883p-2;
  private static final double c1l = -0x1.6b01ec5417057p-56;
  private static final double c3 = 0x1.b2995e7b7b606p-5;

  private static long muuh(long a, long b) {
    return U128.mul(a, b).hi;
  }

  private static long mh(long a, long b) {
    return Math.multiplyHigh(a, b);
  }

  private static U128 imul(long a, long b) {
    return U128.of(a*b, Math.multiplyHigh(a, b));
  }

  private static U128 mUU(U128 a, U128 b) {
    U128 x = a, y = b;
    U128 o = U128.mul(x.hi, y.hi);
    o = o.addLow(U128.mul(x.lo, y.hi).hi);
    o = o.addLow(U128.mul(x.hi, y.lo).hi);
    return o;
  }

  private static U128 muU(long a, U128 b) {
    U128 y = b;
    U128 o = U128.mul(a, y.hi);
    o = o.addLow(U128.mul(a, y.lo).hi);
    return o;
  }

  private static U128 sqrU(U128 a) {
    U128 x = a;
    U128 os = U128.mul(x.lo, x.hi).shr(63);
    U128 o = U128.mul(x.hi, x.hi);
    return o.add(os);
  }

  // (i128)v >> n for a v that is the sign extension of an int64_t
  private static U128 asr(long v, int n) {
    long sh = v >> 63;
    if (n == 0)
      return U128.of(v, sh);
    if (n < 64)
      return U128.of((v >>> n) | (sh << (64 - n)), sh);
    return U128.of(sh, sh);
  }

  private static U128 pasin(U128 x) {
    long xh = x.hi;
    U128 t = chPas[3];
    t = t.addLow(muuh(xh, b[0] + muuh(xh, b[1] + muuh(xh, b[2] + muuh(xh, b[3])))));
    return mUU(x, chPas[0].add(mUU(x, chPas[1].add(mUU(x, chPas[2].add(mUU(x, t)))))));
  }

  private static final long[] b = {0x5ba2e8ba2e8ad9b7L, 0x0004713b13b29079L, 0x000000393331e196L,
    0x0000000002f5c315L};

  private static final U128[] chPas = {
    U128.of(0xaaaaaaaaaaaaaaa5L, 0x0002aaaaaaaaaaaaL), // *+1
    U128.of(0x3333333333333484L, 0x0000001333333333L), // *+1
    U128.of(0xb6db6db6db6da950L, 0x0000000000b6db6dL), // *+1
    U128.of(0x1c71c71c71c76217L, 0x00000000000007c7L), // *+1
  };

  private static final U128[] sAcc = {
    U128.of(0x4e29cf6e5fed0679L, 0x648557de8d99f7eL),
    U128.of(0x76a17954b2b7c517L, 0xc8fb2f886ec09f3L),
    U128.of(0xbeeeae8129a786b9L, 0x12d52092ce19f5ccL),
    U128.of(0xd8e72d912977ee71L, 0x1917a6bc29b42be1L),
    U128.of(0x4e08e535cadaf147L, 0x1f564e56a9730e34L),
    U128.of(0xc002a2684781f080L, 0x259020dd1cc27444L),
    U128.of(0x8ffbbceed62c7c43L, 0x2bc42889167f8ca9L),
    U128.of(0x9732300393f33614L, 0x31f17078d34c156cL),
    U128.of(0x43af186b79b2a0f3L, 0x381704d4fc9ec5f9L),
    U128.of(0x90887712e9dc9663L, 0x3e33f2f642be355eL),
    U128.of(0x4c20ab7aa99a2183L, 0x4447498ac7d9dd82L),
    U128.of(0xd725d3b9ed35fbaaL, 0x4a5018bb567c16a2L),
    U128.of(0x97c4afa25181e605L, 0x504d72505d98050cL),
    U128.of(0x408fca9cc277fc1fL, 0x563e69d6ac7f73f8L),
    U128.of(0x4e61f79b3a36f1dcL, 0x5c2214c3e9167abbL),
    U128.of(0x98916152cf7eee1cL, 0x61f78a9abaa58b46L),
    U128.of(0xd409485edd56b172L, 0x67bde50ea3b628b6L),
    U128.of(0x9b165cba0c171818L, 0x6d744027857300adL),
    U128.of(0x1439670dfe3d68e6L, 0x7319ba64c711785aL),
    U128.of(0x362474f1a105878fL, 0x78ad74e01bd8ec78L),
    U128.of(0x13e03e4889485c69L, 0x7e2e936fe26ae7edL),
    U128.of(0xbfd79717f2880abfL, 0x839c3cc917ff6cb4L),
    U128.of(0xb892ca8361d8c84cL, 0x88f59aa0da591421L),
    U128.of(0xbba4cfecbff54867L, 0x8e39d9cd73464364L),
    U128.of(0xb17821911e71c16eL, 0x93682a66e896f544L),
    U128.of(0x19cec845ac87a5c6L, 0x987fbfe70b81a708L),
    U128.of(0xe25e39549638ae68L, 0x9d7fd1490285c9e3L),
    U128.of(0x3b5167ee359a234eL, 0xa267992848eeb0c0L),
    U128.of(0x149f6e75993468a3L, 0xa73655df1f2f489eL),
    U128.of(0x1becda8089c1a94cL, 0xabeb49a46764fd15L),
    U128.of(0xe4cad00d5c94bcd2L, 0xb085baa8e966f6daL),
    U128.of(0x597d89b3754abe9fL, 0xb504f333f9de6484L),
    U128.of(0x9de1e3b22b8bf4dbL, 0xb96841bf7ffcb21aL),
    U128.of(0xac85320f528d6d5dL, 0xbdaef913557d76f0L),
    U128.of(0xbdf0715cb8b20bd7L, 0xc1d8705ffcbb6e90L),
    U128.of(0x43da25d99267326bL, 0xc5e40358a8ba05a7L),
    U128.of(0x8335241be1693225L, 0xc9d1124c931fda7aL),
    U128.of(0x23af31db7179a4aaL, 0xcd9f023f9c3a059eL),
    U128.of(0x744fea20e8abef92L, 0xd14d3d02313c0eedL),
    U128.of(0xf630e8b6dac83e69L, 0xd4db3148750d1819L),
    U128.of(0x24b9fe00663574a4L, 0xd84852c0a80ffcdbL),
    U128.of(0x2c19b63253da43fcL, 0xdb941a28cb71ec87L),
    U128.of(0x4b19aa71fec3ae6dL, 0xdebe05637ca94cfbL),
    U128.of(0xf4e8a8372f8c5810L, 0xe1c5978c05ed8691L),
    U128.of(0x122785ae67f5515dL, 0xe4aa5909a08fa7b4L),
    U128.of(0x125129529d48a92fL, 0xe76bd7a1e63b9786L),
    U128.of(0x15ad45b4a1b5e823L, 0xea09a68a6e49cd62L),
    U128.of(0x7e610231ac1d6181L, 0xec835e79946a3145L),
    U128.of(0x86f8c20fb664b01bL, 0xeed89db66611e307L),
    U128.of(0x67127db35b287316L, 0xf1090827b43725fdL),
    U128.of(0xa5486bdc455d56a2L, 0xf314476247088f74L),
    U128.of(0x163c5c7f03b718c5L, 0xf4fa0ab6316ed2ecL),
    U128.of(0x2c791f59cc1ffc23L, 0xf6ba073b424b19e8L),
    U128.of(0xc7adc6b4988891bbL, 0xf853f7dc9186b952L),
    U128.of(0x4504ae08d19b2980L, 0xf9c79d63272c4628L),
    U128.of(0x2172a361fd2a722fL, 0xfb14be7fbae58156L),
    U128.of(0x256778ffcb5c1769L, 0xfc3b27d38a5d49abL),
    U128.of(0xeae6bd951c1dabbeL, 0xfd3aabf84528b50bL),
    U128.of(0x90cd1d959db674efL, 0xfe1323870cfe9a3dL),
    U128.of(0x41390efdc726e9efL, 0xfec46d1e89292cf0L),
    U128.of(0xf668633f1ab858aL, 0xff4e6d680c41d0a9L),
    U128.of(0x421e8edaaf59453eL, 0xffb10f1bcb6bef1dL),
    U128.of(0x5657552366961732L, 0xffec4304266865d9L),
  };

  private static final double[] chAcc = {0x1.ffb77e06e54aap+5, -0x1.3b200d87cc0fep+5,
    0x1.79457faf679e3p+4, -0x1.dc7d5a91dfb7ep+2};

  private static final double[][] exceptions = {
    {0x1.2d7f168888139p-38, 0x1.7fe08f5284726p-40, 0x1.77c696b55fc1dp-147},
    {0x1.3b75472d45185p-36, 0x1.91a75c23a207fp-38, 0x1.e6032f672e328p-145},
    {0x1.56a4aa740a5a7p-53, 0x1.b44453e2404e7p-55, 0x1.4e1f71472791ap-161},
    {0x1.6bf137c2d4708p-51, 0x1.cf62bb838ec59p-53, -0x1.fffffffffffffp-107},
    {0x1.d57c381f54243p-43, 0x1.2ae2325f45589p-44, 0x1.fffffffffffffp-98},
    {0x1.a9aee69418f0cp-30, 0x1.0eff9393e91dfp-31, 0x1.fffffffffffffp-85},
    {0x1.7718543a5606ap-29, 0x1.dd95f913d2d23p-31, -0x1.fffffffffffffp-85},
    {0x1.93ba773b31ea1p-45, 0x1.01057f17ceba6p-46, 0x1.1558b472bae84p-155},
    {0x1.cc5dfa12664a3p-33, 0x1.25142c0254b86p-34, 0x1.47123997ff7e4p-142},
    {0x1.f067f55743ea4p-43, 0x1.3c059d39f1d61p-44, 0x1.3b7145ed225e9p-154},
    {0x1.bb2e2254ec57bp-28, 0x1.1a232520b30b8p-29, -0x1.8c0ab8fb3b592p-138},
    {0x1.522191285fcf1p-32, 0x1.ae859c3537f4dp-34, 0x1.647ba0b3d335fp-140},
    {0x1.ec65c2da06159p-44, 0x1.39785114bae2dp-45, 0x1.61ba6bc511061p-152},
    {0x1.68e6482549db1p-51, 0x1.cb82f5da3a6b6p-53, 0x1.62dd725314f5bp-159},
    {0x1.d75f3adfd53b6p-32, 0x1.2c15b0e2bece3p-33, 0x1.1275b80c55392p-141},
    {0x1.8577cec54ab8p-49, 0x1.efe2d4aa2d14p-51, 0x1.dc301c4666ac8p-158},
    {0x1.9a5e5a46ae2b1p-35, 0x1.053fad95a3298p-36, 0x1.3cb00f4298933p-144},
    {0x1.6d0b888ccfa4ap-30, 0x1.d0ca30163b96fp-32, 0x1.d42b0847637dfp-138},
  };

  private static final long[] s = {0L, 0x3242abef46ccfbfL, 0x647d97c437604f9L, 0x96a9049670cfae6L,
    0xc8bd35e14da15f0L, 0xfab272b54b9871aL, 0x12c8106e8e613a22L, 0x15e214448b3fc654L,
    0x18f8b83c69a60ab6L, 0x1c0b826a7e4f62fcL, 0x1f19f97b215f1aafL, 0x2223a4c563eceec1L,
    0x25280c5dab3e0b51L, 0x2826b9282ecc0286L, 0x2b1f34eb563fb9fcL, 0x2e110a61f48b3d5dL,
    0x30fbc54d5d52c5a3L, 0x33def28751db145bL, 0x36ba2013c2b98056L, 0x398cdd326388bc2dL,
    0x3c56ba700dec763cL, 0x3f1749b7f13573f6L, 0x41ce1e648bffb65aL, 0x447acd506d2c8a10L,
    0x471cece6b9a321b2L, 0x49b41533744b7aa2L, 0x4c3fdff385c0d384L, 0x4ebfe8a48142e4f1L,
    0x5133cc9424775860L, 0x539b2aef8f97a44fL, 0x55f5a4d233b27e8aL, 0x5842dd5474b37b6dL,
    0x5a827999fcef3242L, 0x5cb420dfbffe590dL, 0x5ed77c89aabebb78L, 0x60ec382ffe5db748L,
    0x62f201ac545d02d3L, 0x64e88926498fed3dL, 0x66cf811fce1d02cfL, 0x68a69e81189e0776L,
    0x6a6d98a43a868c0cL, 0x6c2429605407fe6dL, 0x6dca0d1465b8f643L, 0x6f5f02b1be54a67dL,
    0x70e2cbc602f6c348L, 0x72552c84d047d3daL, 0x73b5ebd0f31dcbc3L, 0x7504d3453724e6b1L,
    0x7641af3cca3518a2L, 0x776c4edb3308f183L, 0x78848413da1b92feL, 0x798a23b1238447baL,
    0x7a7d055b18b76976L, 0x7b5d039da1258cf4L, 0x7c29fbee48c35ca9L, 0x7ce3ceb193962314L,
    0x7d8a5f3fdd72c0abL, 0x7e1d93e9c52ea4d5L, 0x7e9d55fc22945a85L, 0x7f0991c3867f4d1eL,
    0x7f62368f44949678L, 0x7fa736b40620e854L, 0x7fd8878de5b5f78eL, 0x7ff62182133432ecL, ~0L >>> 1};

  private static final long[] sh = {0L, 0xc90aafbd1b33efcaL, 0x91f65f10dd813e6fL,
    0x5aa41259c33eb998L, 0x22f4d78536857c3bL, 0xeac9cad52e61c68aL, 0xb2041ba3984e8898L,
    0x78851122cff19532L, 0x3e2e0f1a6982ad93L, 0x2e09a9f93d8bf28L, 0xc67e5ec857c6abd2L,
    0x88e93158fb3bb04aL, 0x4a03176acf82d45bL, 0x9ae4a0bb300a193L, 0xc7cd3ad58fee7f08L,
    0x8442987d22cf576aL, 0x3ef1535754b168d3L, 0xf7bca1d476c516dbL, 0xae8804f0ae6015b3L,
    0x63374c98e22f0b43L, 0x15ae9c037b1d8f07L, 0xc5d26dfc4d5cfda2L, 0x73879922ffed9698L,
    0x1eb3541b4b228437L, 0xc73b39ae68c86c97L, 0x6d054cdd12dea896L, 0xff7fce17034e103L,
    0xaffa292050b93c7cL, 0x4cf325091dd61807L, 0xe6cabbe3e5e913c3L, 0x7d69348cec9fa2a3L,
    0x10b7551d2cdedb5dL, 0xa09e667f3bcc908bL, 0x2d0837efff964354L, 0xb5df226aafaede16L,
    0x3b0e0bff976dd218L, 0xbc806b151740b4e8L, 0x3a22499263fb4f50L, 0xb3e047f38740b3c4L,
    0x29a7a0462781ddafL, 0x9b66290ea1a3033fL, 0x90a581501ff9b65L, 0x728345196e3d90e6L,
    0xd7c0ac6f95299f69L, 0x38b2f180bdb0d23fL, 0x954b213411f4f682L, 0xed7af43cc772f0c2L,
    0x4134d14dc939ac43L, 0x906bcf328d4628b0L, 0xdb13b6ccc23c60f1L, 0x212104f686e4bfadL,
    0x6288ec48e111ee95L, 0x9f4156c62dda5d83L, 0xd740e76849633d06L, 0xa7efb9230d72a59L,
    0x38f3ac64e588c509L, 0x6297cff75cb02ac4L, 0x8764fa714ba93565L, 0xa7557f08a516a17dL,
    0xc26470e19fd347b2L, 0xd88da3d125259e08L, 0xe9cdad01883a1522L, 0xf621e3796d7de3a8L,
    0xfd886084cd0cbb2bL, 0L};

  private static final long[] a = {0x002aaaaaaaaaaaaaL, 0x0000133333333344L, 0x0000000b6db6d69dL,
    0x0000000007c7aa6fL};

  private static final long[] bCr = {0xaaaaaaaaaaaaaaaaL, 0x0004ccccccccccccL, 0x0000002db6db6db6L,
    0x0000000001f1c71cL, 0x00000000000016e8L};

  private static final double[] ch = {0x1.ffb77e06e54aap+5, -0x1.3b200d87cc0fep+5,
    0x1.79457faf679e3p+4, -0x1.dc7d5a91dfb7ep+2};

  // __attribute__((cold)) is dropped, the JIT decides
  private static double asinpiAcc(double x) {
    long t = Double.doubleToRawLongBits(x);
    int se = (int) ((t >>> 52) & 0x7ff) - 0x3ff;
    long xsign = t & (1L << 63);
    double ax = Math.abs(x);
    U128 fi;
    long sm = (t << 11) | 1L << 63;
    U128 sm2 = U128.mul(sm, sm);
    if (ax < 0.0131875) {
      int ss = 2*se;
      sm2 = sm2.shr(-14 - ss);
      U128 Sm = U128.of(0, sm >>> 1);
      fi = Sm.add(muU(sm >>> 1, pasin(sm2)));
      se += 0x3ff;
    } else {
      double xx = Math.fma(x, -x, 1.0);
      double ixx = 1.0/xx, c = Math.sqrt(xx);
      ixx *= c;
      double x2 = x*x;
      double c0 = chAcc[0] + ax*chAcc[1];
      double c2 = chAcc[2] + ax*chAcc[3];
      c0 += x2*c2;
      long ic = Double.doubleToRawLongBits(c0*c + 64.0);
      int indx = (int) (((ic & (~0L >>> 12)) + (1L << (52 - 7))) >>> (52 - 6));
      long cm = (Double.doubleToRawLongBits(c) << 11) | 1L << 63;
      int ce = (int) (Double.doubleToRawLongBits(c) >>> 52) - 0x3ff;
      U128 cm2 = U128.mul(cm, cm);
      int off = 36 - 22 + 14;
      int ss = 128 - 104 + 2*se + off;
      sm2 = sm2.shl(ss);
      int sc = 128 - 104 + 2*ce + off;
      cm2 = cm2.shl(sc);
      sm2 = sm2.add(cm2);
      long h = sm2.hi;
      long ixm = (Double.doubleToRawLongBits(ixx) & (~0L >>> 12)) | 1L << 52;
      int ixe = (int) (Double.doubleToRawLongBits(ixx) >>> 52) - 0x3ff;
      long dc = mh(h, ixm);
      U128 dsm2 = imul(dc, cm >>> 1);
      dsm2 = dsm2.shl(13);
      sm2 = sm2.sub(dsm2);
      U128 dsm3 = imul(dc, dc);
      sc = 28 - ixe*2;
      if (sc >= 0)
        dsm3 = dsm3.shr(sc);
      else
        dsm3 = dsm3.shl(-sc); // since sc < 0, the shift by -sc is legitimate
      sm2 = sm2.add(dsm3);
      int k = ixe-ce;
      ss = 24 + k;
      U128 Cm = U128.of(0, cm),
        D = U128.of(dc << ss, dc >> (64 - ss));
      Cm = Cm.sub(D);
      h = sm2.shr(14).lo;
      dc = mh(h, ixm);
      ss = 26-k;
      if (ss >= 0)
        Cm = Cm.sub(asr(dc, ss));
      else
        Cm = Cm.sub(U128.of(dc, dc >> 63).shl(-ss)); // since ss < 0, the shift by -ss is legitimate
      fi = U128.of(0xd313198a2e037073L, 0x3243f6a8885a308L);
      fi = fi.mul(64 - indx);
      if (indx == 0) {
        Cm = Cm.shr(-ce - 7);
        U128 c_2 = sqrU(Cm);
        U128 z = pasin(c_2);
        Cm = Cm.add(mUU(Cm, z));
        fi = fi.sub(Cm.shr(7));
      } else {
        U128 v = muU(sm >>> -se, sAcc[indx - 1]).sub(mUU(Cm, sAcc[63 - indx]).shr(-ce));
        long msk = v.hi >> 63;
        U128 vv = v.add(v);
        U128 v2 = sqrU(v).sub(U128.of(msk&vv.lo, msk&vv.hi));
        v2 = v2.shl(14);
        U128 p = pasin(v2);
        v = v.add(mUU(p, v).sub(U128.of(msk&p.lo, msk&p.hi)));
        fi = fi.add(v);
      }
      se = 0x3fe;
    }

    // asinpi_begin
    /* multiply by 1/pi */
    U128 m1 = U128.mul(INV_PI_H, fi.lo);
    U128 m2 = U128.mul(INV_PI_L, fi.hi);
    fi = U128.mul(INV_PI_H, fi.hi);
    fi = fi.add(m1.shr(64));
    fi = fi.add(m2.shr(64));
    // asinpi_end

    int nz = Long.numberOfLeadingZeros(fi.hi);
    // the C code picks rnd from the rounding mode; Java has only
    // round-to-nearest-even, so only its arm is left
    long rnd = (fi.hi >>> (10 - nz)) & 1;
    t = (fi.hi >>> (11 - nz)) + (((long) se - nz) << 52 | xsign | rnd);
    return Double.longBitsToDouble(t);
  }

  /* special routine for |x| < 2^-53 */
  private static double asinpiTiny(double x) {
    double h, l;
    long t = Double.doubleToRawLongBits(x);
    long au = t & 0x7fffffffffffffffL;

    if (x == 0)
      return x;

    /* deal with generic exceptional case +/-0x1.59af9a1194efep-xxx */
    if ((au << 12) == 0x59af9a1194efe000L) {
      int e = (int) ((t >>> 52) & 0x7ff);
      h = 0x1.b824198b94a89p-56;
      l = 0x1.fffffffffffffp-110;
      t = Double.doubleToRawLongBits((x > 0) ? 1.0 : -1.0);
      t -= (long) (0x3c9 - e) << 52;
      return Math.fma(l, Double.longBitsToDouble(t), h * Double.longBitsToDouble(t));
    }

    /* exceptional case +/-0x1.5cba89af1f855p-1020 */
    if (au == 0x35cba89af1f855L)
      return (x > 0)
        ? Math.fma(0x1p-600, -0x1p-600, 0x1.bc03df34e902cp-1022)
        : Math.fma(0x1p-600, 0x1p-600, -0x1.bc03df34e902cp-1022);

    /* exceptional case +/-0x1.5cba89af1f855p-1022 */
    if (au == 0x15cba89af1f855L) {
      // the C source sets errno to ERANGE here (underflow)
      return (x > 0)
        ? Math.fma(-0x1p-600, 0x1p-600, 0x1.bc03df34e902cp-1024)
        : Math.fma(0x1p-600, 0x1p-600, -0x1.bc03df34e902cp-1024);
    }

    /* exceptional case +/-0x1.68e6482549db1p-1022 */
    if (au == 0x168e6482549db1L) {
      // the C source sets errno to ERANGE here (underflow)
      return (x > 0)
        ? Math.fma(0x1p-600, 0x1p-600, 0x1.cb82f5da3a6b4p-1024)
        : Math.fma(-0x1p-600, 0x1p-600, -0x1.cb82f5da3a6b4p-1024);
    }

    /* exceptional case 0x1.5cba89af1f855p-1021 */
    if (au == 0x25cba89af1f855L) {
      // the C source sets errno to ERANGE here (underflow)
      return (x > 0)
        ? Math.fma(-0x1p-600, 0x1p-600, 0x1.bc03df34e902cp-1023)
        : Math.fma(0x1p-600, 0x1p-600, -0x1.bc03df34e902cp-1023);
    }

    /* we compute h before scaling, so that h is exactly representable */
    h = x * ONE_OVER_PIH;
    double sx  = x * 0x1p106; /* scale x */
    l = Math.fma(sx, ONE_OVER_PIH, -h * 0x1p106);
    l = Math.fma(sx, ONE_OVER_PIL, l);
    /* scale back */
    /* the C source sets errno to ERANGE here (underflow) when
       |x| <= 0x1.921fb54442d17p-1021 or |res| < 0x1p-1022 */
    double res = Math.fma(l, 0x1p-106, h);
    return res;
  }

  /* special routine for 2^-53 <= |x| < 2^-26 */
  private static double asinpiSmall(double x) {
    for (int i = 0; i < exceptions.length; i++)
    {
      if (x == exceptions[i][0])
        return exceptions[i][1] + exceptions[i][2];
      if (x == -exceptions[i][0])
        return -exceptions[i][1] - exceptions[i][2];
    }

    /* We use the Sollya polynomial 0x1.45f306dc9c882a53f84eafa3ea4p-2 * x
       + 0x1.b2995e7b7b606p-5 * x^3, with relative error bounded by 2^-106.965
       on [2^-53, 2^-26] */
    double h = c1h, l = Math.fma(c3, x * x, c1l);
    /* multiply h+l by x */
    double hh, ll;
    hh = h * x;
    ll = Math.fma(h, x, -hh);
    /* hh+ll = h*x */
    ll = Math.fma(l, x, ll);
    return hh + ll;
  }

  public static double compute(double x) {
    long t = Double.doubleToRawLongBits(x);
    int e = (int) ((t >>> 52) & 0x7ff) - 0x3ff;
    /* x = 2^e*y with 1 <= |y| < 2 */
    long xsign = t & (1L << 63);
    /* xsign=0 for x > 0, xsign=1 for x < 0 */
    long sm = (t << 11) | 1L << 63;
    /* sm contains in its high 53 bits: the implicit leading bit, and the
       the 52 explicit bits from the significand, thus |x| = 2^(e+1)*sm/2^64
       where 2^63 <= sm < 2^64 */
    U128 fi;
    if (e >= 0) { /* |x| >= 1 */
      long m = t << 12; /* m contains the 52 explicit bits from the significand */
      if (e == 0 && m == 0) /* case x = 1 or -1: asinpi(1)=1/2, asin(-1)=-1/2 */
        /* h=0x1.921fb54442d18p+0 is pi/2 rounded to nearest,
           and 0x1.1a62633145c07p-54 is pi/2-h rounded to nearest */
        return x * 0.5; // asinpi_specific
      if (e == 0x400 && m != 0) return x + x; // nan
      // the C source sets errno to EDOM here and raises FE_INVALID, and
      // returns __builtin_nan("")
      return Double.NaN;
    } else if (e < -6) { /* |x| < 2^-6 */
      if (e < -26) { /* |x| < 2^-26 */
        // asinpi_begin
        if (e < -53) /* |x| < 2^-53 */
          return asinpiTiny(x);
        return asinpiSmall(x);
        // asinpi_end
      }

      /* now 2^-26 <= |x| < 2^-6 */
      /* We also have |x| = 2^e*sm/2^63, since e <= -7 we have e+1 <= -6,
         thus we write |x| = 2^(e+1)*y with y=sm/2^64 */
      long v2 = muuh(sm, sm), v3 = muuh(sm, v2);
      /* v2 = floor(sm^2/2^64), v3 = floor(sm*v2/2^64) */
      /* since -26 <= e <= -7, 0 <= -2*e-14 <= 38 thus the shift is valid */
      v2 >>>= -2*e-14;
      /* v2/2^64 approximates 2^(-2e-14)*y^2: err(v2) <= 1 */
      /* v3/2^64 approximates y^3: err(v3) <= 2 */
      long d = muuh(v3, bCr[0] + muuh(v2, bCr[1]
        + muuh(v2, bCr[2] + muuh(v2, bCr[3] + muuh(v2, bCr[4])))));
      /* d/2^64 approximates (up to some power of two) 1/6*x^3 + 3/40*x^5 + ...
         + 63/2816*x^11 with maximal absolute error 13, see upstream. */
      int ss = 63 + 2*e;
      fi = U128.of(d << ss, (d >>> (64 - ss)) + (sm >>> 1));
      /* fi.a/2^127 approximates y + 1/6*y^3 + 3/40*y^5 + ... + 63/2816*y^11 */

      // asinpi_begin
      U128 m1 = U128.mul(INV_PI_H, fi.lo);
      U128 m2 = U128.mul(INV_PI_L, fi.hi);
      fi = U128.mul(INV_PI_H, fi.hi);
      fi = fi.add(m1.shr(64));
      fi = fi.add(m2.shr(64));
      /* now fi.a/2^127 approximates asinpi(x), with error < 15 */
      // asinpi_end

      int nz = Long.numberOfLeadingZeros(fi.hi) + 1;
      /* the number of leading zeros in fi.bh is usually 1, but it can also
         be 0, for example for x=0x1.fffffffffffffp-7, thus nz is 1 or 2; the
         C code adds (rm == FE_TONEAREST), which is always true in Java */
      U128 u = fi;
      u = u.addLow(15L << ss); // asinpi_specific
      /* Here fi is the 'left' approximation, and u is the 'right' approximation.
         We check the last bit (or the round bit for FE_TONEAREST) does not
         change between fi and u. */
      if ((((fi.hi ^ u.hi) >>> (11 - nz)) & 1) != 0) {
        return asinpiAcc(x);
      }
      e += 0x3ff;
    } else { /* case |x| >= 2^-6 */
      double xx = Math.fma(x, -x, 1.0); /* xx = 1-x^2 */
      double ixx = 1.0/xx, c = Math.sqrt(xx);
      /* ixx ~ 1/(1-x^2), c ~ sqrt(1-x^2) */
      ixx *= c;
      /* ixx approximates 1/sqrt(1-x^2), with |ixx-1/c| < 2^-49.9*1/c */
      double ax = Math.abs(x), x2 = x*x;
      double c0 = ch[0] + ax*ch[1];
      double c2 = ch[2] + ax*ch[3];
      c0 += x2*c2;
      /* FIXME: use FMA here: c0 = fma (c0, c.f, 64) */
      c0 *= c;
      c0 += 64;
      /* now c0 approximates 64+64*acos(x)/(pi/2), which lies in [64,128] */
      long ic = Double.doubleToRawLongBits(c0);
      int indx = (int) (((ic & (~0L >>> 12)) + (1L << (52 - 7))) >>> (52 - 6));
      /* indx = round(c0)-64 */
      long cm = (Double.doubleToRawLongBits(c) << 11) | 1L << 63;
      int ce = (int) (Double.doubleToRawLongBits(c) >>> 52) - 0x3ff;
      /* cm contains in its high bits the 53 significant bits from c, which
         approximates sqrt(1-x^2), including the implicit bit, ce is the
         corresponding exponent, such that c = 2^ce*cm/2^63 */
      U128 sm2 = U128.mul(sm, sm), cm2 = U128.mul(cm, cm);
      /* x^2 = 2^(2*e)*sm2/2^126 and c^2 = 2^(2*ce)*cm2/2^126 */
      int off = 36 - 22 + 14;   /* off = 28 */
      int ss = 128 - 104 + 2*e + off; /* ss = 52 + 2*e */
      /* for e=-6, ss=40; for x=0x1.fffffffffffffp-1, ss=50 */
      sm2 = sm2.shl(ss);
      /* now frac(2^50*x^2) = sm2/2^128 */
      int sc = 128 - 104 + 2*ce + off;
      if (sc >= 0)
        cm2 = cm2.shl(sc);
      else
        cm2 = cm2.shr(-sc); // since sc < 0, the shift by -sc is legitimate
      /* now frac(2^50*c^2) = cm2/2^128 */
      sm2 = sm2.add(cm2); /* now frac(2^50*(x^2+c^2)) = sm2/2^128 */
      long h = sm2.hi;
      /* h/2^64 approximates 2^50*(x^2+c^2) mod 1, with error bounded by
         1/2^64 for the truncated part sm2.bl/2^128 */
      long ixm = (Double.doubleToRawLongBits(ixx) & (~0L >>> 12)) | 1L << 52;
      int ixe = (int) (Double.doubleToRawLongBits(ixx) >>> 52) - 0x3ff;
      /* ixx = ixm*2^(ixe-52) */
      long Smh;
      ss = 6 + e; /* ss >= 0 */
      Smh = (sm << ss) - sh[64 - indx];
      /* since |x| = 2^(e+1)*sm/2^64, sm*2^ss = |x|*2^69 */
      /* now Smh approximates 2^69*(|x|-sin(y[i])) mod 2^64,
         with maximal error < 0.5 */
      long Cmh;
      sc = 6 + ce;
      if (sc >= 0)
        Cmh = cm << sc;
      else
        Cmh = cm >>> -sc; // since sc < 0, the shift by -sc is legitimate
      Cmh -= sh[indx];
      /* We now subtract 1/2*h/2^114*ixm*2^(ixe-52) from c */
      Cmh -= mh(h, ixm) >> (34 - ixe);
      /* now Cmh approximates 2^69*(sqrt(1-x^2)-cos(y[i]))
         with maximal error 2^69*2^-74.72+0.5 < 0.52 */
      long v = mh(Smh, s[indx]) - mh(Cmh, s[64 - indx]), v2 = mh(v, v), v3 = mh(v2, v);
      v += mh(v3, a[0] + muuh(v2, a[1] + muuh(v2, a[2] + muuh(v2, a[3]))));
      /* the total error on v is bounded by 24.08 */

      fi = U128.of(0xd313198a2e037073L, 0x3243f6a8885a308L);
      /* fi.a/2^127 approximates pi/2/64 */
      fi = fi.mul(64 - indx); /* multiply pi/2/64 by i=64-indx */
      /* we add v after normalization */
      long Vh = v >> 5, Vl = v << 59;
      /* the maximal error 24.08 on v translates into an error of 24.08*2^59
         on Vl */
      U128 V = U128.of(Vl, Vh);
      fi = fi.add(V);
      /* now fi/2^127 approximates asin(|x|) */

      // asinpi_begin
      U128 m1 = U128.mul(INV_PI_H, fi.lo);
      U128 m2 = U128.mul(INV_PI_L, fi.hi);
      fi = U128.mul(INV_PI_H, fi.hi);
      fi = fi.add(m1.shr(64));
      fi = fi.add(m2.shr(64));
      /* now fi.a/2^127 approximates asinpi(|x|), with error < 124*2^55 */
      // asinpi_end

      int nz = Long.numberOfLeadingZeros(fi.hi) + 1;
      U128 u = fi, d = fi;
      u = u.addLow(124L << 55); // asinpi_specific
      d = d.sub(U128.of(124L << 55, 0)); // asinpi_specific
      if ((((d.hi ^ u.hi) >>> (11 - nz)) & 1) != 0) {
        return asinpiAcc(x);
      }
      e = 0x3fe;
    }

    int nz = Long.numberOfLeadingZeros(fi.hi);
    // the C code picks rnd from the rounding mode; Java has only
    // round-to-nearest-even, so only its arm is left
    long rnd = (fi.hi >>> (10 - nz)) & 1;
    t = ((fi.hi >>> (11 - nz)) + (((long) (e - nz) << 52) | rnd)) | xsign;
    return Double.longBitsToDouble(t);
  }
}
