/*
 * Correctly rounded arccosine of a binary32 value, in half-revolution units.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/acospi/acospif.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_acospif -> AcosPiF.compute
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

package io.github.ntysdd.coremath.binary32;

/**
 * Correctly rounded {@code acos(x)/pi} of a binary32 value, the translation of
 * the CORE-MATH {@code cr_acospif}.
 *
 * <p>The same shape as {@code cr_asinpif}: a 17-row table of eight coefficients
 * selected from the exponent, evaluated in {@code z = x} below {@code 2^-4} and
 * in {@code z = sqrt(1-|x|)} above it, with the reflection
 * {@code 0.5 - z*c0} and {@code o[t.u>>31] + c0*sqrt(1-|x|)} for the two
 * branches. The class is one field block and one method.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} becomes {@link Float#floatToRawIntBits}; the
 *       {@code ~0u>>9} of the row index is {@code ~0 >>> 9}, and
 *       {@code o[t.u>>31]} is {@code o[(int) (t >>> 31)]}.
 *   <li>The {@code CORE_MATH_SUPPORT_ERRNO} block is dropped, there is no
 *       {@code errno} in Java, and {@code feraiseexcept (FE_INVALID)} goes with
 *       it; {@code __builtin_nanf ("1")} is {@link Float#NaN}.
 *   <li>{@code #ifndef __FP_FAST_FMA} selects the two ways of adding the last
 *       term of the {@code |x| < 2^-4} polynomial. The reference build has FMA
 *       (the oracle is compiled with {@code -mfma}, and the guard only exists
 *       to avoid a spurious underflow when it is absent), so the
 *       {@code __builtin_fma} arm is the one translated and the
 *       {@code (c4*z4)*z4 + c0} arm is dropped with a comment.
 *   <li>{@code __builtin_expect} is dropped, the JIT profiles the branches
 *       itself, and {@code __builtin_copysign} is {@link Math#copySign}.
 *   <li>The two results the C code returns as {@code double} expressions
 *       ({@code 0.5 - z*c0} and {@code r}) are converted with the explicit
 *       {@code (float)} cast, as are the two {@code float} special cases
 *       {@code 0.0f} and {@code 1.0f}, which the C code writes as
 *       {@code 0.0f} and {@code 1.0f} already.
 *   <li>{@code const double *c = ch[i]} becomes {@code double[] c = ch[i]}.
 * </ul>
 */
