/*
 * Correctly rounded atanh(x) for binary64 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/atanh/atanh.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_atanh -> Atanh.compute
 *
 * Copyright (c) 2023-2025 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly rounded {@code atanh(x)} of a binary64 value, the translation of
 * the CORE-MATH {@code cr_atanh}.
 *
 * <p>Below {@code 0x1p-2} the function is {@code x + x^3/3 + ...} with a
 * nine-coefficient minimax correction, and below
 * {@code 0x1.d12ed0af1a27fp-27} it collapses to {@code fma(x, 2^-55, x)}.
 * Above it, {@code atanh(x) = log((1+x)/(1-x))/2} is evaluated in double-double
 * through the {@code 1+x} and {@code 1-x} split, the same {@code r1}/{@code r2}
 * and {@code l1}/{@code l2} logarithm as {@code asinh} with the {@code B}
 * correction table, and the {@code asAtanhZero} / {@code asAtanhRefine} pair
 * settles the inputs whose two-sided bound disagrees;
 * {@code asAtanhDatabase} holds the two exact rows.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code errno} assignments are dropped with their
 *       {@code CORE_MATH_SUPPORT_ERRNO} blocks. The file includes no
 *       floating-point environment.
 *   <li>The pole {@code __builtin_copysign(1, x) / 0.0} keeps its shape as
 *       {@link Math#copySign}, which yields the infinities of the right sign,
 *       and {@code __builtin_sqrt(-1.0)} stays {@link Math#sqrt}, which yields
 *       the NaN the C code wants.
 *   <li>The {@code b64u64_u} unions become
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}; the bit patterns {@code ix}, {@code t},
 *       {@code v}, {@code w} and {@code t0} are carried as {@code long}, and
 *       the two {@code tn}/{@code td} constructions of the binary32 file have
 *       no counterpart here. {@code __builtin_expect} and the forward
 *       declaration of {@code asAtanhRefine} are dropped.
 *   <li>{@code __builtin_fma} is {@link Math#fma}, {@code __builtin_sqrt} is
 *       {@link Math#sqrt}, {@code __builtin_fabs} is {@link Math#abs} and
 *       {@code __builtin_copysign} is {@link Math#copySign}.
 *   <li>{@code double *} output parameters, and every local whose address the C
 *       code passes ({@code y1} and {@code y2} of {@code asAtanhZero},
 *       {@code pl}, {@code tl} and {@code ll} of the entry point, {@code xl},
 *       {@code sl} and {@code v2} of {@code asAtanhRefine}), become one-element
 *       arrays. The C array {@code double L[3]} keeps its shape as
 *       {@code double[] L}.
 *   <li>{@code uint64_t} comparisons go through {@link Long#compareUnsigned}
 *       and {@code uint64_t} shifts are {@code >>>}, except where the C code
 *       shifts a signed value or knows the operand is positive.
 *   <li>The {@code struct {ushort c0; short c1;} B[]} becomes {@code int[][] B}
 *       with the two columns in declaration order, and the casts
 *       {@code (u64)B[i].c0<<33} and {@code (int64_t)B[i].c1*(d>>16)} become
 *       the {@code long} expressions {@code (long)B[i][0]<<33} and
 *       {@code (long)B[i][1]*(d>>16)}.
 *   <li>The three-dimensional table {@code LL[4][17][3]} keeps its shape as
 *       {@code double[][][] LL}.
 *   <li>{@code static const} tables declared inside the functions become
 *       fields. The two tables named {@code c} are {@code cSmall} (the nine
 *       coefficients of the {@code |x| < 1/4} polynomial) and {@code c} (the
 *       five of the logarithm), {@code ch} and {@code cl} of
 *       {@code asAtanhZero} are {@code chZero} and {@code clZero}, and those of
 *       {@code asAtanhRefine} are {@code chRef} and {@code clRef}.
 *       {@code sizeof(db)/sizeof(db[0])} becomes {@code db.length}.
 *   <li>In {@code compute} and {@code asAtanhRefine} the union member
 *       {@code t.f} is read three and four times in a row and is materialised
 *       once as {@code tf}, as the mapping table allows from the third read on;
 *       the writes that follow still go through the bit pattern of {@code t}.
 *   <li>{@code __attribute__((noinline))}, the {@code u64} and
 *       {@code b64u64_u} typedefs are dropped.
 * </ul>
 */
