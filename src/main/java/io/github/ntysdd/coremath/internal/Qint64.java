package io.github.ntysdd.coremath.internal;

/**
 * Mutable 256-bit fixed-point value with an exponent and a sign, the
 * translation of upstream's {@code qint64_t} from pow.c's {@code qint.h}.
 *
 * <p>The fields are the ones of the C struct: {@link #hh}, {@link #hl},
 * {@link #lh} and {@link #ll} hold the 256-bit significand from the most to the
 * least significant limb, the most significant bit of {@code hh} being set for a
 * normalized value, {@link #ex} is the exponent and {@link #sgn} is 0 or 1. The
 * arithmetic and conversions live in the translation unit that ports them; this
 * class only carries the data and the two {@code u128} members of the union,
 * which upstream calls {@code rh} and {@code rl}.
 *
 * <p>As for {@link Dint64}, the little-endian member of the byte-order
 * {@code #if} is the one the reference build compiles, so {@code rl} is
 * {@code (ll, lh)} and {@code rh} is {@code (hl, hh)}.
 *
 * <p>Values are scratch objects, so no state is shared between calls: the
 * arrays of constants in the binary64 power are built once and only read.
 * Not part of the API.
 */
public final class Qint64 {
  public long hh;
  public long hl;
  public long lh;
  public long ll;
  public long ex;
  public long sgn;

  /** An unspecified value, for locals the C code declares without initializer. */
  public Qint64() {
  }

  /** The C designated initializer {@code {.hh, .hl, .lh, .ll, .ex, .sgn}}. */
  public Qint64(long hh, long hl, long lh, long ll, long ex, long sgn) {
    this.hh = hh;
    this.hl = hl;
    this.lh = lh;
    this.ll = ll;
    this.ex = ex;
    this.sgn = sgn;
  }

  /** The {@code rl} member of the union, {@code (u128)lh << 64 | ll}. */
  public U128 rl() {
    return U128.of(ll, lh);
  }

  /** Assigns the {@code rl} member of the union. */
  public void rl(U128 v) {
    ll = v.lo;
    lh = v.hi;
  }

  /** The {@code rh} member of the union, {@code (u128)hh << 64 | hl}. */
  public U128 rh() {
    return U128.of(hl, hh);
  }

  /** Assigns the {@code rh} member of the union. */
  public void rh(U128 v) {
    hl = v.lo;
    hh = v.hi;
  }
}