public final strictfp class AcosPiF {
  private AcosPiF() {
  }

  private static final double[][] ch = {
    {0x1.45f306dc9c882p-2, 0x1.b2995e7b7dc2fp-5, 0x1.8723a1cf50c7ep-6, 0x1.d1a4591d16a29p-7,
     0x1.3ce3aa68ddaeep-7, 0x1.d3182ab0cc1bfp-8, 0x1.62b379a8b88e3p-8, 0x1.6811411fcfec2p-8},
    {0x1.ffffffffd3cd9p-2, -0x1.17cc1b3355fd5p-4, 0x1.d067a1e8d5a99p-6, -0x1.08e16fb09314ap-6,
     0x1.5eed43d42dcb2p-7, -0x1.f58baca7acc71p-8, 0x1.5dab64e2dcf15p-8, -0x1.59270e30797acp-9},
    {0x1.fffffff7c4622p-2, -0x1.17cc149ded3a2p-4, 0x1.d0654d4cb2c1ap-6, -0x1.08c3ba713d33ap-6,
     0x1.5d2053481079cp-7, -0x1.e485ebc545e7ep-8, 0x1.303baca167dddp-8, -0x1.dee8d16d06b38p-10},
    {0x1.ffffffa749848p-2, -0x1.17cbe7155935p-4, 0x1.d05a312269adfp-6, -0x1.0862b3ee617d7p-6,
     0x1.5920708db2a73p-7, -0x1.cb0463b3862c3p-8, 0x1.02b82478f95d7p-8, -0x1.52a7b8579e729p-10},
    {0x1.fffffe1f92bb5p-2, -0x1.17cb3e74c64e3p-4, 0x1.d03af67311cbfp-6, -0x1.079441cbfc7ap-6,
     0x1.52b4287805a61p-7, -0x1.ac3286d604a98p-8, 0x1.b2f1210d9701bp-9, -0x1.e740ddc25afd6p-11},
    {0x1.fffff92beb6e2p-2, -0x1.17c986fe9518bp-4, 0x1.cff98167c9a5ep-6, -0x1.0638b591eae52p-6,
     0x1.4a0803828959ep-7, -0x1.8adeca229f11dp-8, 0x1.6b9a7ba05dfcep-9, -0x1.640521a43b2dp-11},
    {0x1.ffffeccee5bfcp-2, -0x1.17c5f1753f5eap-4, 0x1.cf874e4fe258fp-6, -0x1.043e6cf77b256p-6,
     0x1.3f7db42227d92p-7, -0x1.691a6fa2a2882p-8, 0x1.2f6543162bc61p-9, -0x1.07d5da05822b6p-11},
    {0x1.ffffd2f64431dp-2, -0x1.17bf8208c10c1p-4, 0x1.ced7487cdb124p-6, -0x1.01a0d30932905p-6,
     0x1.3388f99b254dap-7, -0x1.4844e245c65bdp-8, 0x1.fa777150197c6p-10, -0x1.8c1ecf16a05c8p-12},
    {0x1.ffffa36d1712ep-2, -0x1.17b523971bd4ep-4, 0x1.cddee26de2deep-6, -0x1.fccb00abaaabcp-7,
     0x1.269afc3622342p-7, -0x1.2933152686752p-8, 0x1.a76d4956cc9a3p-10, -0x1.2ce7d6dc651cep-12},
    {0x1.ffff5402ab3a1p-2, -0x1.17a5ba85da77ap-4, 0x1.cc96894e05c02p-6, -0x1.f532143cb832ep-7,
     0x1.19180b660ff09p-7, -0x1.0c57417a78b3cp-8, 0x1.62e26cbd7bb1ep-10, -0x1.ce28d33fe1df3p-13},
    {0x1.fffed8d639751p-2, -0x1.1790349f3ae76p-4, 0x1.caf9a4fd1b398p-6, -0x1.ec986b111342ep-7,
     0x1.0b53c3ad4baa4p-7, -0x1.e3c2282eeace4p-9, 0x1.2a55369f55bbep-10, -0x1.667fe48c396e8p-13},
    {0x1.fffe24b714161p-2, -0x1.177394fbcb719p-4, 0x1.c90652d920ebdp-6, -0x1.e3239197bddf1p-7,
     0x1.fb2188525b025p-8, -0x1.b3aadd451afc7p-9, 0x1.f74020f31fdabp-11, -0x1.18b0cb246768dp-13},
    {0x1.fffd298bec9e2p-2, -0x1.174efbfd34648p-4, 0x1.c6bcfe48ea92bp-6, -0x1.d8f9f2a16157cp-7,
     0x1.e0044f56c8864p-8, -0x1.883e2347fe76cp-9, 0x1.a9f0e3c1b7af5p-11, -0x1.bb5acc0e60825p-14},
    {0x1.fffbd8b784c4dp-2, -0x1.1721abdd3722ep-4, 0x1.c41fee756d4bp-6, -0x1.ce40bccf8065fp-7,
     0x1.c59b684b70ef9p-8, -0x1.6133d027996b3p-9, 0x1.69cad01106397p-11, -0x1.60f8e45494156p-14},
    {0x1.fffa23749cf88p-2, -0x1.16eb0a8285c06p-4, 0x1.c132d762e1b0dp-6, -0x1.c31a959398f4ep-7,
     0x1.ac1c5b46bc8ap-8, -0x1.3e34f1abe51dcp-9, 0x1.346738737c0b9p-11, -0x1.1b227a3f5c75p-14},
    {0x1.fff7fb25bb407p-2, -0x1.16aaa14d7564p-4, 0x1.bdfa75fca5ff2p-6, -0x1.b7a6e260d079cp-7,
     0x1.93ab06911033cp-8, -0x1.1ee5560967fd5p-9, 0x1.07d31060838bfp-11, -0x1.c96f33a283115p-15},
  };

  private static final double[] o = {0, 1};

  public static float compute(float x) {
    float ax = Math.abs(x);
    double az = ax, z = x;
    int t = Float.floatToRawIntBits(x);
    int e = (t >>> 23) & 0xff;
    if (e >= 127) {
      if (x == 1.0f) return 0.0f;
      if (x == -1.0f) return 1.0f;
      if (e == 0xff && (t << 9) != 0) return x + x; // nan
      // the C source sets errno to EDOM and raises FE_INVALID here, and
      // returns __builtin_nanf("1")
      return Float.NaN; // |x| > 1
    }
    int s = 146 - e, i = 0;
    if (s < 32) i = ((t & (~0 >>> 9)) | 1 << 23) >>> s;
    double[] c = ch[i];
    double z2 = z*z, z4 = z2*z2;
    if (i == 0) {
      double c0 = c[0] + z2*c[1];
      double c2 = c[2] + z2*c[3];
      double c4 = c[4] + z2*c[5];
      double c6 = c[6] + z2*c[7];
      c0 += c2*z4;
      c4 += c6*z4;
      /* For |x| <= 0x1.0fd288p-127, c0 += c4*(z4*z4) would raise a spurious
         underflow exception, we use an FMA instead, where c4 * z4 does not
         underflow.  The #ifndef __FP_FAST_FMA arm of the C source, which
         computes (c4 * z4) * z4 + c0 instead, is the one for a build without
         FMA and is dropped here. */
      c0 = Math.fma(c4 * z4, z4, c0);
      return (float) (0.5 - z*c0);
    } else {
      double f = Math.sqrt(1-az);
      double c0 = c[0] + az*c[1];
      double c2 = c[2] + az*c[3];
      double c4 = c[4] + az*c[5];
      double c6 = c[6] + az*c[7];
      c0 += c2*z2;
      c4 += c6*z2;
      c0 += c4*z4;
      double r = o[t >>> 31] + c0*Math.copySign(f, x);
      return (float) r;
    }
  }
}
