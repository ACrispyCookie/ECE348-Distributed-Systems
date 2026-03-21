package dev.ioannikas.chatapp.model;

import java.io.Serializable;
import java.util.Date;

public class Message implements Serializable {

    private final String author;
    private final Date date;
    private final String message;

    public Message(String author, Date date, String message) {
        this.author = author;
        this.date = date;
        this.message = message;
    }

    public String getAuthor() {
        return author;
    }

    public Date getDate() {
        return date;
    }

    public String getMessage() {
        return message;
    }
}
