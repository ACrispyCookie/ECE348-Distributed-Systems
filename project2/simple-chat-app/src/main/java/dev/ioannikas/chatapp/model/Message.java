package dev.ioannikas.chatapp.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {

    private final String author;
    private final LocalDateTime date;
    private final String message;

    public Message(String author, LocalDateTime date, String message) {
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
}
