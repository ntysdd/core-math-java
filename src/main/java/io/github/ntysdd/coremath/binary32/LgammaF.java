/*
 * Correctly rounded natural logarithm of the gamma function for binary32
 * values, with the sign of gamma.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/lgamma/lgammaf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_lgammaf -> LgammaF.compute
 *
 * Copyright (c) 2025 Alexei Sibidanov <sibid@uvic.ca>
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
 * Correctly rounded {@code lgamma} of a binary32 value and the sign of
 * {@code gamma}, the translation of the CORE-MATH {@code cr_lgammaf}.
 *
 * <p>{@code compute} returns {@code log|gamma(x)|} and writes the sign of
 * {@code gamma(x)} to {@code signgamp[0]} when {@code signgamp} is not null,
 * which is the information that the C entry point publishes through the
 * process-global {@code signgam}. Apart from the special values, the function
 * splits at {@code |x| = 0.5}: below it a rational approximation and then
 * {@code log} are enough for {@code |x| < 0.03125} and for
 * {@code |x| >= 0.03125} the argument is shifted by one and evaluated by the
 * piece-wise approximation, while above it the Stirling expansion, a short
 * expansion in {@code 1/x}, or the polynomial in {@code x} is used, with the
 * reflection formula for negative arguments. The 27-row {@code tb} table
 * replaces the results that fall within 31 of a rounding boundary, tested on
 * the low 28 bits of the {@code double} result before the conversion to
 * {@code float}.
 *
 * <p>{@code signgamp} is the only deviation that is not purely mechanical, and
 * it is a deliberate one. The C entry point assigns the global
 * {@code signgam} at six places; two unsynchronised calls in different threads
 * are therefore a data race on a non-atomic object, which C11 5.1.2.4p25 makes
 * undefined behaviour, and the upstream test
 * {@code src/binary32/support/check_exhaustive.c} says so in as many words:
 * "This test should be performed in single-thread mode, since signgam is not
 * thread-local." AGENTS.md forbids static mutable state, and Java has no
 * {@code signgam}, so the sign is passed out through a caller-owned array
 * instead: the port has no shared state, and a null array means the caller does
 * not need the sign. Every other deviation is the usual one:
 * <ul>
 *   <li>{@code __builtin_floorf} is {@code (float) Math.floor},
 *       {@code __builtin_fabsf} is {@link Math#abs}, {@code __builtin_fma} is
 *       {@link Math#fma}, {@code __builtin_fmaf} is {@link Math#fma} with the
 *       result narrowed, and {@code __builtin_expect (c, n)} is {@code c}.
 *   <li>The {@code errno} assignments are dropped, together with the
 *       {@code #ifdef CORE_MATH_SUPPORT_ERRNO} blocks that contain nothing
 *       else; {@code #pragma STDC FENV_ACCESS ON} goes with them.
 *   <li>The {@code b32u32_u} and {@code b64u64_u} unions become {@code int} and
 *       {@code long} bit patterns through {@link Float#floatToRawIntBits},
 *       {@link Double#doubleToRawLongBits} and
 *       {@link Double#longBitsToDouble}.
 *   <li>{@code 1.0f/0.0f}, which the C source evaluates for the negative
 *       integers to raise {@code FE_DIVBYZERO}, is
 *       {@link Float#POSITIVE_INFINITY}; {@code x * x} and {@code x + x} are
 *       kept, since they only propagate the argument.
 *   <li>Every {@code uint32_t} comparison goes through
 *       {@link Integer#compareUnsigned}, including the {@code t.u < tb[i].x.u}
 *       of the binary search; the {@code tl <= 31u} test over a
 *       {@code uint64_t} goes through {@link Long#compareUnsigned}.
 *   <li>The {@code struct {b32u32_u x; float f, df;} tb[]} becomes the three
 *       columns {@code tbX}, {@code tbF} and {@code tbDf}, with the binary32
 *       value of every row written as a 24-bit hex literal so that it round
 *       trips; {@code sizeof(tb)/sizeof(tb[0])} is {@code tbX.length}.
 *   <li>The five {@code static const double c[]} of the file and the two
 *       {@code rn}/{@code rd} pairs get distinct names ({@code cSinpi},
 *       {@code cLn}, {@code cIz1}, {@code cIz2}, {@code cIz3}, {@code rnTiny},
 *       {@code rdTiny}, {@code rnPoly}, {@code rdPoly}, {@code cExc1},
 *       {@code cExc2}, {@code cExc3}), each with a comment naming the C
 *       declaration it comes from.
 *   <li>{@code int i = (t.u>>48)&0xf} and the two {@code int} truncations
 *       carry the explicit casts, and the two implicit narrowings of the result
 *       carry {@code (float)}. The {@code rn} and {@code rd} helpers take the
 *       table as a {@code double[]} and index it from zero.
 * </ul>
 */
