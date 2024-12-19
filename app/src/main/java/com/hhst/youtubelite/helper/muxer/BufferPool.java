package com.hhst.youtubelite.helper.muxer;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
    private final Queue<ByteBuffer> pool;
    private final int bufferSize;
    private final int maxPoolSize;

    /**
     * @param bufferSize size of each buffer
     * @param maxPoolSize max size of buffer pool
     */
    public BufferPool(int bufferSize, int maxPoolSize) {
        this.bufferSize = bufferSize;
        this.maxPoolSize = maxPoolSize;
        this.pool = new ConcurrentLinkedQueue<>();
    }


    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            // if no available buffer, create a new one
            buffer = ByteBuffer.allocate(bufferSize);
        } else {
            buffer.clear();
        }
        return buffer;
    }


    // release bytebuffer
    public void release(ByteBuffer buffer) {
        if (buffer != null && pool.size() < maxPoolSize) {
            pool.offer(buffer);
        }
    }

    public int getPoolSize() {
        return pool.size();
    }

}

