/*
 * Correctly rounded base-2 logarithm of a binary32 value.
 *
 * Java translation of CORE-MATH.
 *   upstream: https://gitlab.inria.fr/core-math/core-math
 *   source:   src/binary32/log2/log2f.c
 *   commit:   0ce0cbc15935e24c656280e5a46c34e89f301b7a
 *   entry:    cr_log2f -> Log2F.compute
 *
 * Copyright (c) 2022-2025 Alexei Sibidanov.
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
 * Correctly rounded base-2 logarithm of a binary32 value, the translation of
 * the CORE-MATH {@code cr_log2f}.
 *
 * <p>{@code log2f.c} has one helper for the non-finite and non-positive
 * arguments, {@code as_special}, and then {@code cr_log2f}: an argument split
 * into a power of two and a mantissa in {@code [0.5, 1)}, a table of 65
 * inverses, and a degree-3 polynomial in the reduced argument, with a second
 * polynomial for the rounding boundary.
 *
 * <p>The deviations from the C file are the ones forced by the language or by
 * the mapping table in AGENTS.md:
 *
 * <ul>
 *   <li>{@code b32u32_u} and {@code b64u64_u} become
 *       {@link Float#floatToRawIntBits} and
 *       {@link Double#doubleToRawLongBits}. The comparisons of {@code ax},
 *       {@code ux} and {@code m} with the C constants go through
 *       {@link Integer#compareUnsigned}, being unsigned in the C code, and the
 *       sign-extension in {@code (int32_t)ux>>23} is kept: it is what gives the
 *       exponent of a normalized subnormal, as in {@code logf}.
 *   <li>{@code 1.0f / 0.0f} becomes {@link Float#NEGATIVE_INFINITY} and
 *       {@code 0.0f / 0.0f} becomes {@link Float#NaN}, each with a comment
 *       naming the exception the C source raises there; the
 *       {@code CORE_MATH_SUPPORT_ERRNO} blocks are dropped.
 *   <li>Where the C file returns or stores a {@code double} expression from a
 *       {@code float} function, the {@code (float)} cast is written out: the
 *       exact case {@code return e}, the two ends of the rounding test and the
 *       refined result.
 * </ul>
 */
