/*
 * Correctly-rounded binary32 arccosine function.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/acos/acosf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_acosf -> AcosF.compute
 *
 * Copyright (c) 2022-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly rounded arccosine of a binary32 value, the translation of the
 * CORE-MATH {@code cr_acosf}.
 *
 * <p>One fast path with the {@code b[]} table for {@code |x| < 0x1.c2a1dcp-1},
 * then an accurate path that evaluates {@code acos(x) = pi/2 - asin(x)} for
 * {@code |x| < 0.5} and {@code acos(x) = o[sign] + sqrt(1-|x|)*P(1-|x|)}
 * otherwise; both accurate branches reuse the same {@code poly12}.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code b32u32_u} union becomes {@link Float#floatToRawIntBits} and
 *       {@link Float#intBitsToFloat}; the bits of the argument are carried in
 *       an {@code int}.
 *   <li>{@code 0.0f/0.0f} becomes {@link Float#NaN}, the two constants of
 *       {@code as_special} stay locals, and the {@code errno} assignments are
 *       dropped, since no caller can observe {@code errno}.
 *   <li>{@code __builtin_fabs}, {@code __builtin_sqrt} and
 *       {@code __builtin_copysign} are {@link Math#abs}, {@link Math#sqrt} and
 *       {@link Math#copySign}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, and {@code t.u>>31}, which indexes
 *       {@code o}, is {@code >>>}.
 *   <li>{@code static const} declarations inside {@code cr_acosf} become
 *       fields: the {@code pi2} and {@code o} of the entry point, the
 *       {@code pi2h} and {@code pi2l} of its small-argument branch, and the two
 *       different {@code c[]} tables, which are {@code cAcc} and
 *       {@code cLarge} because both are class fields in Java.
 *   <li>{@code poly12} takes its table as a {@code double[]}.
 *   <li>Where the C code narrows a {@code double} to {@code float}
 *       ({@code ub}, {@code lb} and the returned {@code r}) the cast is
 *       explicit.
 * </ul>
 */
public final strictfp class AcosF {
  private AcosF() {
  }

  private static float asSpecial(float x) {
    float pih = 0x1.921fb6p+1f, pil = -0x1p-24f;
    int ux = Float.floatToRawIntBits(x);
    if(ux == (0x7f<<23)) return 0.0f; // x=1
    if(ux == (0x17f<<23)) return pih + pil;  // x=-1
    int ax = ux<<1;
    if(Integer.compareUnsigned(ax, 0xff<<24)>0) return x + x; // nan
    // the C source sets errno to EDOM here
    return Float.NaN; // 0.0f/0.0f in the C source, to raise FE_INVALID
  }

  private static double poly12(double z, double[] c){
    double z2 = z*z, z4 = z2*z2;
    double c0 = c[0] + z*c[1];
    double c2 = c[2] + z*c[3];
    double c4 = c[4] + z*c[5];
    double c6 = c[6] + z*c[7];
    double c8 = c[8] + z*c[9];
    double c10 = c[10] + z*c[11];
    c0 += c2*z2;
    c4 += c6*z2;
    c8 += z2*c10;
    c0 += z4*(c4 + z4*c8);
    return c0;
  }

  private static final double[] b =
        {0x1.fffffffd9ccb8p-1, 0x1.5555c94838007p-3, 0x1.32ded4b7c20fap-4, 0x1.8566df703309ep-5,
         -0x1.980c959bec9a3p-6, 0x1.56fbb04998344p-1, -0x1.403d8e4c49f52p+2, 0x1.b06c3e9f311eap+4,
         -0x1.9ea97c4e2c21fp+6, 0x1.200b8261cc61bp+8, -0x1.2274c2799a5c7p+9, 0x1.a558a59cc19d3p+9,
         -0x1.aca4b6a529ffp+9, 0x1.228744703f813p+9, -0x1.d7dbb0b322228p+7, 0x1.5c2018c0c0105p+5};

  private static final double[] o = {0, 0x1.921fb54442d18p+1};

  // the first static const double c[] inside cr_acosf() in the C source
  private static final double[] cAcc =
        {0x1.555555555529cp-3, 0x1.333333337e0ddp-4, 0x1.6db6db3b4465ep-5, 0x1.f1c72e13ac306p-6,
         0x1.6e89cebe06bc4p-6, 0x1.1c6dcf5289094p-6, 0x1.c6dbbcc7c6315p-7, 0x1.8f8dc2615e996p-7,
         0x1.a5833b7bf15e8p-8, 0x1.43f44ace1665cp-6, -0x1.0fb17df881c73p-6, 0x1.07520c026b2d6p-5};

  // the second static const double c[] inside cr_acosf() in the C source
  private static final double[] cLarge =
        {0x1.6a09e667f3bcbp+0, 0x1.e2b7dddff2db9p-4, 0x1.b27247ab42dbcp-6, 0x1.02995cc4e0744p-7,
         0x1.5ffb0276ec8eap-9, 0x1.033885a928decp-10, 0x1.911f2be23f8c7p-12, 0x1.4c3c55d2437fdp-13,
         0x1.af477e1d7b461p-15, 0x1.abd6bdff67dcbp-15, -0x1.1717e86d0fa28p-16, 0x1.6ff526de46023p-16};

  // static const double pi2, static const double o[], and static const float
  // pi2h and pi2l inside cr_acosf() in the C source
  private static final double pi2 = 0x1.921fb54442d18p+0; // approximates pi/2
  private static final float pi2h = 0x1.921fb6p+0f, pi2l = -0x1.777a5cp-25f;

  public static float compute(float x) {
    double xs = x, r;
    int ux = Float.floatToRawIntBits(x);
    int ax = ux<<1;
    if(Integer.compareUnsigned(ax, 0x7f<<24)>=0) return asSpecial(x);
    if(Integer.compareUnsigned(ax, 0x7ec2a1dc)<0){ // |x| < 0x1.c2a1dcp-1
      /* avoid spurious underflow */
      if (Integer.compareUnsigned(ax, 0x40000000)<0) { // |x| < 2^-63
        return pi2h + pi2l;
      }
      double z = xs, z2 = z*z, z4 = z2*z2, z8 = z4*z4, z16=z8*z8;
      r = z*((((b[0] + z2*b[1]) + z4*(b[2] + z2*b[3])) + z8*((b[4] + z2*b[5]) + z4*(b[6] + z2*b[7]))) +
            z16*(((b[8] + z2*b[9]) + z4*(b[10] + z2*b[11])) + z8*((b[12] + z2*b[13]) + z4*(b[14] + z2*b[15]))));
      float ub = (float)(0x1.921fb54574191p+0 - r), lb = (float)(0x1.921fb543118ap+0 - r);
      if(ub==lb) return ub;
    }
    // accurate path
    if(Integer.compareUnsigned(ax, 0x7e<<24)<0){
      if(ux == 0x328885a3) return 0x1.921fb6p+0f + 0x1p-25f;
      if(ux == 0x39826222) return 0x1.920f6ap+0f + 0x1p-25f;
      double x2 = xs*xs;
      r = (pi2 - xs) - (xs*x2)*poly12(x2, cAcc);
    } else {
      double bx = Math.abs(xs);
      double z = 1.0 - bx;
      double s = Math.copySign(Math.sqrt(z), xs);
      r = o[ux>>>31] + s*poly12(z, cLarge);
    }
    return (float)r;
  }
}
