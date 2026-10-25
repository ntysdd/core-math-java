package io.github.ntysdd.coremath.internal;

/**
 * Mutable 128-bit fixed-point value with an exponent and a sign, the
 * translation of upstream's {@code dint64_t} from pow.c, as used by the
 * binary64 cosine.
 *
 * <p>The fields are the ones of the C struct: {@link #hi} and {@link #lo} hold
 * the 128-bit significand, the most significant bit of {@code hi} being set for
 * a normalized value, {@link #ex} is the exponent and {@link #sgn} is 0 or 1.
 * The arithmetic and conversions live in the translation unit that ports them;
 * this class only carries the data and the {@code r} member of the union.
 *
 * <p>Values are scratch objects, so no state is shared between calls: the
 * arrays of constants in the binary64 sources are built once and only read.
 * Not part of the API.
 */
public final class Dint64 {
  public long hi;
  public long lo;
  public long ex;
  public long sgn;

  /** An unspecified value, for locals the C code declares without initializer. */
  public Dint64() {
  }

  /** The C designated initializer {@code {.hi, .lo, .ex, .sgn}}, in that order. */
  public Dint64(long hi, long lo, long ex, long sgn) {
    this.hi = hi;
    this.lo = lo;
    this.ex = ex;
    this.sgn = sgn;
  }

  /** The {@code r} member of the union, {@code (u128)hi << 64 | lo}. */
  public U128 r() {
    return U128.of(lo, hi);
  }

  /** Assigns the {@code r} member of the union. */
  public void r(U128 v) {
    lo = v.lo;
    hi = v.hi;
  }
}
