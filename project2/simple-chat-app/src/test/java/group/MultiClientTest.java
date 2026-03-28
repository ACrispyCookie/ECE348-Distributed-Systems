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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class MultiClientTest {

    @Test
    void runRapidBlastTest() throws Exception {
        String prop = System.getProperty("total", "true");
        boolean testTotalOrder = Boolean.parseBoolean(prop);
        String propC = System.getProperty("causal", "true");
        boolean testCausalOrder = Boolean.parseBoolean(propC);
        String type = testTotalOrder ? testCausalOrder ? "CTRM" : "TRM" : "URM";

        int numClients = 2;
        int messagesPerClient = 1000;
        int basePort = 10200;

        int totalExpectedPerClient = messagesPerClient * numClients;
        int totalSystemMessages = totalExpectedPerClient * numClients;
        System.out.println("--- Starting " + type + " Rapid Blast Test with " + numClients + " clients ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        System.out.println("Waiting 2 seconds for GMS to start...");
        Thread.sleep(2000);

        List<GroupAPI> clients = new ArrayList<>();
        List<List<String>> receivedOrders = new ArrayList<>();

        System.out.println("Clients connecting to group...");
        int groupId = -1;

        // Initialize and Join
        for (int i = 0; i < numClients; i++) {
            String clientName = "Client-" + i;
            GroupAPI api = new GroupAPI("127.0.0.1", 10100, basePort + i, testTotalOrder, testCausalOrder, ChatMessage::deserialize);
            clients.add(api);

            receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));

            JoinReply reply = api.join("TestGroup", clientName);
            if (i == 0) groupId = reply.getGroupId();
        }

        Thread.sleep(5000); // Give GMS time to sync all members

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
                } catch (Exception e) {

                }
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

        System.out.println("Starting... Waiting for " + totalSystemMessages + " to be received!");
        startSignal.countDown();

        // Wait for sending threads to finish queuing
        doneSignal.await();
        System.out.println("Finished queueing. Waiting for message to arrive (up to 300 seconds)...");

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
            System.err.println("FAIL: Someone dropped messages! URM/BM failed to deliver everything.");
            fail = true;
        } else {
            // Validation: Compare everyone against Client-0
            List<String> referenceList = receivedOrders.get(0);

            if (testTotalOrder) {
                // TRM CHECK
                for (int i = 1; i < numClients; i++) {
                    if (!referenceList.equals(receivedOrders.get(i))) {
                        System.err.println("FAIL: Mismatch found between Client-0 and Client-" + i + ". TRM failed to sort correctly.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: Total Order (TRM) is correct across all " + numClients + " clients!");
                }
            } else {
                // URM CHECK
                List<String> sortedReference = new ArrayList<>(referenceList);
                Collections.sort(sortedReference);

                for (int i = 1; i < numClients; i++) {
                    List<String> sortedTarget = new ArrayList<>(receivedOrders.get(i));
                    Collections.sort(sortedTarget);

                    if (!sortedReference.equals(sortedTarget)) {
                        System.err.println("FAIL: URM failed. Client-0 and Client-" + i + " received different message sets.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: Uniform Reliable Multicast is correct across all " + numClients + " clients!");
                }
            }
        }

        // Clean up
        System.out.println("Shutting down...");
        for (GroupAPI api : clients) {
            api.shutdown();
        }
        GMS.shutdown();
        gmsThread.join();

        assertFalse(fail, "Test failed — see output above for details.");
    }
}