public final strictfp class Log2F {
  private Log2F() {
  }

  private static final double[] ix = {
    0x1p+0, 0x1.f81f82p-1, 0x1.f07c1fp-1, 0x1.e9131acp-1,
    0x1.e1e1e1ep-1, 0x1.dae6077p-1, 0x1.d41d41dp-1, 0x1.cd85689p-1,
    0x1.c71c71cp-1, 0x1.c0e0704p-1, 0x1.bacf915p-1, 0x1.b4e81b5p-1,
    0x1.af286bdp-1, 0x1.a98ef6p-1, 0x1.a41a41ap-1, 0x1.9ec8e95p-1,
    0x1.999999ap-1, 0x1.948b0fdp-1, 0x1.8f9c19p-1, 0x1.8acb90fp-1,
    0x1.8618618p-1, 0x1.8181818p-1, 0x1.7d05f41p-1, 0x1.78a4c81p-1,
    0x1.745d174p-1, 0x1.702e05cp-1, 0x1.6c16c17p-1, 0x1.6816817p-1,
    0x1.642c859p-1, 0x1.605816p-1, 0x1.5c9882cp-1, 0x1.58ed231p-1,
    0x1.5555555p-1, 0x1.51d07ebp-1, 0x1.4e5e0a7p-1, 0x1.4afd6ap-1,
    0x1.47ae148p-1, 0x1.446f865p-1, 0x1.4141414p-1, 0x1.3e22cbdp-1,
    0x1.3b13b14p-1, 0x1.3813814p-1, 0x1.3521cfbp-1, 0x1.323e34ap-1,
    0x1.2f684bep-1, 0x1.2c9fb4ep-1, 0x1.29e412ap-1, 0x1.27350b9p-1,
    0x1.2492492p-1, 0x1.21fb781p-1, 0x1.1f7047ep-1, 0x1.1cf06aep-1,
    0x1.1a7b961p-1, 0x1.1811812p-1, 0x1.15b1e5fp-1, 0x1.135c811p-1,
    0x1.1111111p-1, 0x1.0ecf56cp-1, 0x1.0c9715p-1, 0x1.0a6810ap-1,
    0x1.0842108p-1, 0x1.0624dd3p-1, 0x1.041041p-1, 0x1.0204081p-1, 0.5};
  private static final double[] lix = {
    0x0p+0, -0x1.6e7966ead8ac5p-6, -0x1.6bad38119a13ap-5, -0x1.0eb389ee9f56p-4,
    -0x1.663f6fc3a678dp-4, -0x1.bc8423d408321p-4, -0x1.08c588e79f8e9p-3, -0x1.32ae9e28fc362p-3,
    -0x1.5c01a3cde7f74p-3, -0x1.84c2bccf005a9p-3, -0x1.acf5e2c156d8fp-3, -0x1.d49ee4b90c47cp-3,
    -0x1.fbc16b67c143bp-3, -0x1.11307dc445fecp-2, -0x1.24407abf4dc03p-2, -0x1.37124cede831fp-2,
    -0x1.49a784a5bc715p-2, -0x1.5c01a3965cc38p-2, -0x1.6e221cc2bb868p-2, -0x1.800a564aa10b6p-2,
    -0x1.91bba8a906b7fp-2, -0x1.a33760adbb56ep-2, -0x1.b47ebf91d417cp-2, -0x1.c592faf028f8ep-2,
    -0x1.d6753e1a43e85p-2, -0x1.e726aa2157f61p-2, -0x1.f7a8567cd1cbdp-2, -0x1.03fda8a93ea1cp-1,
    -0x1.0c10500ed4feep-1, -0x1.140c9fb5a8f7fp-1, -0x1.1bf311daefb44p-1, -0x1.23c41d317edc1p-1,
    -0x1.2b80347f8250cp-1, -0x1.3327c6a752222p-1, -0x1.3abb3fb080128p-1, -0x1.423b07f5114e5p-1,
    -0x1.49a784b14715p-1, -0x1.5101187e9b871p-1, -0x1.5848226c6c7c3p-1, -0x1.5f7cff3de8f2ap-1,
    -0x1.66a008d8ede91p-1, -0x1.6db19694a04aap-1, -0x1.74b1fd6b5e715p-1, -0x1.7ba18f99ce2a5p-1,
    -0x1.82809d4d79ba9p-1, -0x1.894f749cf5047p-1, -0x1.900e615bac2f7p-1, -0x1.96bdad16f516p-1,
    -0x1.9d5d9fe08baefp-1, -0x1.a3ee7f3e4a7ebp-1, -0x1.aa708f4de7fep-1, -0x1.b0e4125ca68fep-1,
    -0x1.b74948f9a72bp-1, -0x1.bda071b77c9e2p-1, -0x1.c3e9ca4193f99p-1, -0x1.ca258dd397814p-1,
    -0x1.d053f6d543325p-1, -0x1.d6753dfedaa39p-1, -0x1.dc899aa874b31p-1, -0x1.e29142f2e8b3dp-1,
    -0x1.e88c6b41b14aep-1, -0x1.ee7b4718b4414p-1, -0x1.f45e08c87b09p-1, -0x1.fa34e117d8785p-1,
    -0x1p+0
  };
  private static final double[] b = {
    0x1.7154765bab2b5p+0, -0x1.71574d6939f6cp-1, 0x1.ec60b584ca47fp-2};
  private static final double[] c = {
    0x1.71547652b8314p+0, -0x1.71547652b7f67p-1, 0x1.ec709db872c63p-2,
    -0x1.715476b064cdfp-2, 0x1.277c72c15441fp-2, -0x1.ec4ff35b64c31p-3};

  private static float asSpecial(float x) {
    int t = Float.floatToRawIntBits(x);
    int ux = t, ax = ux << 1;
    if (ax == 0) { // +/-0.0
      // the C source returns -1.0f/0.0f here, to raise FE_DIVBYZERO
      return Float.NEGATIVE_INFINITY;
    }
    if (ux == 0x7f800000) return x; // +inf
    if (Integer.compareUnsigned(ax, 0xff000000) > 0) return x + x; // nan
    // the C source returns 0.0f/0.0f here, to raise FE_INVALID
    return Float.NaN;
  }

  public static float compute(float x) {
    int t = Float.floatToRawIntBits(x);
    int ux = t;
    if (Integer.compareUnsigned(ux, 0x7f800000) >= 0) return asSpecial(x); // <=-0, nan, inf
    if (Integer.compareUnsigned(ux, 1 << 23) < 0) { // subnormal
      if (ux == 0) return asSpecial(x); // +0
      int n = Integer.numberOfLeadingZeros(ux) - 8;
      ux <<= n;
      ux -= n << 23;
    }
    int e = (ux >> 23) - 0x7f;
    int m = ux & (~0 >>> 9);
    if (m == 0) return (float) e;
    int j = (m + (1 << (23 - 7))) >> (23 - 6);
    double xd = Double.longBitsToDouble(((long) m << (52 - 23)) | (0x3ffL << 52));
    double z = xd * ix[j] - 1.0, z2 = z * z, el = e - lix[j];
    double f = (el + z * b[0]) + z2 * (b[1] + z * b[2]);
    float lb = (float) f, ub = (float) (f + 0x1.661p-32);
    if (lb == ub) return lb;
    double c0 = c[0] + z * c[1];
    double c2 = c[2] + z * c[3];
    double c4 = c[4] + z * c[5];
    c0 += z2 * (c2 + z2 * c4);
    return (float) (el + z * c0);
  }

}
