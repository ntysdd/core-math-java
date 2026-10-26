/*
 * Correctly-rounded cube root for binary64 values.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary64/cbrt/cbrt.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_cbrt -> Cbrt.compute
 *
 * Copyright (c) 2023-2025 Alexei Sibidanov and Paul Zimmermann.
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
 * Correctly rounded cube root of a binary64 value, the translation of the
 * CORE-MATH {@code cr_cbrt}.
 *
 * <p>Reduce the exponent modulo 3 and the significand to {@code [1,2)}, take a
 * degree-3 polynomial, then two steps of Newton's method in double-double, and
 * finally settle the last bits with the two rounding tests, with two hard cases
 * named explicitly.
 *
 * <p>This is the first translated file that reads the rounding mode of the
 * floating-point environment, and the deviations from the C file are:
 *
 * <ul>
 *   <li>{@code get_rounding_mode} and {@code set_flags}, with their
 *       {@code _mm_getcsr}/{@code _mm_setcsr}, the arm64 FPCR emulation and the
 *       {@code fegetround}/{@code fesetexceptflag} fallback, are not
 *       translated: the port is round-to-nearest-even only, so {@code rm} is a
 *       constant 0. Consequently {@code off[rm]} is {@code off[0]}, and the
 *       {@code if (rm > 0)} block, which bumps seven hard cases of its
 *       {@code wlist} table by 2^-52 when {@code rm + sign == 2}, is dead and is
 *       dropped, with a comment where it stood. The {@code set_flags} calls at
 *       the end only restored the saved exception flags.
 *   <li>{@code ((const uint64_t *)escale)[it]}, the bit view of the
 *       {@code double} table, is {@link Double#doubleToRawLongBits} applied to
 *       the entry; the table keeps the spelling of the C source.
 *   <li>The {@code b64u64_u} union becomes {@link Double#doubleToRawLongBits}
 *       and {@link Double#longBitsToDouble}; {@code cvt0} to {@code cvt5} are
 *       carried as {@code long} bit patterns.
 *   <li>{@code __builtin_clzll}, {@code __builtin_fabs},
 *       {@code __builtin_copysign} and {@code __builtin_fma} are
 *       {@link Long#numberOfLeadingZeros}, {@link Math#abs},
 *       {@link Math#copySign} and {@link Math#fma}.
 *   <li>{@code __builtin_expect} is dropped.
 *   <li>The {@code unsigned} locals {@code e}, {@code et} and {@code it} are
 *       {@code int}: {@code e -= nz - 1} does go negative for a subnormal, but
 *       3072 is added right after, so the wrapping {@code unsigned} arithmetic
 *       of the C source and {@code int} agree on every value that is used.
 *   <li>The {@code (uint64_t)(m0^m1)<=(1ul<<30)} comparison goes through
 *       {@link Long#compareUnsigned}, because the C comparison is unsigned,
 *       while the {@code cbrtf.c} counterpart is signed and stays so.
 *   <li>{@code static const} tables declared inside the function become fields:
 *       {@code escale}, {@code c}, {@code rsc} and {@code off}.
 * </ul>
 */