public strictfp class LgammaF {

  private LgammaF() {
  }

  // static const double c[] of as_sinpi()
  private static final double[] cSinpi =
      {0x1p+2, -0x1.de9e64df22ea4p+1, 0x1.472be122401f8p+0, -0x1.d4fcd82df91bp-3,
       0x1.9f05c97e0aab2p-6, -0x1.f3091c427b611p-10, 0x1.b22c9bfdca547p-14, -0x1.15484325ef569p-18};

  // static const double c[] of as_ln()
  private static final double[] cLn =
      {0x1.fffffffffff24p-1, -0x1.ffffffffd1d67p-2, 0x1.55555537802dep-2, -0x1.ffffeca81b866p-3,
       0x1.999611761d772p-3, -0x1.54f3e581b61bfp-3, 0x1.1e642b4cb5143p-3, -0x1.9115a5af1e1edp-4};

  private static final double[] il =
      {0x1.59caeec280116p-57, 0x1.f0a30c01162aap-5, 0x1.e27076e2af2ebp-4, 0x1.5ff3070a793d6p-3,
       0x1.c8ff7c79a9a2p-3, 0x1.1675cababa60fp-2, 0x1.4618bc21c5ec2p-2, 0x1.739d7f6bbd007p-2,
       0x1.9f323ecbf984dp-2, 0x1.c8ff7c79a9a21p-2, 0x1.f128f5faf06ecp-2, 0x1.0be72e4252a83p-1,
       0x1.1e85f5e7040d1p-1, 0x1.307d7334f10bep-1, 0x1.41d8fe84672afp-1, 0x1.52a2d265bc5abp-1};

  private static final double[] ix =
      {0x1p+0, 0x1.e1e1e1e1e1e1ep-1, 0x1.c71c71c71c71cp-1, 0x1.af286bca1af28p-1,
       0x1.999999999999ap-1, 0x1.8618618618618p-1, 0x1.745d1745d1746p-1, 0x1.642c8590b2164p-1,
       0x1.5555555555555p-1, 0x1.47ae147ae147bp-1, 0x1.3b13b13b13b14p-1, 0x1.2f684bda12f68p-1,
       0x1.2492492492492p-1, 0x1.1a7b9611a7b96p-1, 0x1.1111111111111p-1, 0x1.0842108421084p-1};

  // the x member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbX = {
      0x1.ecf3fep-73f, 0x1.108a5ap-66f, 0x1.a68bbcp-42f, 0x1.ddfd06p-12f, 0x1.f8a754p-9f,
      0x1.8d16b2p+5f, 0x1.359e0ep+10f, 0x1.a82a2cp+13f, 0x1.62c646p+14f, 0x1.7f298p+31f,
      0x1.a45ea4p+33f, 0x1.f9413ep+76f, 0x1.dcbbaap+99f, 0x1.58ace8p+112f, 0x1.87bdfp+115f,
      -0x1.25cb66p-123f, -0x1.ecf3fep-73f, -0x1.108a5ap-66f, -0x1.f51c8ep-49f, -0x1.d85bfep-43f,
      -0x1.437e74p-40f, -0x1.ade594p-30f, -0x1.c2f04p-30f, -0x1.580c1ep+1f, -0x1.69d628p+3f,
      -0x1.627346p+7f, -0x1.efc2a2p+14f};

  // the f member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbF = {
      0x1.8f8e5ap+5f, 0x1.6d7b18p+5f, 0x1.c9c6e8p+4f, 0x1.ec5ba8p+2f, 0x1.63acc2p+2f,
      0x1.1e4b4ep+7f, 0x1.d9ad02p+12f, 0x1.c38036p+16f, 0x1.9075bep+17f, 0x1.f44946p+35f,
      0x1.25dcbcp+38f, 0x1.9d5ab4p+82f, 0x1.fc5772p+105f, 0x1.9e4f66p+118f, 0x1.e465aep+121f,
      0x1.547a44p+6f, 0x1.8f8e5ap+5f, 0x1.6d7b18p+5f, 0x1.0a572ap+5f, 0x1.d31592p+4f,
      0x1.b7dec2p+4f, 0x1.446ab2p+4f, 0x1.43a6f6p+4f, -0x1.5787c6p-4f, -0x1.0eac2ap+4f,
      -0x1.73235ep+9f, -0x1.222dbcp+18f};

  // the df member of struct {b32u32_u x; float f, df;} tb[] in the C source
  private static final float[] tbDf = {
      -0x1p-20f, -0x1p-20f, 0x1p-21f, -0x1p-23f, 0x1p-23f,
      0x1p-18f, -0x1p-13f, 0x1p-9f, -0x1p-8f, -0x1p+10f,
      -0x1p+13f, -0x1p+57f, 0x1p+80f, -0x1p+93f, 0x1p+96f,
      -0x1p-19f, -0x1p-20f, -0x1p-20f, -0x1p-20f, -0x1p-21f,
      -0x1p-21f, -0x1p-21f, 0x1p-21f, 0x1p-29f, -0x1p-21f,
      -0x1p-16f, -0x1p-7f};

  // static const double rn[] of the |x| < 0x1.52p-1 branch
  private static final double[] rnTiny =
        {-0x1.505bdf4b65acp+4, -0x1.51c80eb47e068p+2, 0x1.0000000007cb8p+0, -0x1.4ac529250a1fcp+1,
         -0x1.a8c99dbe1621ap+0, -0x1.4abdcc74115eap+0, -0x1.1b87fe5a5b923p+0, -0x1.05b8a4d47ff64p+0};

  // static const double rd[] of the |x| < 0x1.52p-1 branch
  private static final double[] rdTiny =
        {-0x1.4db2cfe9a5265p+5, -0x1.062e99d1c4f27p+3, -0x1.c81bc2ecf25f6p+1, -0x1.108e55c10091bp+1,
         -0x1.7dd25af0b83d4p+0, -0x1.36bf1880125fcp+0, -0x1.1379fc8023d9cp+0, -0x1.03712e41525d2p+0};

  // static const double c[] of the ax > 1198 branch
  private static final double[] cIz1 =
              {0x1.555555547fbadp-4, -0x1.6c0fd270c465p-9};

  // static const double c[] of the ax > 0x1.279a7p+6 branch
  private static final double[] cIz2 =
              {0x1.555555554de0bp-4, -0x1.6c16bdc45944fp-9, 0x1.a0077f300ecb3p-11, -0x1.2e9cfff3b29c2p-11};

  // static const double c[] of the last expansion branch
  private static final double[] cIz3 =
              {0x1.5555555551286p-4, -0x1.6c16c0e7c4cf4p-9, 0x1.a0193267fe6f2p-11, -0x1.37e87ec19cb45p-11,
               0x1.b40011dfff081p-11, -0x1.c16c8946b19b6p-10, 0x1.e9f47ace150d8p-9, -0x1.4f5843a71a338p-8};

  // static const double rn[] of the |x| >= 0x1.52p-1 branch
  private static final double[] rnPoly =
          {-0x1.667923ff14df7p+5, -0x1.2d35f25ad8f64p+3, -0x1.b8c9eab9d5bd3p+1, -0x1.7a4a97f494127p+0,
           -0x1.3a6c8295b4445p-1, -0x1.da44e8b810024p-3, -0x1.9061e81c77e4ap-5};

  // static const double rd[] of the |x| >= 0x1.52p-1 branch
  private static final double[] rdPoly =
          {-0x1.491a899e84c52p+6, -0x1.d202961b9e098p+3, -0x1.4ced68c631ed6p+2, -0x1.2589eedf40738p+1,
           -0x1.1302e3337271p+0, -0x1.c36b802f26dffp-2, -0x1.3ded448acc39dp-3, -0x1.bffc491078eafp-6};

  // static const double c[] of the 0x40301b93 > t.u > 0x402f95c2 region
  private static final double[] cExc1 =
              {-0x1.ea12da904b18cp+0, 0x1.3267f3c265a54p+3, -0x1.4185ac30cadb3p+4, 0x1.f504accc3f2e4p+5,
               -0x1.8588444c679b4p+7, 0x1.43740491dc22p+9, -0x1.12400ea23f9e6p+11, 0x1.dac829f365795p+12};

  // static const double c[] of the 0x401ceccb < t.u < 0x401d95ca region
  private static final double[] cExc2 =
              {0x1.83fe966af535fp+0, 0x1.36eebb002f61ap+2, 0x1.694a60589a0b3p+0, 0x1.1718d7aedb0b5p+3,
               0x1.733a045eca0d3p+2, 0x1.8d4297421205bp+4, 0x1.7feea5fb29965p+4};

  // static const double c[] of the 0x40492009 < t.u < 0x404940ef region
  private static final double[] cExc3 =
              {0x1.f20a65f2fac55p+2, 0x1.9d4d297715105p+4, 0x1.c1137124d5b21p+6, 0x1.267203d24de38p+9,
               0x1.99a63399a0b44p+11, 0x1.2941214faaf0cp+14, 0x1.bb912c0c9cdd1p+16};

  private static double asR7(double x, double[] c) {
    return (((x-c[0])*(x-c[1]))*((x-c[2])*(x-c[3])))*(((x-c[4])*(x-c[5]))*((x-c[6])));
  }

  private static double asR8(double x, double[] c) {
    return (((x-c[0])*(x-c[1]))*((x-c[2])*(x-c[3])))*(((x-c[4])*(x-c[5]))*((x-c[6])*(x-c[7])));
  }

  private static double asSinpi(double x) {
    x -= 0.5;
    double x2 = x*x, x4 = x2*x2, x8 = x4*x4;
    return (0.25 - x2)*((cSinpi[0] + x2*cSinpi[1]) + x4*(cSinpi[2] + x2*cSinpi[3]) + x8*((cSinpi[4] + x2*cSinpi[5]) + x4*(cSinpi[6] + x2*cSinpi[7])));
  }

  private static double asLn(double x) {
    long t = Double.doubleToRawLongBits(x);
    int e = (int) (t >>> 52) - 0x3ff;
    int i = (int) ((t>>>48)&0xf);
    t = (t&(~0L>>>12))|(0x3ffL<<52);
    double z = ix[i]*Double.longBitsToDouble(t) - 1, z2 = z*z, z4 = z2*z2;
    return e*0x1.62e42fefa39efp-1 + il[i] + z*((cLn[0] + z*cLn[1]) + z2*(cLn[2] + z*cLn[3]) + z4*((cLn[4] + z*cLn[5]) + z2*(cLn[6] + z*cLn[7])));
  }

  public static float compute(float x, int[] signgamp) {
    float fx = (float) Math.floor(x), ax = Math.abs(x);
    int t = Float.floatToRawIntBits(ax);
    if(Integer.compareUnsigned(t, 0xff<<23) >= 0){ // NaN or Inf
      if(signgamp != null) signgamp[0] = 1; // for nan we set signgam arbitrarily to 1
      if(t==(0xff<<23)){ // +-inf
        return x * x;
      }
      return x + x; // nan
    }
    if(fx==x){ // x integer
      if(x <= 0.0f) {
        int tx = Float.floatToRawIntBits(x);
        // gamma(+0) = +Inf, gamma(-0) = -Inf
        // for negative integer we set signgam arbitrarily to 1
        if(signgamp != null)
          signgamp[0] = ((tx<<1) == 0) ? 1 - 2*(tx >>> 31) : 1;
        return Float.POSITIVE_INFINITY; // the C source computes 1.0f/0.0f
      }
      if(x==1.0f || x==2.0f) {
        if(signgamp != null) signgamp[0] = 1;
        return 0.0f;
      }
    }

    /* Check the value of fx to avoid a spurious invalid exception.
       Note that for a binary32 |x| >= 2^23, x is necessarily an integer,
       and we already dealed with negative integers, thus now:
       -2^23 < x < +Inf and x is not a negative integer nor 0, 1, 2. */
    if (fx >= 0) {
      if(signgamp != null) signgamp[0] = 1;
    } else {
      // gamma(x) is negative in (-2n-1,-2n), thus when fx is odd
      if(signgamp != null) signgamp[0] = 1 - ((((int)fx)&1)<<1);
    }
    double z = ax, f;
    double s = x;
    if(ax<0x1.52p-1f){
      double c0 = 0x1.0fc0fad268c4dp+2;
      f = (c0*s)*asR8(s, rnTiny)/asR8(s, rdTiny) - asLn(z);
    } else { // |x| >= 0x1.52p-1
      if(ax > 0x1.afc1ap+1f){
        if(x >= 0x1.895f1cp+121f){
          /* for x=0x1.895f1cp+121, lgamma(x) < 2^128, thus there is no
             overflow for rounding towards zero or downwards.
             The following expression overflows for x > 0x1.895f1cp+121
             or x = 0x1.895f1cp+121 and rounding to nearest or away,
             and does not overflow for x = 0x1.895f1cp+121 and rounding
             towards zero */
          float r = Math.fma (x, 0x1.4d3398p+6f, 0x1.10f35ep+103f);
          return r;
        }
        double lz = asLn(z);
        f = (z - 0.5)*(lz - 1) + 0x1.acfe390c97d69p-2;
        if(ax < 0x1.0p+20f){
          double iz = 1.0/z, iz2 = iz*iz;
          if(ax > 1198.0f){
            f +=  iz*(1./12.);
          } else if(ax > 0x1.279a7p+6f){
            f +=  iz*(cIz1[0] + iz2*cIz1[1]);
          } else if(ax > 0x1.555556p+3f){
            double iz4 = iz2*iz2;
            f +=  iz*((cIz2[0] + iz2*cIz2[1]) + iz4*(cIz2[2] + iz2*cIz2[3]));
          } else {
            double iz4 = iz2*iz2, iz8 = iz4*iz4;
            double p = ((cIz3[0] + iz2*cIz3[1]) + iz4*(cIz3[2] + iz2*cIz3[3])) + iz8*((cIz3[4] + iz2*cIz3[5]) + iz4*(cIz3[6] + iz2*cIz3[7]));
            f += iz*p;
          }
        }
        if(x < 0.0f){
          f = 0x1.250d048e7a1bdp+0 - f - lz;
          double lp = asLn(asSinpi(x-fx));
          f -= lp;
        }
      } else {
        double c0 = 0x1.3cc0e6a0106b3p+2;
        f = (z-1)*(z-2)*c0*asR7(z, rnPoly)/asR8(z, rdPoly);
        if(x < 0.0f){
          if(Integer.compareUnsigned(t, 0x40301b93) < 0
             && Integer.compareUnsigned(t, 0x402f95c2) > 0){
            double h = (s + 0x1.5fb410a1bd901p+1) - 0x1.a19a96d2e6f85p-54, h2 = h*h, h4 = h2*h2;
            f = h*((cExc1[0] + h*cExc1[1]) + h2*(cExc1[2] + h*cExc1[3]) + h4*((cExc1[4] + h*cExc1[5]) + h2*(cExc1[6] + h*cExc1[7])));
          } else if(Integer.compareUnsigned(t, 0x401ceccb) > 0
                    && Integer.compareUnsigned(t, 0x401d95ca) < 0){
            double h = (s + 0x1.3a7fc9600f86cp+1) + 0x1.55f64f98af8dp-55, h2 = h*h, h4 = h2*h2;
            f = h*((cExc2[0] + h*cExc2[1]) + h2*(cExc2[2] + h*cExc2[3]) + h4*((cExc2[4] + h*cExc2[5]) + h2*(cExc2[6])));
          } else if(Integer.compareUnsigned(t, 0x40492009) > 0
                    && Integer.compareUnsigned(t, 0x404940ef) < 0){
            double h = (s + 0x1.9260dbc9e59afp+1) + 0x1.f717cd335a7b3p-53, h2 = h*h, h4 = h2*h2;
            f = h*((cExc3[0] + h*cExc3[1]) + h2*(cExc3[2] + h*cExc3[3]) + h4*((cExc3[4] + h*cExc3[5]) + h2*(cExc3[6])));
          } else {
            f = 0x1.250d048e7a1bdp+0 - f;
            double lp = asLn(asSinpi(x-fx)*z);
            f -= lp;
          }
        }
      }
    }

    long rt = Double.doubleToRawLongBits(f);
    long tl = (rt+5)&0xfffffff;
    float r = (float) f;
    if(Long.compareUnsigned(tl, 31) <= 0){
      int tx = Float.floatToRawIntBits(x);
      int a = 0, b = tbX.length;
      // invariant: t.u < tb[0].x.u or tb[a].x.u <= t.u < tb[b].x.u
      while (a + 1 < b) {
        int i = (a + b) / 2;
        if(Integer.compareUnsigned(tx, Float.floatToRawIntBits(tbX[i])) < 0)
          b = i;
        else
          a = i;
      }
      if(tx == Float.floatToRawIntBits(tbX[a])) return tbF[a] + tbDf[a];
    }
    return r;
  }
}
