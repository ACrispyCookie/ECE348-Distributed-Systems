package dev.ioannikas.group.gms.utility;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Printer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void printInfo(String message) {
        print("INFO", message);
    }

    public static void printDebug(String message) {
        print("DEBUG", message);
    }

    public static void printErr(String message) {
        print("ERROR", message);
    }

    private static void print(String prefix, String message) {
        String[] lines = message.split("\n");
        for (String line : lines)
            System.out.println(LocalDateTime.now().format(FMT) + ": [" + prefix + "] " + line);
    }
}
