package dev.ioannikas.grouplib.api;

public class Message {
    private final Type type;
    private final Object object;

    public Message(Type type, Object object) {
        this.type = type;
        this.object = object;
    }

    public Type getType() {
        return type;
    }

    public Object getObject() {
        return object;
    }

    public enum Type {
        DATA,
        VIEW_UPDATE
    }
}
