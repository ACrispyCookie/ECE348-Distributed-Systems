package group;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.GMS;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TwoClientsTest {

    public static void main(String[] args) throws Exception {
        System.out.println("--- Starting TRM Rapid Blast Test ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        System.out.println("Waiting 2 seconds for GMS to start...");
        Thread.sleep(2000);

        GroupAPI alice = new GroupAPI("127.0.0.1", 10100, 20201, ChatMessage::deserialize);
        GroupAPI bob = new GroupAPI("127.0.0.1", 10100, 20202, ChatMessage::deserialize);

        List<String> aliceReceivedOrder = Collections.synchronizedList(new ArrayList<>());
        List<String> bobReceivedOrder = Collections.synchronizedList(new ArrayList<>());

        // 3. Join the group and wait for TRM/URM state to settle
        System.out.println("Clients connecting to group...");
        JoinReply aliceJoin = alice.join("TestGroup", "Alice");
        bob.join("TestGroup", "Bob");
        Thread.sleep(2000);

        int groupId = aliceJoin.getGroupId();
        // 4. Setup the Receive Threads (since receive() is blocking)
        Thread aliceReceiver = new Thread(() -> {
            try {
                while (!alice.isShuttingDown()) {
                    GroupMessagePair pair = alice.receive(List.of(groupId));
                    if (pair.getMessage() instanceof ChatMessage msg) {
                        aliceReceivedOrder.add(msg.getMessage());
                        System.out.println("ALice " + msg.getMessage());
                    }
                }
            } catch (Exception e) {
                // Expected when shutting down
            }
        });
        aliceReceiver.start();

        Thread bobReceiver = new Thread(() -> {
            try {
                while (!bob.isShuttingDown()) {
                    GroupMessagePair pair = bob.receive(List.of(groupId));
                    if (pair.getMessage() instanceof ChatMessage msg) {
                        bobReceivedOrder.add(msg.getMessage());
                    }
                }
            } catch (Exception e) {
                // Expected when shutting down
            }
        });
        bobReceiver.start();

        // 5. The Starter Pistol
        int messagesPerClient = 100;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        // ALICE'S BLAST THREAD
        new Thread(() -> {
            try {
                startSignal.await(); // Wait for the pistol
                for (int i = 1; i <= messagesPerClient; i++) {
                    alice.send(groupId, new ChatMessage("Alice", LocalDateTime.now(), "Alice-" + i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneSignal.countDown();
            }
        }).start();

        // BOB'S BLAST THREAD
        new Thread(() -> {
            try {
                startSignal.await(); // Wait for the pistol
                for (int i = 1; i <= messagesPerClient; i++) {
                    bob.send(groupId, new ChatMessage("Bob", LocalDateTime.now(), "Bob-" + i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneSignal.countDown();
            }
        }).start();

        System.out.println("Firing the starter pistol... Blasting " + (messagesPerClient * 2) + " messages!");
        startSignal.countDown();

        doneSignal.await();
        System.out.println("Finished sending. Waiting 4 seconds...");
        Thread.sleep(120000);

        System.out.println("\n--- TEST RESULTS ---");
        System.out.println("Alice received: " + aliceReceivedOrder.size() + " messages.");
        System.out.println("Bob received:   " + bobReceivedOrder.size() + " messages.");

        boolean sizesMatch = aliceReceivedOrder.size() == (messagesPerClient * 2) &&
                bobReceivedOrder.size() == (messagesPerClient * 2);

        boolean fail = false;
        if (!sizesMatch) {
            System.err.println("FAIL: Someone dropped messages! URM failed.");
            fail = true;
        } else {
            System.out.println(aliceReceivedOrder);
            System.out.println(bobReceivedOrder);
            boolean orderMatches = aliceReceivedOrder.equals(bobReceivedOrder);
            fail = !orderMatches;

            if (orderMatches) {
                System.out.println("SUCCESS: Total Order is correct!");
                System.out.println("Sample interweaved order: " + aliceReceivedOrder.subList(0, Math.min(10, messagesPerClient)));
            } else {
                System.err.println("FAIL: Arrays do not match.");
                for (int i = 0; i < aliceReceivedOrder.size(); i++) {
                    if (!aliceReceivedOrder.get(i).equals(bobReceivedOrder.get(i))) {
                        System.err.println("Mismatch at index " + i + ": Alice has [" + aliceReceivedOrder.get(i) + "] but Bob has [" + bobReceivedOrder.get(i) + "]");
                        break;
                    }
                }
            }
        }

        // Clean up
        System.out.println("Shutting down...");
        alice.shutdown();
        bob.shutdown();
        GMS.shutdown();

        System.exit(fail ? 1 : 0);
    }
}