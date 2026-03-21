package dev.ioannikas.gms.utility;

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIdGenerator {
    private final AtomicInteger id = new AtomicInteger(0);

    public int getNext() {
        return id.getAndIncrement();
    }

    public int get() {
        return id.get();
    }
}
