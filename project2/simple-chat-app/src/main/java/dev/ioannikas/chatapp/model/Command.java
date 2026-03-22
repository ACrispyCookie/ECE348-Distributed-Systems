package dev.ioannikas.chatapp.model;

import java.util.Arrays;

public class Command {

    private final Type type;
    private final String[] args;

    public Command(Type type, String[] args) {
        this.type = type;
        this.args = args;
    }

    public Type getType() {
        return type;
    }

    public String[] getArgs() {
        return args;
    }

    public static Command parse(String s) {
        if (s == null || s.trim().isEmpty())
            return null;

        String[] tokens = s.trim().split("\\s+");
        String commandWord = tokens[0];

        if (!commandWord.startsWith("/"))
            return null;
        commandWord = commandWord.substring(1);

        Type parsedType;
        try {
            parsedType = Type.valueOf(commandWord.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        String[] commandArgs = Arrays.copyOfRange(tokens, 1, tokens.length);
        return new Command(parsedType, commandArgs);
    }

    public enum Type {
        JOIN,
        LEAVE,
        SELECT,
        INFO,
        EXIT
    }
}
