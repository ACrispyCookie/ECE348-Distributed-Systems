package dev.ioannikas.chatapp.model;

import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;

public class ChatMessage implements SerializableObject {

    private final String author;
    private final LocalDateTime date;
    private final String message;

    public ChatMessage(String author, LocalDateTime date, String message) {
        this.author = author;
        this.date = date;
        this.message = message;
    }

    public String getAuthor() {
        return author;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        PacketTCP.writeString(buffer, getAuthor());
        PacketTCP.writeString(buffer, getDate().toString());
        PacketTCP.writeString(buffer, message);
    }

    public static SerializableObject deserialize(ByteBuffer buffer) {
        String author = PacketTCP.readString(buffer);
        LocalDateTime date = LocalDateTime.parse(PacketTCP.readString(buffer));
        String message = PacketTCP.readString(buffer);

        return new ChatMessage(author, date, message);
    }
}
