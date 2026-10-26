package io.github.ntysdd.coremath.internal;

/**
 * Mutable 192-bit fixed-point value with an exponent and a sign, the
 * translation of upstream's {@code tint_t} from atan2.c's {@code tint.h}.
 *
 * <p>The value is {@code (-1)^sgn * (h/2^64 + m/2^128 + l/2^192) * 2^ex}, with
 * either {@code h = m = l = 0}, which encodes {@code +0} or {@code -0}, or the
 * most significant bit of {@code h} set. The fields are the ones of the C
 * struct; the two {@code u128} members of the union, which upstream calls
 * {@code _h} and {@code _l}, are accessors here.
 *
 * <p>As for the other fixed-point classes, the little-endian member of the
 * byte-order {@code #if} is the one the reference build compiles, so {@code _h}
 * is {@code (m, h)}: the limb {@code m} comes before {@code h} in memory.
 *
 * <p>Values are scratch objects, so no state is shared between calls: the
 * arrays of constants in the binary64 arc-tangent are built once and only read.
 * Not part of the API.
 */
public final class Tint64 {
  public long h;
  public long m;
  public long l;
  public long ex;
  public long sgn;

  /** An unspecified value, for locals the C code declares without initializer. */
  public Tint64() {
  }

  /** The C designated initializer {@code {.h, .m, .l, .ex, .sgn}}. */
  public Tint64(long h, long m, long l, long ex, long sgn) {
    this.h = h;
    this.m = m;
    this.l = l;
    this.ex = ex;
    this.sgn = sgn;
  }

  /** The {@code _h} member of the union, {@code (u128)h << 64 | m}. */
  public U128 _h() {
    return U128.of(m, h);
  }

  /** Assigns the {@code _h} member of the union. */
  public void _h(U128 v) {
    m = v.lo;
    h = v.hi;
  }

  /** The {@code _l} member of the union. */
  public long _l() {
    return l;
  }

  /** Assigns the {@code _l} member of the union. */
  public void _l(long v) {
    l = v;
  }
}
