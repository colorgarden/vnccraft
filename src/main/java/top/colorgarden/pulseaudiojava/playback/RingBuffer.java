package top.colorgarden.pulseaudiojava.playback;

/**
 * Simple ring buffer. write() drops data when full (returns bytes written).
 * read() blocks briefly when empty. Large enough buffer avoids data loss.
 */
public class RingBuffer {
    private final byte[] buffer;
    private volatile int readPos;
    private volatile int writePos;

    public RingBuffer(int capacity) {
        int cap = 1;
        while (cap < capacity) cap <<= 1;
        this.buffer = new byte[cap];
    }

    public int available() {
        return (writePos - readPos) & (buffer.length - 1);
    }

    private int freeSpace() {
        return buffer.length - available() - 1;
    }

    /** Non-blocking write. Returns bytes actually written (may be less than length if full). */
    public int write(byte[] src, int offset, int length) {
        int free = freeSpace();
        int n = Math.min(length, free);
        if (n <= 0) return 0;
        int pos = writePos & (buffer.length - 1);
        int first = Math.min(n, buffer.length - pos);
        System.arraycopy(src, offset, buffer, pos, first);
        if (first < n) System.arraycopy(src, offset + first, buffer, 0, n - first);
        writePos += n;
        return n;
    }

    /** Blocking read. Waits until data is available, then returns available bytes. */
    public int read(byte[] dst, int offset, int length) throws InterruptedException {
        int avail;
        while ((avail = available()) == 0) {
            Thread.sleep(2);
        }
        int n = Math.min(length, avail);
        int pos = readPos & (buffer.length - 1);
        int first = Math.min(n, buffer.length - pos);
        System.arraycopy(buffer, pos, dst, offset, first);
        if (first < n) System.arraycopy(buffer, 0, dst, offset + first, n - first);
        readPos += n;
        return n;
    }

    /** Discard bytes from the buffer (fast-forward to re-sync). */
    public int skip(int bytes) {
        int avail = available();
        int n = Math.min(bytes, avail);
        if (n > 0) readPos += n;
        return n;
    }

    /** Buffer fill as a fraction 0.0..1.0 */
    public double fillRatio() {
        return (double) available() / buffer.length;
    }
}
