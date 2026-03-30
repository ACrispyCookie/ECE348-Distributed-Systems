package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;
import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.ViewUpdateMessage;

import java.util.concurrent.ExecutionException;

public class MessageReceiver extends Thread {

    private boolean shutdown = false;
    // --- BENCHMARK VARIABLES ---
    private boolean isBenchmarking = false;
    private int benchmarkCounter = 0;
    private long startTime = 0;
    private int expectedTotalMessages = 0;

    public MessageReceiver() {
        setName("sca-message-receiver");
    }

    @Override
    public void run() {
        while (!ChatApp.isShuttingDown() && !shutdown) {
            try {
                GroupMessagePair message = ChatApp.getApi().receive(ChatApp.getChatManager().getGroups());

                if (message.getMessage() instanceof ViewUpdateMessage update) {
                    handleUpdate(update);
                } else {
                    handleMessage(message.getGroupId(), (ChatMessage) message.getMessage());
                }
            } catch (InterruptedException ignored) {

            } catch (ExecutionException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void handleUpdate(ViewUpdateMessage update) {
        if (update.getAction() == ViewUpdateMessage.UpdateAction.JOINED) {
            ChatApp.getChatManager().memberJoined(update.getGroupId(), update.getClient());
        } else {
            ChatApp.getChatManager().memberLeft(update.getGroupId(), update.getClient());
        }
    }

    private void handleMessage(int groupId, ChatMessage message) {
        String text = message.getMessage();

        if (text.startsWith("START_BENCHMARK_")) {
            String[] parts = text.split("_");
            if (parts.length < 4) {
                Printer.printErr("Invalid benchmark command! Use: START_BENCHMARK_SINGLE_<amount> or START_BENCHMARK_ALL_<amount>");
                return;
            }

            String mode = parts[2].toUpperCase();
            int amountPerClient = Integer.parseInt(parts[3]);
            int numClients = ChatApp.getChatManager().getGroupSize(groupId);

            boolean isSingleMode = mode.equals("SINGLE");

            this.expectedTotalMessages = isSingleMode ? amountPerClient : (amountPerClient * numClients);
            this.benchmarkCounter = 0;
            this.isBenchmarking = true;
            this.startTime = System.currentTimeMillis();

            Printer.printInfo("Benchmark started! Mode: " + mode + ". Disabled output. Expecting " + expectedTotalMessages + " total messages...");

            String myUsername = ChatApp.getChatManager().getUsername(groupId);
            boolean amITheSender = message.getAuthor().equals(myUsername);

            if (!isSingleMode || amITheSender) {
                ChatApp.getInputManager().triggerBenchmarkSpam(groupId, amountPerClient);
            }
            return;
        }

        if (isBenchmarking) {
            benchmarkCounter++;
            if (benchmarkCounter % 500 == 0) System.out.print(".");

            if (benchmarkCounter == expectedTotalMessages) {
                long endTime = System.currentTimeMillis();
                double durationSeconds = (endTime - startTime) / 1000.0;
                double throughput = expectedTotalMessages / durationSeconds;

                System.out.println();
                Printer.printInfo("Benchmark complete!");
                Printer.printInfo("Time: " + durationSeconds + " seconds");
                Printer.printInfo("Throughput: " + String.format("%.2f", throughput) + " msg/sec");

                isBenchmarking = false;
            }
        } else {
            ChatApp.getChatManager().printChatMessage(groupId, message);
        }
    }

    public void shutdown() {
        shutdown = true;
        interrupt();
    }
}
