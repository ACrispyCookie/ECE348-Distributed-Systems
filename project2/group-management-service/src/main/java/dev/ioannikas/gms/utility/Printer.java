package dev.ioannikas.gms.utility;

import dev.ioannikas.gms.model.Group;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Printer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void printInfo(String message) {
        print("INFO", message);
    }

    public static void printDebug(String message) {
        print("DEBUG ", message);
    }

    public static void printGroup(Group group, String message) {
        print("GROUP " + group.getName(), message);
    }

    public static void printErr(String message) {
        print("ERROR", message);
    }

    private static void print(String prefix, String message) {
        System.err.println(LocalDateTime.now().format(FMT) + ": [" + prefix + "] " + message);
    }
}
