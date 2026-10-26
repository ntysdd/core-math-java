/*
 * Correctly-rounded binary32 arcsine function.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/asin/asinf.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_asinf -> AsinF.compute
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
 * Correctly rounded arcsine of a binary32 value, the translation of the
 * CORE-MATH {@code cr_asinf}.
 *
 * <p>A single fast path on the bits of the argument. The {@code b[]} table
 * covers {@code |x| < 0x1.7d83a6p+0}, the {@code c[]} of {@code poly12} covers
 * the rest up to {@code |x| < 0.5} in the {@code z + z^3*P(z^2)} form, and the
 * last branch uses {@code pi/2 - sqrt(1-|x|)*P(1-|x|)} for {@code |x|} close to
 * one.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>The {@code b32u32_u} union becomes {@link Float#floatToRawIntBits} and
 *       {@link Float#intBitsToFloat}; the bits of the argument are carried in
 *       an {@code int}.
 *   <li>{@code 0.0f/0.0f} becomes {@link Float#NaN}, and the {@code errno}
 *       assignments are dropped, since no caller can observe {@code errno}.
 *   <li>{@code __builtin_fmaf} is {@link Math#fma} with {@code float}
 *       arguments, {@code __builtin_fma}, {@code __builtin_fabs},
 *       {@code __builtin_sqrt} and {@code __builtin_copysign} are
 *       {@link Math#fma}, {@link Math#abs}, {@link Math#sqrt} and
 *       {@link Math#copySign}.
 *   <li>{@code uint32_t} comparisons go through
 *       {@link Integer#compareUnsigned}, and {@code uint32_t} shifts are
 *       arithmetic on {@code int}.
 *   <li>{@code static const} tables declared inside {@code cr_asinf} become
 *       fields: {@code b}, and the two different {@code c[]} tables, which are
 *       {@code cAcc} and {@code cLarge} because both are class fields in Java.
 *       The {@code const double pi2} of the entry point is not static and stays
 *       a local.
 *   <li>{@code poly12} takes its table as a {@code double[]}.
 *   <li>A local whose address the C code never takes stays a {@code double};
 *       where the C code narrows {@code double} to {@code float} the cast is
 *       explicit.
 * </ul>
 */
public final strictfp class AsinF {
  private AsinF() {
  }

  private static float asSpecial(float x) {
    int ux = Float.floatToRawIntBits(x);
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
        {0x1.0000000000005p+0, 0x1.55557aeca105dp-3, 0x1.3314ec3db7d12p-4, 0x1.775738a5a6f92p-5,
         0x1.5d5f7ce1c8538p-8, 0x1.605c6d58740fp-2, -0x1.5728b732d73c6p+1, 0x1.f152170f151ebp+3,
         -0x1.f962ea3ca992ep+5, 0x1.71971e17375ap+7, -0x1.860512b4ba23p+8, 0x1.26a3b8d4bdb14p+9,
         -0x1.36f2ea5698b51p+9, 0x1.b3d722aebfa2ep+8, -0x1.6cf89703b1289p+7, 0x1.1518af6a65e2dp+5};

  // the first static const double c[] inside cr_asinf() in the C source
  private static final double[] cAcc =
        {0x1.555555555529cp-3, 0x1.333333337e0ddp-4, 0x1.6db6db3b4465ep-5, 0x1.f1c72e13ac306p-6,
         0x1.6e89cebe06bc4p-6, 0x1.1c6dcf5289094p-6, 0x1.c6dbbcc7c6315p-7, 0x1.8f8dc2615e996p-7,
         0x1.a5833b7bf15e8p-8, 0x1.43f44ace1665cp-6, -0x1.0fb17df881c73p-6, 0x1.07520c026b2d6p-5};

  // the second static const double c[] inside cr_asinf() in the C source
  private static final double[] cLarge =
        {0x1.6a09e667f3bcbp+0, 0x1.e2b7dddff2db9p-4, 0x1.b27247ab42dbcp-6, 0x1.02995cc4e0744p-7,
         0x1.5ffb0276ec8eap-9, 0x1.033885a928decp-10, 0x1.911f2be23f8c7p-12, 0x1.4c3c55d2437fdp-13,
         0x1.af477e1d7b461p-15, 0x1.abd6bdff67dcbp-15, -0x1.1717e86d0fa28p-16, 0x1.6ff526de46023p-16};

  public static float compute(float x) {
    double pi2 = 0x1.921fb54442d18p+0;
    double xs = x, r;
    int ux = Float.floatToRawIntBits(x);
    int ax = ux<<1;
    if(Integer.compareUnsigned(ax, 0x7f<<24)>0) return asSpecial(x);
    if(Integer.compareUnsigned(ax, 0x7ec29000)<0){ // |x| < 0x1.7d83a6p+0
      if (Integer.compareUnsigned(ax, 115<<24)<0) { // |x| < 0x1p-12
        /* The Taylor expansion of asin(x) at x=0 is x + x^3/6 + o(x^3),
           thus for |x| >= 2^-126 we have no underflow, whatever the
           rounding mode.
           For |x| < 2^-126 and rounding towards zero, we have underflow.
           For x = nextbelow(2^-126) = 0x1.fffffcp-127, asin(x) would round
           upward to 0x1.fffffep-127 with unbounded exponent range, which is not
           representable, thus we have underflow too.
           In summary, we have underflow whenever |x| < 2^-126. */
        // the C source sets errno to ERANGE here on underflow
        return Math.fma(x, 0x1p-25f, x);
      }
      double z = xs, z2 = z*z, z4 = z2*z2, z8 = z4*z4, z16 = z8*z8;
      r = z*((((b[0] + z2*b[1]) + z4*(b[2] + z2*b[3])) + z8*((b[4] + z2*b[5]) + z4*(b[6] + z2*b[7]))) +
            z16*(((b[8] + z2*b[9]) + z4*(b[10] + z2*b[11])) + z8*((b[12] + z2*b[13]) + z4*(b[14] + z2*b[15]))));
      float ub = (float)r, lb = (float)(r - z*0x1.efa8ebp-31);
      if(ub==lb) return ub;
    }
    if(Integer.compareUnsigned(ax, 0x7e<<24)<0){
      double z = xs, z2 = z*z, c0 = poly12(z2, cAcc);
      r = z + (z*z2)*c0;
    } else {
      if(ax == 0x7e55688a) return Math.copySign(0x1.75b8a2p-1f, x) + Math.copySign(0x1p-26f, x);
      if(ax == 0x7e107434) return Math.copySign(0x1.1f4b64p-1f, x) + Math.copySign(0x1p-26f, x);
      double bx = Math.abs(xs);
      double z = 1.0 - bx;
      double s = Math.sqrt(z);
      r = pi2 - s*poly12(z, cLarge);
      r = Math.copySign(r, xs);
    }
    return (float)r;
  }
}