public final strictfp class Atanh {
  private Atanh() {
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

  private static double adddd(double xh, double xl, double ch, double cl, double[] l) {
    double s = xh + ch, d = s - xh;
    l[0] = ((ch - d) + (xh + (d - s))) + (xl + cl);
    return s;
  }

  /* Note: in revision 085972b, we replaced the last two lines
     ch = ahhh + ahhl and *l = (ahhh - ch) + ahhl by fasttwosum (ahhh, ahhl, l).
     Indeed, these last two lines did emulate a FastTwoSum.
     However, they did emulate another variant of fasttwosum, with
     z = x - s and e = z + y.
     Note that these two variants differ when the fasttwosum condition
     |x| >= |y| is not satisfied.
     Take for example with precision 3 and rounding upwards, x=-7 and
     y=28. Then s = RU(x + y) = 24. With the first variant, z = RU(s-x) = 32
     and e = RU(y - z) = -4, thus s + e = 20. With the second variant,
     z = RU(x-s) = -28 and e = RU(z + y) = 0, thus s + e = 24. In this case,
     the first variant is closer to the sum x + y = 21.
     Still with precision 3 and rounding upwards, now take x=7 and
     y=-28. Then s = RU(x + y) = -20. With the first variant, z = RU(s-x) = -24
     and e = RU(y - z) = -4, thus s + e = -28. With the second variant,
     z = RU(x-s) = 28 and e = RU(z + y) = 0, thus s + e = -20. In this case,
     the second variant is closer to the sum x + y = -21.
  */
  private static double muldd_acc(double xh, double xl, double ch, double cl, double[] l) {
    double ahlh = ch*xl, alhh = cl*xh, ahhh = ch*xh, ahhl = Math.fma(ch, xh, -ahhh);
    ahhl += alhh + ahlh;
    return fasttwosum (ahhh, ahhl, l);
  }

  // same as muldd_acc, but without the fasttwosum() normalization
  private static double muldd(double xh, double xl, double ch, double cl, double[] l) {
    double ahlh = ch*xl, alhh = cl*xh, ahhh = ch*xh, ahhl = Math.fma(ch, xh, -ahhh);
    l[0] = ahhl + (alhh + ahlh);
    return ahhh;
  }

  private static double mulddd(double xh, double xl, double ch, double[] l) {
    double hh = xh*ch;
    l[0] = Math.fma(ch, xh, -hh) + xl*ch;
    return hh;
  }

  private static double polydd(double xh, double xl, int n, double[][] c, double[] l) {
    int i = n-1;
    double ch;
    double[] cl = new double[1];
    ch = fasttwosum(c[i][0], l[0], cl);
    cl[0] += c[i][1];
    while(--i>=0){
      ch = muldd_acc(xh, xl, ch, cl[0], cl);
      double th;
      double[] tl = new double[1];
      th = fasttwosum(c[i][0], ch, tl);
      ch = th;
      cl[0] += tl[0] + c[i][1];
    }
    l[0] = cl[0];
    return ch;
  }

  private static final double[][] chZero = {
    {0x1.5555555555555p-2, 0x1.5555555555555p-56},
    {0x1.999999999999ap-3, -0x1.999999999611cp-57},
    {0x1.2492492492492p-3, 0x1.2492490f76b25p-57},
    {0x1.c71c71c71c71cp-4, 0x1.c71cd5c38a112p-58},
    {0x1.745d1745d1746p-4, -0x1.7556c4165f4cap-59},
    {0x1.3b13b13b13b14p-4, -0x1.b893c3b36052ep-59},
    {0x1.1111111111105p-4, 0x1.4e1afd723ed1fp-59},
    {0x1.e1e1e1e1e2678p-5, -0x1.f86ea96fb1435p-59},
    {0x1.af286bc9f90ccp-5, 0x1.1e51a6e54fde9p-60},
    {0x1.8618618c779b6p-5, -0x1.ab913de95c3bfp-61},
    {0x1.642c84aa383ebp-5, 0x1.632e747641b12p-59},
    {0x1.47ae2d205013cp-5, -0x1.0c9617e7bcff2p-60},
    {0x1.2f664d60473f9p-5, 0x1.3adb3e2b7f35ep-61}
  };

  private static final double[] clZero = {
    0x1.1a9a91fd692afp-5, // degree 29
    0x1.06dfbb35e7f44p-5, // degree 31
    0x1.037bed4d7588fp-5, // degree 33
    0x1.5aca6d6d720d6p-6, // degree 35
    0x1.99ea5700d53a5p-5
  };

  private static final double[] cSmall = {
       0x1.999999999999ap-3, 0x1.2492492492244p-3, 0x1.c71c71c79715fp-4, 0x1.745d16f777723p-4,
       0x1.3b13ca4174634p-4, 0x1.110c9724989bdp-4, 0x1.e2d17608a5b2ep-5, 0x1.a0b56308cba0bp-5,
       0x1.fb6341208ad2ep-5};

  private static final int[][] B = {
    {301, 27565}, {7189, 24786},
    {13383, 22167}, {18923, 19696},
    {23845, 17361}, {28184, 15150},
    {31969, 13054}, {35231, 11064},
    {37996, 9173}, {40288, 7372},
    {42129, 5657}, {43542, 4020},
    {44546, 2457}, {45160, 962},
    {45399, -468}, {45281, -1838},
    {44821, -3151}, {44032, -4412},
    {42929, -5622}, {41522, -6786},
    {39825, -7905}, {37848, -8982},
    {35602, -10020}, {33097, -11020},
    {30341, -11985}, {27345, -12916},
    {24115, -13816}, {20661, -14685},
    {16989, -15526}, {13107, -16339},
    {9022, -17126}, {4740, -17889}
  };

  private static final double[] r1 = {
       0x1p+0, 0x1.f5076p-1, 0x1.ea4bp-1, 0x1.dfc98p-1, 0x1.d5818p-1, 0x1.cb72p-1, 0x1.c199cp-1,
       0x1.b7f76p-1, 0x1.ae8ap-1, 0x1.a5504p-1, 0x1.9c492p-1, 0x1.93738p-1, 0x1.8ace6p-1,
       0x1.8258ap-1, 0x1.7a114p-1, 0x1.71f76p-1, 0x1.6a09ep-1, 0x1.6247ep-1, 0x1.5ab08p-1,
       0x1.5342cp-1, 0x1.4bfdap-1, 0x1.44e08p-1, 0x1.3dea6p-1, 0x1.371a8p-1, 0x1.306fep-1,
       0x1.29e9ep-1, 0x1.2387ap-1, 0x1.1d488p-1, 0x1.172b8p-1, 0x1.11302p-1, 0x1.0b558p-1,
       0x1.059bp-1, 0x1p-1};

  private static final double[] r2 = {
       0x1p+0, 0x1.ffa74p-1, 0x1.ff4eap-1, 0x1.fef62p-1, 0x1.fe9dap-1, 0x1.fe452p-1, 0x1.fdeccp-1,
       0x1.fd946p-1, 0x1.fd3c2p-1, 0x1.fce3ep-1, 0x1.fc8bcp-1, 0x1.fc33ap-1, 0x1.fbdbap-1,
       0x1.fb83ap-1, 0x1.fb2bcp-1, 0x1.fad3ep-1, 0x1.fa7c2p-1, 0x1.fa246p-1, 0x1.f9ccap-1,
       0x1.f975p-1, 0x1.f91d8p-1, 0x1.f8c6p-1, 0x1.f86e8p-1, 0x1.f8172p-1, 0x1.f7bfep-1,
       0x1.f768ap-1, 0x1.f7116p-1, 0x1.f6ba4p-1, 0x1.f6632p-1, 0x1.f60c2p-1, 0x1.f5b52p-1,
       0x1.f55e4p-1, 0x1.f5076p-1};

  private static final double[][] l1 = {
    {0x0p+0, 0x0p+0},
    {-0x1.532c1269e2038p-27, 0x1.62e5p-7},
    {0x1.ce42d81b54e84p-27, 0x1.62e3cp-6},
    {-0x1.25826f815ec3dp-26, 0x1.0a2acp-5},
    {0x1.0db1b1e7cee11p-26, 0x1.62e4ap-5},
    {-0x1.1f3a8c6c95003p-26, 0x1.bb9dcp-5},
    {-0x1.774cd4fb8c30dp-26, 0x1.0a2b2p-4},
    {0x1.452e56c030a0ap-29, 0x1.3687fp-4},
    {0x1.6b63c4966a79ap-28, 0x1.62e41p-4},
    {-0x1.b20a21ccb525ep-28, 0x1.8f40ap-4},
    {0x1.4006cfb3d8f85p-26, 0x1.bb9d1p-4},
    {-0x1.cdb026b310c41p-26, 0x1.e7f9bp-4},
    {-0x1.69124fdc0f16dp-26, 0x1.0a2b08p-3},
    {-0x1.084656cdc2727p-26, 0x1.205958p-3},
    {-0x1.376fa8b0357fdp-26, 0x1.3687cp-3},
    {0x1.e56ae55a47b4ap-28, 0x1.4cb5e8p-3},
    {0x1.070ff8834eeb4p-26, 0x1.62e44p-3},
    {0x1.623516109f4fep-26, 0x1.79129p-3},
    {-0x1.ec656b95fbdacp-29, 0x1.8f40bp-3},
    {0x1.f0ca2e729f51p-28, 0x1.a56ed8p-3},
    {-0x1.7d260a858354ap-26, 0x1.bb9d68p-3},
    {0x1.e7279075503d3p-27, 0x1.d1cb9p-3},
    {0x1.39e1a0a503873p-27, 0x1.e7f9dp-3},
    {0x1.cd86d7b87c3d6p-26, 0x1.fe27d8p-3},
    {0x1.060ab88de341ep-26, 0x1.0a2b24p-2},
    {0x1.20a860d3f939p-28, 0x1.154244p-2},
    {-0x1.dacee95fc2f1p-27, 0x1.205974p-2},
    {0x1.45de3a86e0acap-26, 0x1.2b707p-2},
    {0x1.c164cbfb991afp-27, 0x1.3687bp-2},
    {0x1.d3f66b24225efp-26, 0x1.419ec4p-2},
    {0x1.fc023efa144bap-26, 0x1.4cb5f8p-2},
    {0x1.086a8af6f26cp-28, 0x1.57cd28p-2},
    {-0x1.05c610ca86c39p-30, 0x1.62e43p-2}
  };

  private static final double[][] l2 = {
    {0x0p+0, 0x0p+0},
    {-0x1.37e152a129e4ep-28, 0x1.632p-12},
    {-0x1.3f6c916b8be9cp-26, 0x1.63p-11},
    {0x1.20505936739d5p-26, 0x1.0a24p-10},
    {-0x1.23e2e8cb541bap-26, 0x1.62dcp-10},
    {-0x1.acb7983ac4f5ep-32, 0x1.bbap-10},
    {0x1.6f7c7689c63aep-28, 0x1.0a2ap-9},
    {0x1.f5ca695b4c58bp-30, 0x1.368cp-9},
    {-0x1.c6c18bd953226p-27, 0x1.62e6p-9},
    {0x1.7a516c34846bdp-26, 0x1.8f46p-9},
    {-0x1.f3b83dd8b853p-27, 0x1.bbap-9},
    {-0x1.c3459046e4e57p-31, 0x1.e8p-9},
    {0x1.b5c7e34cb79f6p-38, 0x1.0a2cp-8},
    {-0x1.2487e9af9a692p-27, 0x1.205cp-8},
    {0x1.f21bbc4ad79cep-26, 0x1.3687p-8},
    {-0x1.550ffc857b731p-29, 0x1.4cb7p-8},
    {0x1.87458ec1b7b34p-27, 0x1.62e2p-8},
    {0x1.103d4fe83ee81p-26, 0x1.7911p-8},
    {0x1.810483d3b398cp-27, 0x1.8f44p-8},
    {-0x1.2085cb340608ep-27, 0x1.a573p-8},
    {0x1.12698a119c42fp-26, 0x1.bb9dp-8},
    {-0x1.edb8c172b4c33p-26, 0x1.d1ccp-8},
    {-0x1.8b55b87a5e238p-26, 0x1.e7fep-8},
    {0x1.be5e17763f78ap-26, 0x1.fe2bp-8},
    {-0x1.c2d496790073ep-30, 0x1.0a2a8p-7},
    {0x1.6542f523abeecp-26, 0x1.1541p-7},
    {-0x1.b7fdbe5b193f8p-26, 0x1.205ap-7},
    {0x1.fa4d42fe30c7cp-26, 0x1.2b7p-7},
    {0x1.0d46ad04adc86p-26, 0x1.36888p-7},
    {-0x1.1c22d02d17c4cp-26, 0x1.419fp-7},
    {0x1.a7d1e330dcccep-30, 0x1.4cb7p-7},
    {0x1.187025e656ba3p-31, 0x1.57cdp-7},
    {-0x1.532c1269e2038p-27, 0x1.62e5p-7}
  };

  private static final double[] c = {
       -0x1p+0, 0x1.555555555553p+0, -0x1.fffffffffffap+0, 0x1.99999e33a6366p+1,
       -0x1.555559ef9525fp+2};

  private static final double[][] db = {
    {0x1.2dbb7b1c91363p-2, 0x1.36f33d51c264dp-2, 0x1p-56},
    {0x1.c493dc899e4a5p-2, 0x1.e611aa58ab608p-2, -0x1p-56}
  };

  private static final double[] t1 = {
       0x1p+0, 0x1.ea4afap-1, 0x1.d5818ep-1, 0x1.c199bep-1, 0x1.ae89f98p-1, 0x1.9c4918p-1,
       0x1.8ace54p-1, 0x1.7a1147p-1, 0x1.6a09e68p-1, 0x1.5ab07ep-1, 0x1.4bfdad8p-1, 0x1.3dea65p-1,
       0x1.306fe08p-1, 0x1.2387a7p-1, 0x1.172b84p-1, 0x1.0b5587p-1, 0x1p-1};

  private static final double[] t2 = {
       0x1p+0, 0x1.fe9d968p-1, 0x1.fd3c228p-1, 0x1.fbdba38p-1, 0x1.fa7c18p-1, 0x1.f91d8p-1,
       0x1.f7bfdbp-1, 0x1.f663278p-1, 0x1.f507658p-1, 0x1.f3ac948p-1, 0x1.f252b38p-1,
       0x1.f0f9c2p-1, 0x1.efa1bfp-1, 0x1.ee4aaap-1, 0x1.ecf483p-1, 0x1.eb9f488p-1};

  private static final double[] t3 = {
       0x1p+0, 0x1.ffe9d2p-1, 0x1.ffd3a58p-1, 0x1.ffbd798p-1, 0x1.ffa74e8p-1, 0x1.ff91248p-1,
       0x1.ff7afb8p-1, 0x1.ff64d38p-1, 0x1.ff4eac8p-1, 0x1.ff38868p-1, 0x1.ff22618p-1,
       0x1.ff0c3dp-1, 0x1.fef61ap-1, 0x1.fedff78p-1, 0x1.fec9d68p-1, 0x1.feb3b6p-1};

  private static final double[] t4 = {
       0x1p+0, 0x1.fffe9dp-1, 0x1.fffd3ap-1, 0x1.fffbd78p-1, 0x1.fffa748p-1, 0x1.fff9118p-1,
       0x1.fff7ae8p-1, 0x1.fff64cp-1, 0x1.fff4e9p-1, 0x1.fff386p-1, 0x1.fff2238p-1,
       0x1.fff0c08p-1, 0x1.ffef5d8p-1, 0x1.ffedfa8p-1, 0x1.ffec98p-1, 0x1.ffeb35p-1};

  private static final double[][][] LL = {
    {
      {0x0p+0, 0x0p+0, 0x0p+0},
      {0x1.62e432b24p-6, -0x1.745af34bb54b8p-42, -0x1.17e3ec05cde7p-97},
      {0x1.62e42e4a8p-5, 0x1.111a4eadf312p-44, 0x1.cff3027abb119p-93},
      {0x1.0a2b233f1p-4, -0x1.88ac4ec78af8p-42, 0x1.4fa087ca75dfdp-93},
      {0x1.62e43056cp-4, 0x1.6bd65e8b0b7p-46, -0x1.b18e160362c24p-95},
      {0x1.bb9d3cbd6p-4, 0x1.de14aa55ec2bp-42, -0x1.c6ac3f1862a6bp-94},
      {0x1.0a2b244dap-3, 0x1.94def487fea7p-42, -0x1.dead1a4581acfp-94},
      {0x1.3687aa9b78p-3, 0x1.9cec9a50db22p-43, 0x1.34a70684f8e0ep-93},
      {0x1.62e42fabap-3, -0x1.d69047a3aebp-44, -0x1.4e061f79144e2p-95},
      {0x1.8f40b56d28p-3, 0x1.de7d755fd2e2p-42, 0x1.bdc7ecf001489p-94},
      {0x1.bb9d3b61fp-3, 0x1.c14f1445b12p-46, 0x1.a1d78cbdc5b58p-93},
      {0x1.e7f9c11f08p-3, -0x1.6e3e0000dae7p-43, 0x1.6a4559fadde98p-94},
      {0x1.0a2b242ec4p-2, 0x1.bb7cf852a5fe8p-42, 0x1.a6aef11ee43bdp-93},
      {0x1.205966c764p-2, 0x1.ad3a5f214294p-45, 0x1.5cc344fa10652p-93},
      {0x1.3687a98aacp-2, 0x1.1623671842fp-45, -0x1.0b428fe1f9e43p-94},
      {0x1.4cb5ec93f4p-2, 0x1.3d50980ea513p-42, 0x1.67f0ea083b1c4p-93},
      {0x1.62e42fefa4p-2, -0x1.8432a1b0e264p-44, 0x1.803f2f6af40f3p-93}
    },
    {
      {0x0p+0, 0x0p+0, 0x0p+0},
      {0x1.62e462b4p-10, 0x1.061d003b97318p-42, 0x1.d7faee66a2e1ep-93},
      {0x1.62e44c92p-9, 0x1.95a7bff5e239p-42, -0x1.f7e788a87135p-95},
      {0x1.0a2b1e33p-8, 0x1.2a3a1a65aa3ap-43, -0x1.54599c9605442p-93},
      {0x1.62e4367cp-8, -0x1.4a995b6d9ddcp-45, -0x1.56bb79b254f33p-100},
      {0x1.bb9d449ap-8, 0x1.8a119c42e9bcp-42, -0x1.8ecf7d8d661f1p-93},
      {0x1.0a2b1f19p-7, 0x1.8863771bd10a8p-42, 0x1.e9731de7f0155p-94},
      {0x1.3687ad11p-7, 0x1.e026a347ca1c8p-42, 0x1.fadc62522444dp-97},
      {0x1.62e436f28p-7, 0x1.25b84f71b70b8p-42, -0x1.fcb3f98612d27p-96},
      {0x1.8f40b7b38p-7, -0x1.62a0a4fd4758p-43, 0x1.3cb3c35d9f6a1p-93},
      {0x1.bb9d3abbp-7, -0x1.0ec48f94d786p-42, -0x1.6b47d410e4cc7p-93},
      {0x1.e7f9bb23p-7, 0x1.e4415cbc97ap-43, -0x1.3729fdb677231p-93},
      {0x1.0a2b22478p-6, -0x1.cb73f4505b03p-42, -0x1.1b3b3a3bc370ap-93},
      {0x1.2059691e8p-6, -0x1.abcc3412f264p-43, -0x1.fe6e998e48673p-95},
      {0x1.3687a768p-6, -0x1.43901e5c97a9p-42, 0x1.b54cdd52a5d88p-96},
      {0x1.4cb5eb5d8p-6, -0x1.8f106f00f13b8p-42, -0x1.8f793f5fce148p-93},
      {0x1.62e432b24p-6, -0x1.745af34bb54b8p-42, -0x1.17e3ec05cde7p-97}
    },
    {
      {0x0p+0, 0x0p+0, 0x0p+0},
      {0x1.62e7bp-14, -0x1.868625640a68p-44, -0x1.34bf0db910f65p-93},
      {0x1.62e35f6p-13, -0x1.2ee3d96b696ap-43, 0x1.a2948cd558655p-94},
      {0x1.0a2b4b2p-12, 0x1.53edbcf1165p-47, -0x1.cfc26ccf6d0e4p-97},
      {0x1.62e4be1p-12, 0x1.783e334614p-52, -0x1.04b96da30e63ap-93},
      {0x1.bb9e085p-12, -0x1.60785f20acb2p-43, -0x1.f33369bf7dff1p-96},
      {0x1.0a2b94dp-11, 0x1.fd4b3a273353p-42, -0x1.685a35575eff1p-96},
      {0x1.368810f8p-11, 0x1.7ded26dc813p-47, -0x1.4c4d1abca79bfp-96},
      {0x1.62e47878p-11, 0x1.7d2bee9a1f63p-42, 0x1.860233b7ad13p-93},
      {0x1.8f40cb48p-11, -0x1.af034eaf471cp-42, 0x1.ae748822d57b7p-94},
      {0x1.bb9d094p-11, -0x1.7a223013a20fp-42, -0x1.1e499087075b6p-93},
      {0x1.e7fa32c8p-11, -0x1.b2e67b1b59bdp-43, -0x1.54a41eda30fa6p-93},
      {0x1.0a2b237p-10, -0x1.7ad97ff4ac7ap-44, 0x1.f932da91371ddp-93},
      {0x1.2059a338p-10, -0x1.96422d90df4p-44, -0x1.90800fbbf2ed3p-94},
      {0x1.36879824p-10, 0x1.0f9054001812p-44, 0x1.9567e01e48f9ap-93},
      {0x1.4cb602cp-10, -0x1.0d709a5ec0b5p-43, 0x1.253dfd44635d2p-94},
      {0x1.62e462b4p-10, 0x1.061d003b97318p-42, 0x1.d7faee66a2e1ep-93}
    },
    {
      {0x0p+0, 0x0p+0, 0x0p+0},
      {0x1.63007cp-18, -0x1.db0e38e5aaaap-43, 0x1.259a7b94815b9p-93},
      {0x1.6300f6p-17, 0x1.2b1c75580438p-44, 0x1.78cabba01e3e4p-93},
      {0x1.0a2115p-16, -0x1.5ff223730759p-42, 0x1.8074feacfe49dp-95},
      {0x1.62e1ecp-16, -0x1.85d6f6487ce4p-45, 0x1.05485074b9276p-93},
      {0x1.bba301p-16, -0x1.af5d58a7c921p-43, -0x1.30a8c0fd2ff5fp-93},
      {0x1.0a32298p-15, 0x1.590faa0883bdp-43, 0x1.95e9bda999947p-93},
      {0x1.3682f1p-15, 0x1.f0224376efaf8p-42, -0x1.5843c0db50d1p-93},
      {0x1.62e3d8p-15, -0x1.142c13daed4ap-43, 0x1.c68a61183ce87p-93},
      {0x1.8f44dd8p-15, -0x1.aa489f399931p-43, 0x1.11c5c376854eap-94},
      {0x1.bb9601p-15, 0x1.9904d8b6a3638p-42, 0x1.8c89554493c8fp-93},
      {0x1.e7f744p-15, 0x1.5785ddbe7cba8p-42, 0x1.e7ff3cde7d70cp-94},
      {0x1.0a2c53p-14, -0x1.6d9e8780d0d5p-43, 0x1.ad9c178106693p-94},
      {0x1.205d134p-14, -0x1.214a2e893fccp-43, 0x1.548a9500c9822p-93},
      {0x1.3685e28p-14, 0x1.e23588646103p-43, 0x1.2a97b26da2d88p-94},
      {0x1.4cb6c18p-14, 0x1.2b7cfcea9e0d8p-42, -0x1.5095048a6b824p-93},
      {0x1.62e7bp-14, -0x1.868625640a68p-44, -0x1.34bf0db910f65p-93}
    }
  };

  private static final double[][] chRef = {
    {0x1p-1, 0x1.24b67ee516e3bp-111},
    {-0x1p-2, -0x1.932ce43199a8dp-110},
    {0x1.5555555555555p-3, 0x1.55540c15cf91fp-57}
  };

  private static final double[] clRef = {-0x1p-3, 0x1.9999999a0754fp-4, -0x1.55555555c3157p-4};

  private static double asAtanhZero(double x) {
    double x2 = x*x , x2l = Math.fma(x, x,-x2);
    double[] y2 = new double[1];
    y2[0] = x2 * (clZero[0] + x2 * (clZero[1] + x2 * (clZero[2] + x2 * (clZero[3]
        + x2 * (clZero[4])))));
    double[] y1 = new double[1];
    y1[0] = polydd(x2, x2l, 13, chZero, y2);
    y1[0] = mulddd(y1[0], y2[0], x, y2);
    y1[0] = muldd_acc(x2, x2l, y1[0], y2[0], y2);
    double y0 = fasttwosum(x, y1[0], y1);
    y1[0] = fasttwosum(y1[0], y2[0], y2);
    /* We have 22 failures (only for RNDN, 11 up to sign) if we disable this
       check, all with 53 or 54 identical bits after the round bit.
       For example x=0x1.cc7092205cfafp-10 we have y0=0x1.cc70b12857108p-10
       and y1=-0x1p-63. */
    long t = Double.doubleToRawLongBits(y1[0]);
    if((t&(~0L>>>12))==0){
      long w = Double.doubleToRawLongBits(y2[0]);
      if((w^t)>>63 != 0)
        t--;
      else
        t++;
      y1[0] = Double.longBitsToDouble(t);
    }
    return y0 + y1[0];
  }

  public static double compute(double x) {
    double ax = Math.abs(x);
    long ix = Double.doubleToRawLongBits(ax);
    long aix = ix;
    if(Long.compareUnsigned(aix, 0x3ff0000000000000L) >= 0){ // |x| >= 1
      if(aix==0x3ff0000000000000L){ // |x| = 1
        return Math.copySign(1, x) / 0.0;
      }
      if(Long.compareUnsigned(aix, 0x7ff0000000000000L) > 0) return x + x; // nan
      return Math.sqrt(-1.0);
    }

    if(Long.compareUnsigned(aix, 0x3fd0000000000000L) < 0){ // |x| < 0x1p-2
      // atanh(x) rounds to x to nearest for |x| < 0x1.d12ed0af1a27fp-27
      if(Long.compareUnsigned(aix, 0x3e4d12ed0af1a27fL) < 0) {
        // |x| < 0x1.d12ed0af1a27fp-27
        /* We have underflow exactly when 0 < |x| < 2^-1022:
           for RNDU, atanh(2^-1022-2^-1074) would round to 2^-1022-2^-1075
           with unbounded exponent range */
        return Math.fma(x,0x1p-55,x);
      }
      /* checked exhautively this branch (with and without FMA):
       * for 0x1.d12ed0af1a27fp-27 <= x < 2^-24
       */
      double x2 = x * x;
      /* the polynomial c(x) = c[0]+c[1]*x^2+...+c[8]*x^16 is a minimax
         approximation of (atanh(x) - x - x^3/3)/x^5, which yields for
         p(x) = x + (0x1.5555555555555p-2 + 0x1.5555555555555p-56) x^3 + x^5 c(x)
         an approximation of atanh(x) with relative error <= 2^-64.377
         on [0x1.d12ed0af1a27fp-27,1/4], cf atanh.sollya */
      double dx2 = Math.fma(x,x,-x2);
      double x4 = x2*x2, x3 = x2*x, x8 = x4*x4;
      double dx3 = Math.fma(x2,x,-x3) + dx2*x;
      /* following the C standard, the following is equivalent to (a + b) + c,
         where a = c[0] + x2*c[1], b = x4*(...) and c = x8*(...) */
      double p = (cSmall[0] + x2*cSmall[1]) + x4*(cSmall[2] + x2*cSmall[3])
          + x8*((cSmall[4] + x2*cSmall[5]) + x4*(cSmall[6] + x2*cSmall[7]) + x8*cSmall[8]);
      double t = Math.fma (x2, p, 0x1.5555555555555p-56);
      double[] pl = new double[1];
      double ph = fasttwosum(0x1.5555555555555p-2, t, pl);
      ph = muldd(ph, pl[0],  x3, dx3, pl);
      double[] tl = new double[1];
      ph = fasttwosum(x,ph,tl);
      pl[0] += tl[0];
      double eps = x*(x4*0x1.dp-53 + 0x1p-103);
      // revision 221543c fails with 0.737*eps and x=0x1.2f67d96be6eafp-9 (rndu)
      double lb = ph + (pl[0] - eps), ub = ph + (pl[0] + eps);
      if(lb == ub) return lb;
      return asAtanhZero(x);
    }

    /* We have performed an exhaustive search of branch |x| >= 0.25 on 221543c
       both with default CFLAGS (with FMA) and with CFLAGS="-O3 -march=x86-64"
       (no FMA). We found no failure, except the ones in atanh.wc which are fixed
       in a later commit. */
    double[] pl = new double[1];
    double ph = fasttwosum(1,ax,pl);
    double[] ql = new double[1];
    double qh = fasttwosub(1,ax,ql);
    double iqh = 1/qh;
    double th = ph*iqh;
    double[] tl = new double[1];
    tl[0] = Math.fma(ph,iqh,-th) + (pl[0] + ph*(Math.fma(-qh,iqh,1) - ql[0]*iqh))*iqh;

    long t = Double.doubleToRawLongBits(th);
    int ex = (int)(t>>>52), e = ex - 0x3ff;
    t &= ~0L>>>12;
    double ed = e;
    long i = t>>>(52-5);
    long d = t & (~0L>>>17);
    long j = (t + ((long)B[(int)i][0]<<33) + ((long)B[(int)i][1]*(d>>16)))>>>(52-10);
    t |= 0x3ffL<<52;
    int i1 = (int)(j>>5), i2 = (int)(j&0x1f);
    double tf = Double.longBitsToDouble(t);
    double r = (0.5*r1[i1])*r2[i2], dx = Math.fma(r, tf, -0.5), dx2 = dx*dx;
    double rx = r*tf, dxl = Math.fma(r,tf,-rx);
    double f = dx2*((c[0] + dx*c[1]) + dx2*(c[2] + dx*c[3] + dx2*c[4]));
    double l2h = 0x1.62e42fefa3ap-2, l2l = -0x1.0ca86c3898dp-50;
    double[] ll = new double[1];
    double lh = (l1[i1][1] + l2[i2][1]) + l2h*ed;
    lh = fasttwosum(lh,rx-0.5,ll);
    ll[0] += l2l*ed + (l1[i1][0] + l2[i2][0]) + dxl + 0.5*tl[0]/th;
    ll[0] += f;
    lh *= Math.copySign(1,x);
    ll[0] *= Math.copySign(1,x);
    double eps = 38e-24 + dx2*0x1p-49;
    // revision bbe3f8c fails with 0.989 * eps and x=0x1.ebf0bfefa727dp-1 (rndz)
    double lb = lh + (ll[0] - eps), ub = lh + (ll[0] + eps);
    if(lb==ub) return lb;
    th = fasttwosum(th, tl[0], tl);
    return asAtanhRefine(x, th, tl[0], 0x1.71547652b82fep+1*Math.abs(lh + ll[0]));
  }

  private static double asAtanhDatabase(double x, double f) {
    double ax = Math.abs(x), sgn = Math.copySign(1,x);
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

  // a is an approximation of atanh(x)/log(2)
  private static double asAtanhRefine(double x, double zh, double zl, double a) {
    long t = Double.doubleToRawLongBits(zh);
    int ex = (int)(t>>>52), e = ex-0x3ff;
    t &= ~0L>>>12;
    t |= 0x3ffL<<52;
    double ed = e;
    long v = Double.doubleToRawLongBits(a - ed + 0x1.00008p+0);
    long i = (v - (0x3ffL<<52))>>>(52-16);
    int i1 = (int)(i>>12), i2 = (int)((i>>8)&0xf), i3 = (int)((i>>4)&0xf), i4 = (int)(i&0xf);
    double l20 = 0x1.62e42fefa3ap-2, l21 = -0x1.0ca86c3898dp-50, l22 = 0x1.f97b57a079ap-104;
    double el2 = l22*ed, el1 = l21*ed, el0 = l20*ed;
    double[] L = new double[3];
    L[0] = LL[0][i1][0] + LL[1][i2][0] + (LL[2][i3][0] + LL[3][i4][0]) + el0;
    L[1] = LL[0][i1][1] + LL[1][i2][1] + (LL[2][i3][1] + LL[3][i4][1]);
    L[2] = LL[0][i1][2] + LL[1][i2][2] + (LL[2][i3][2] + LL[3][i4][2]);
    double t12 = t1[i1]*t2[i2], t34 = t3[i3]*t4[i4];
    double th = t12*t34, tl = Math.fma(t12,t34,-th);
    double tf = Double.longBitsToDouble(t);
    double dh = th*tf, dl = Math.fma(th,tf,-dh);
    double sh = tl*tf;
    double[] sl = new double[1];
    sl[0] = Math.fma(tl,tf,-sh);
    double[] xl = new double[1];
    double xh = fasttwosum(dh-1, dl, xl);
    t = Double.doubleToRawLongBits(zl);
    t -= (long)e<<52;
    xl[0] += th*Double.longBitsToDouble(t);
    xh = adddd(xh, xl[0], sh, sl[0], xl);
    sl[0] = xh*(clRef[0] + xh*(clRef[1] + xh*clRef[2]));
    sh = polydd(xh, xl[0], 3, chRef, sl);
    sh = muldd_acc(xh, xl[0], sh, sl[0], sl);
    sh = adddd(sh, sl[0], el1, el2, sl);
    sh = adddd(sh, sl[0], L[1], L[2], sl);
    double[] v2 = new double[1];
    double v0 = fasttwosum(L[0], sh, v2);
    double v1 = fasttwosum(v2[0], sl[0], v2);
    t = Double.doubleToRawLongBits(v1);
    if((t&(~0L>>>12))==0){ // is v1 a power of 2?
      /* we enter here only for 5 values up to sign:
       * 0x1.110e96a6c2d96p-2: 49 identical bits after round bit
       * 0x1.2dbb7b1c91363p-2: 50 identical bits after round bit
       * 0x1.c493dc899e4a5p-2: 50 identical bits after round bit (only with FMA)
       * 0x1.dc3fe1b524821p-2: 49 identical bits after round bit (only without FMA)
       * 0x1.64279e7c7064bp-1: 50 identical bits after round bit (only without FMA)
       In all cases v1 and v2 are of same sign, thus we add sign(v1)*ulp(v1) to v1.
       */
      t++;
      v1 = Double.longBitsToDouble(t);
    }
    long t0 = Double.doubleToRawLongBits(v0);
    long er = ((t + 1) & (~0L>>>12)), de = ((t0>>>52)&0x7ff) - ((t>>>52)&0x7ff);
    v0 *= Math.copySign(1,x);
    v1 *= Math.copySign(1,x);
    double res = v0 + v1;
    if(de>104 || er<3)
      return asAtanhDatabase(x,res);
    return res;
  }
}
