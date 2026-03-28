package group;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class TwoClientsTest {

    @Test
    void runRapidBlastTest() throws Exception {

        String prop = System.getProperty("total", "true");
        boolean testTotalOrder = Boolean.parseBoolean(prop);

        System.out.println("--- Starting " + (testTotalOrder ? "TRM" : "URM") + " Rapid Blast Test ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        System.out.println("Waiting 2 seconds for GMS to start...");
        Thread.sleep(2000);

        GroupAPI alice = new GroupAPI("127.0.0.1", 10100, 10201, testTotalOrder, ChatMessage::deserialize);
        GroupAPI bob = new GroupAPI("127.0.0.1", 10100, 10202, testTotalOrder, ChatMessage::deserialize);

        List<String> aliceReceivedOrder = Collections.synchronizedList(new ArrayList<>());
        List<String> bobReceivedOrder = Collections.synchronizedList(new ArrayList<>());

        System.out.println("Clients connecting to group...");
        JoinReply aliceJoin = alice.join("TestGroup", "Alice");
        bob.join("TestGroup", "Bob");
        Thread.sleep(2000);

        int groupId = aliceJoin.getGroupId();

        // --- LATCH LOGIC ---
        int messagesPerClient = 1000;
        int totalExpectedPerClient = messagesPerClient * 2; // 8000 each

        // One latch to track ALL expected messages across BOTH clients (16000 total)
        CountDownLatch receiveLatch = new CountDownLatch(totalExpectedPerClient * 2);

        Thread aliceReceiver = new Thread(() -> {
            try {
                while (!alice.isShuttingDown()) {
                    GroupMessagePair pair = alice.receive(List.of(groupId));
                    if (pair.getMessage() instanceof ChatMessage msg) {
                        aliceReceivedOrder.add(msg.getMessage());
                        receiveLatch.countDown(); // Decrement latch
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
                        receiveLatch.countDown(); // Decrement latch
                    }
                }
            } catch (Exception e) {
                // Expected when shutting down
            }
        });
        bobReceiver.start();

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        // ALICE'S BLAST THREAD
        new Thread(() -> {
            try {
                startSignal.await(); // Wait for the latch
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
                startSignal.await(); // Wait for the latch
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

        // Wait for sending threads to finish loops
        doneSignal.await();
        System.out.println("Finished sending. Waiting for network buffers to drain (up to 120 seconds)...");
        boolean allReceived = receiveLatch.await(300, TimeUnit.SECONDS);

        System.out.println("\n--- TEST RESULTS ---");
        System.out.println("Alice received: " + aliceReceivedOrder.size() + " messages.");
        System.out.println("Bob received:   " + bobReceivedOrder.size() + " messages.");

        boolean sizesMatch = aliceReceivedOrder.size() == totalExpectedPerClient &&
                bobReceivedOrder.size() == totalExpectedPerClient;

        boolean fail;
        if (!allReceived) {
            System.err.println("FAIL: Test timed out! Packets were dropped and not rescued by the retransmitter.");
            fail = true;
        } else if (!sizesMatch) {
            System.err.println("FAIL: Someone dropped messages! URM/BM failed to deliver everything.");
            fail = true;
        } else {
            if (testTotalOrder) {
                // TRM CHECK: Must be exact same order
                boolean orderMatches = aliceReceivedOrder.equals(bobReceivedOrder);
                fail = !orderMatches;

                if (orderMatches) {
                    System.out.println("SUCCESS: Total Order is correct!");
                    System.out.println("Sample interweaved order: " + aliceReceivedOrder.subList(0, Math.min(10, messagesPerClient)));
                } else {
                    System.err.println("FAIL: Arrays do not match. TRM failed to sort correctly.");
                    for (int i = 0; i < aliceReceivedOrder.size(); i++) {
                        if (!aliceReceivedOrder.get(i).equals(bobReceivedOrder.get(i))) {
                            System.err.println("Mismatch at index " + i + ": Alice has [" + aliceReceivedOrder.get(i) + "] but Bob has [" + bobReceivedOrder.get(i) + "]");
                            break;
                        }
                    }
                }
            } else {
                // URM CHECK: Sort arrays first, then check equality (verifies all payloads are present)
                List<String> sortedAlice = new ArrayList<>(aliceReceivedOrder);
                List<String> sortedBob = new ArrayList<>(bobReceivedOrder);
                Collections.sort(sortedAlice);
                Collections.sort(sortedBob);

                boolean elementsMatch = sortedAlice.equals(sortedBob);
                fail = !elementsMatch;

                if (elementsMatch) {
                    System.out.println("SUCCESS: Uniform Reliable Multicast is correct! All messages delivered to both.");
                    System.out.println("Note: Total order was intentionally ignored.");
                } else {
                    System.err.println("FAIL: URM failed. Alice and Bob received different sets of messages.");
                }
            }
        }

        // Clean up
        System.out.println("Shutting down...");
        alice.shutdown();
        bob.shutdown();
        GMS.shutdown();

        assertFalse(fail, "Test failed — see output above for details.");
    }
}