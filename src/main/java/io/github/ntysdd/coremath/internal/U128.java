package io.github.ntysdd.coremath.internal;

/**
 * Unsigned 128-bit value in two 64-bit halves, the translation of upstream's
 * {@code unsigned __int128}.
 *
 * <p>{@link #lo} and {@link #hi} hold the low and high 64 bits as raw patterns.
 * The value is immutable, so the arrays of constants that the binary64 sources
 * carry can be shared. Every operation is unsigned: halves are compared through
 * {@link Long#compareUnsigned} and shifted with {@code >>>}. Not part of the
 * API.
 */
public final class U128 {
  public final long lo;
  public final long hi;

  private U128(long lo, long hi) {
    this.lo = lo;
    this.hi = hi;
  }

  /**
   * The value {@code (u128)l | ((u128)h << 64)}, the C macro that upstream
   * spells {@code U128(l,h)}. The low half comes first.
   */
  public static U128 of(long l, long h) {
    return new U128(l, h);
  }

  /** Unsigned 64-by-64 to 128-bit multiplication. */
  public static U128 mul(long a, long b) {
    long lo = a*b;
    // The signed high half plus the two sign corrections gives the unsigned one.
    long hi = Math.multiplyHigh(a, b) + ((a >> 63)&b) + ((b >> 63)&a);
    return new U128(lo, hi);
  }

  /** {@code (u128)a * b} truncated to 128 bits. */
  public U128 mul(long a) {
    U128 lolo = mul(a, lo);
    return new U128(lolo.lo, lolo.hi + a*hi);
  }

  /** {@code a * b} truncated to 128 bits. */
  public U128 mul(U128 b) {
    U128 lolo = mul(lo, b.lo);
    return new U128(lolo.lo, lolo.hi + hi*b.lo + lo*b.hi);
  }

  public U128 add(U128 b) {
    long l = lo + b.lo;
    return new U128(l, hi + b.hi + (Long.compareUnsigned(l, lo) < 0 ? 1 : 0));
  }

  /** {@code this + (u128)v}, for a 64-bit addend. */
  public U128 addLow(long v) {
    long l = lo + v;
    return new U128(l, hi + (Long.compareUnsigned(l, lo) < 0 ? 1 : 0));
  }

  public U128 sub(U128 b) {
    long l = lo - b.lo;
    return new U128(l, hi - b.hi - (Long.compareUnsigned(lo, b.lo) < 0 ? 1 : 0));
  }

  public U128 and(U128 b) {
    return new U128(lo&b.lo, hi&b.hi);
  }

  public U128 or(U128 b) {
    return new U128(lo|b.lo, hi|b.hi);
  }

  public U128 shl(int n) {
    if (n == 0)
      return this;
    if (n >= 128)
      return new U128(0, 0);
    if (n >= 64)
      return new U128(0, lo << (n - 64));
    return new U128(lo << n, (hi << n) | (lo >>> (64 - n)));
  }

  public U128 shr(int n) {
    if (n == 0)
      return this;
    if (n >= 128)
      return new U128(0, 0);
    if (n >= 64)
      return new U128(hi >>> (n - 64), 0);
    return new U128((lo >>> n) | (hi << (64 - n)), hi >>> n);
  }

  /** Unsigned comparison, negative when {@code this} is below {@code b}. */
  public int compareUnsigned(U128 b) {
    int c = Long.compareUnsigned(hi, b.hi);
    return c != 0 ? c : Long.compareUnsigned(lo, b.lo);
  }
}
