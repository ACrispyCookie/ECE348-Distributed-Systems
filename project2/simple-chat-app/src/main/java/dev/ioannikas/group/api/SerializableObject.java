package dev.ioannikas.group.api;

import java.nio.ByteBuffer;

public interface SerializableObject {
    void serialize(ByteBuffer buffer);
}
