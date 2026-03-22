package dev.ioannikas.gms.utility;


import dev.ioannikas.gms.managers.GroupManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Printer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static GroupManager manager;

    public static void initialize(GroupManager manager) {
        Printer.manager = manager;
    }

    public static void printInfo(String message) {
        print("INFO", message);
    }

    public static void printDebug(String message) {
        print("DEBUG", message);
    }

    public static void printGroup(int groupId, String message) {
        print("GROUP " + manager.getGroupName(groupId), message);
    }

    public static void printErr(String message) {
        print("ERROR", message);
    }

    private static void print(String prefix, String message) {
        System.err.println(LocalDateTime.now().format(FMT) + ": [" + prefix + "] " + message);
    }
}
