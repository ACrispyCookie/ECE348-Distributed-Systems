package group;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class CatocTest {

    @Test
    void runCatocBlastTest() throws Exception {
        int numClients = 3;
        int messagesPerClient = 1000;
        int basePort = 10200;

        int totalExpectedPerClient = messagesPerClient * numClients;
        int totalSystemMessages = totalExpectedPerClient * numClients;

        System.out.println("--- Starting CATOC (Causal & Total Order) Rapid Blast Test with " + numClients + " clients ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        System.out.println("Waiting 2 seconds for GMS to start...");
        Thread.sleep(2000);

        List<GroupAPI> clients = new ArrayList<>();
        List<List<String>> receivedOrders = new ArrayList<>();

        System.out.println("Clients connecting to group...");
        int groupId = -1;

        for (int i = 0; i < numClients; i++) {
            String clientName = "Client-" + i;
            GroupAPI api = new GroupAPI("127.0.0.1", 10100, basePort + i, true, true, ChatMessage::deserialize);
            clients.add(api);

            receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));

            JoinReply reply = api.join("CatocGroup", clientName);
            if (i == 0) groupId = reply.getGroupId();
        }

        Thread.sleep(5000);

        // --- LATCH LOGIC ---
        CountDownLatch receiveLatch = new CountDownLatch(totalSystemMessages);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numClients);

        // Setup Receiver Threads
        for (int i = 0; i < numClients; i++) {
            final int clientIndex = i;
            final GroupAPI api = clients.get(i);
            final int gId = groupId;

            new Thread(() -> {
                try {
                    while (!api.isShuttingDown()) {
                        GroupMessagePair pair = api.receive(Set.of(gId));
                        if (pair.getMessage() instanceof ChatMessage msg) {
                            receivedOrders.get(clientIndex).add(msg.getMessage());
                            receiveLatch.countDown();
                        }
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        // Setup Sender Threads
        for (int i = 0; i < numClients; i++) {
            final int clientIndex = i;
            final GroupAPI api = clients.get(i);
            final String clientName = "Client-" + i;
            final int gId = groupId;

            new Thread(() -> {
                try {
                    startSignal.await(); // Wait for the starter pistol
                    for (int j = 1; j <= messagesPerClient; j++) {
                        api.send(gId, new ChatMessage(clientName, LocalDateTime.now(), clientName + "-" + j));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneSignal.countDown();
                }
            }).start();
        }

        System.out.println("Firing the starter pistol... Blasting " + totalSystemMessages + " messages across the network!");
        startSignal.countDown();

        // Wait for sending threads to finish queuing
        doneSignal.await();
        System.out.println("Finished queueing. Waiting for network buffers to drain (up to 300 seconds)...");

        boolean allReceived = receiveLatch.await(300, TimeUnit.SECONDS);

        System.out.println("\n--- TEST RESULTS ---");
        boolean sizesMatch = true;
        for (int i = 0; i < numClients; i++) {
            int receivedSize = receivedOrders.get(i).size();
            System.out.println("Client-" + i + " received: " + receivedSize + " messages.");
            if (receivedSize != totalExpectedPerClient) {
                sizesMatch = false;
            }
        }

        boolean fail = false;
        if (!allReceived) {
            System.err.println("FAIL: Test timed out! Packets were dropped and not rescued by the retransmitter.");
            fail = true;
        } else if (!sizesMatch) {
            System.err.println("FAIL: Someone dropped messages! CATOC failed to deliver everything.");
            fail = true;
        } else {
            // Validation 1: Verify TOTAL ORDER (Everyone matches Client-0)
            List<String> referenceList = receivedOrders.get(0);
            boolean totalOrderMaintained = true;

            for (int i = 1; i < numClients; i++) {
                if (!referenceList.equals(receivedOrders.get(i))) {
                    System.err.println("FAIL: Mismatch found between Client-0 and Client-" + i + ". Total Order was violated.");
                    totalOrderMaintained = false;
                    fail = true;
                    break;
                }
            }

            if (totalOrderMaintained) {
                System.out.println("SUCCESS: Total Order (TRM) is correct across all " + numClients + " clients!");

                if (!verifyCausality(referenceList)) {
                    System.err.println("FAIL: Total order was achieved, but CAUSALITY was violated!");
                    fail = true;
                } else {
                    System.out.println("SUCCESS: Causal order is verified!");
                }
            }
        }

        // Clean up
        System.out.println("Shutting down...");
        for (GroupAPI api : clients) {
            api.shutdown();
        }
        GMS.shutdown();

        assertFalse(fail, "CATOC Test failed — see output above for details.");
    }

    private boolean verifyCausality(List<String> deliveredMessages) {
        Map<String, Integer> expectedNextSeq = new HashMap<>();

        for (String msg : deliveredMessages) {
            try {
                int lastDash = msg.lastIndexOf('-');
                String sender = msg.substring(0, lastDash);
                int seqNum = Integer.parseInt(msg.substring(lastDash + 1));

                expectedNextSeq.putIfAbsent(sender, 1);
                int expected = expectedNextSeq.get(sender);

                if (seqNum != expected) {
                    System.err.println("Causality Violation");
                    System.err.println("Expected " + sender + "-" + expected + " but got " + msg);
                    return false;
                }

                expectedNextSeq.put(sender, expected + 1);
            } catch (Exception e) {
                System.err.println("Could not parse message for causality check: " + msg);
            }
        }

        return true;
    }
}