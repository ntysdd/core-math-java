package io.github.ntysdd.coremath.internal;

/**
 * Unsigned 128-bit value in two 64-bit halves, the translation of upstream's
 * {@code unsigned __int128}.
 *
 * <p>{@link #lo} and {@link #hi} hold the low and high 64 bits as raw patterns.
 * Every operation here is unsigned, so the halves are only ever compared
 * through {@link Long#compareUnsigned}. Not part of the API.
 */
public final class U128 {
  public long lo;
  public long hi;

  private U128(long lo, long hi) {
    this.lo = lo;
    this.hi = hi;
  }

  /** Unsigned 64-by-64 to 128-bit multiplication, {@code a*b} with both operands unsigned. */
  public static U128 mul(long a, long b) {
    long lo = a*b;
    // The signed high half plus the two sign corrections gives the unsigned one.
    long hi = Math.multiplyHigh(a, b) + ((a >> 63)&b) + ((b >> 63)&a);
    return new U128(lo, hi);
  }

  /** {@code this += (u128)v << 64}, the high half only. */
  public void addHi(long v) {
    long old = lo;
    lo = old + v;
    hi += Long.compareUnsigned(lo, old) < 0 ? 1 : 0;
  }
}