public final strictfp class Cbrt {
  private Cbrt() {
  }

  private static final double[] escale = {1.0, 0x1.428a2f98d728bp+0/* 2^(1/3) */, 0x1.965fea53d6e3dp+0/* 2^(2/3) */};

  /* the polynomial c0+c1*x+c2*x^2+c3*x^3 approximates x^(1/3) on [1,2]
     with maximal error < 9.2e-5 (attained at x=2) */
  private static final double[] c = {0x1.1b0babccfef9cp-1, 0x1.2c9a3e94d1da5p-1, -0x1.4dc30b1a1ddbap-3, 0x1.7a8d3e4ec9b07p-6};

  private static final double[] rsc = { 1, -1, 0.5, -0.5, 0.25, -0.25};

  // only off[0] is read, the other three entries are for the directed roundings
  private static final double[] off = {0x1p-53, 0, 0, 0};

  public static double compute(double x) {
    double u0 = 0x1.5555555555555p-2, u1 = 0x1.c71c71c71c71cp-3;
    // rm = get_rounding_mode (&flag) in the C source; Java is round-to-nearest
    // only, so rm is a constant 0 and the mode is never read here
    /* rm=0 for rounding to nearest, and other values for directed roundings */
    long cvt0 = Double.doubleToRawLongBits(x);
    long hx = cvt0, mant = hx&(~0L>>>12), sign = hx>>>63;
    int e = (int)((hx>>>52)&0x7ff);
    if(((e+1)&0x7ff)<2){
      long ix = hx&(~0L>>>1);
      if(e==0x7ff||ix==0) return x + x; /* 0, inf, nan: we return x + x instead of simply x,
                                           to that for x a signaling NaN, it correctly triggers
                                           the invalid exception. */
      /* use __builtin_clzll otherwise ix might be truncated to 32 bits
         on 32-bit processors */
      int nz = Long.numberOfLeadingZeros(ix) - 11;  /* subnormal */
      mant <<= nz;
      mant &= (~0L)>>>12;
      e -= nz - 1;
    }
    e += 3072;
    long cvt1 = mant|(0x3ffL<<52), cvt5 = cvt1;
    int et = e/3, it = e%3;
    /* 2^(3k+it) <= x < 2^(3k+it+1), with 0 <= it <= 3 */
    cvt5 += (long)it<<52;
    cvt5 |= sign<<63;
    double zz = Double.longBitsToDouble(cvt5);
    /* cbrt(x) = cbrt(zz)*2^(et-1365) where 1 <= |zz| < 8 */
    long isc = Double.doubleToRawLongBits(escale[it]);
    isc |= sign<<63;
    long cvt2 = isc;
    double z = Double.longBitsToDouble(cvt1);
    /* cbrt(zz) = cbrt(z)*isc, where isc encodes 1, 2^(1/3) or 2^(2/3),
       and 1 <= z < 2 */
    double r = 1/z, rr = r*rsc[it<<1|(int)sign], z2 = z*z;
    double c0 = c[0] + z*c[1], c2 = c[2] + z*c[3];
    double y = c0 + z2*c2, y2 = y*y;
    /* y is an approximation of z^(1/3) */
    double h = y2*(y*r) - 1;
    /* h determines the error between y and z^(1/3) */
    y -= (h*y)*(u0 - u1*h);
    /* The correction y -= (h*y)*(u0 - u1*h) corresponds to a cubic variant
       of Newton's method, with the function f(y) = 1-z/y^3. */
    y *= Double.longBitsToDouble(cvt2);
    /* Now y is an approximation of zz^(1/3),
       and rr an approximation of 1/zz. We now perform another iteration of
       Newton-Raphson, this time with a linear approximation only. */
    y2 = y*y; double y2l = Math.fma(y,y,-y2);
    /* y2 + y2l = y^2 exactly */
    double y3 = y2*y, y3l = Math.fma(y,y2,-y3) + y*y2l;
    /* y3 + y3l approximates y^3 with about 106 bits of accuracy */
    h = ((y3 - zz) + y3l)*rr;
    double dy = h*(y*u0);
    /* the approximation of zz^(1/3) is y - dy */
    double y1 = y - dy;
    dy = (y - y1) - dy;
    /* the approximation of zz^(1/3) is now y1 + dy, where |dy| < 1/2 ulp(y)
       (for rounding to nearest) */
    double ady = Math.abs(dy);
    /* For directed roundings, ady0 is tiny when dy is tiny, or ady0 is near
       from ulp(1);
       for rounding to nearest, ady0 is tiny when dy is near from 1/2 ulp(1),
       or from 3/2 ulp(1). */
    double ady0 = Math.abs(ady - off[0]);
    double ady1 = Math.abs(ady - (0x1p-52+off[0]));
    if(ady0<0x1p-75 || ady1<0x1p-75){
      y2 = y1*y1; y2l = Math.fma(y1,y1,-y2);
      y3 = y2*y1; y3l = Math.fma(y1,y2,-y3) + y1*y2l;
      h = ((y3 - zz) + y3l)*rr;
      dy = h*(y1*u0);
      y = y1 - dy;
      dy = (y1 - y) - dy;
      y1 = y;
      ady = Math.abs(dy);
      ady0 = Math.abs(ady - off[0]);
      ady1 = Math.abs(ady - (0x1p-52+off[0]));
      if(ady0<0x1p-98 || ady1<0x1p-98){
        double azz = Math.abs(zz);
        if(azz == 0x1.9b78223aa307cp+1) // ~ 0x1.79d15d0e8d59b80000000000000ffc3dp+0
          y1 = Math.copySign(0x1.79d15d0e8d59cp+0, zz);
        if(azz == 0x1.a202bfc89ddffp+2) // ~ 0x1.de87aa837820e80000000000001c0f08p+0
          y1 = Math.copySign(0x1.de87aa837820fp+0, zz);
        /* The C source has a block here, guarded by "if (rm > 0)", which reads
           its static const double wlist[7][2] and adds 2^-52 to those seven
           hard cases when rm + sign == 2. It only runs under a directed
           rounding mode, so with rm == 0 it is dead and is not translated. */
      }
    }
    long cvt3 = Double.doubleToRawLongBits(y1);
    cvt3 += (long)(et - 342 - 1023)<<52;
    long m0 = cvt3<<30, m1 = m0>>63;
    if(Long.compareUnsigned(m0^m1, 1L<<30) <= 0){
      long cvt4 = Double.doubleToRawLongBits(y1);
      cvt4 = (cvt4 + (1L<<15))&0xffffffffffff0000L;
      if( Math.abs((Double.longBitsToDouble(cvt4) - y1) - dy) < 0x1p-60 || Math.abs(zz) == 1.0 ){
        cvt3 = (cvt3 + (1L<<15))&0xffffffffffff0000L;
        // set_flags (&flag) in the C source, which restores the saved flags
      }
    }
    return Double.longBitsToDouble(cvt3);
  }
}
