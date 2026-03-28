package crash;

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

import static org.junit.jupiter.api.Assertions.assertFalse;

public class MultiClientCrashTest {

    @Test
    void runConcurrentCrashTest() throws Exception {
        String prop = System.getProperty("total", "true");
        boolean testTotalOrder = Boolean.parseBoolean(prop);
        String propC = System.getProperty("causal", "true");
        boolean testCausalOrder = Boolean.parseBoolean(propC);
        String type = testTotalOrder ? testCausalOrder ? "CTRM" : "TRM" : "URM";

        // --- CRASH CONFIGURATION ---
        // 0 = Coordinator crashes. 1 or 2 = Regular client crashes.
        int kamikazeIndex = Integer.parseInt(System.getProperty("crashIndex", "1"));

        int numClients = 3;
        int blastSize = 4000;
        int crashPoint = 3000;
        int basePort = 10200;

        System.out.println("--- Starting " + type + " Concurrent Crash Test ---");
        System.out.println("Target: Client-" + kamikazeIndex + (kamikazeIndex == 0 ? " (COORDINATOR)" : " (REGULAR CLIENT)"));

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        Thread.sleep(3000);

        List<GroupAPI> clients = new ArrayList<>();
        List<List<String>> receivedOrders = new ArrayList<>();

        System.out.println("Clients connecting to group...");
        int groupId = -1;

        for (int i = 0; i < numClients; i++) {
            String clientName = "Client-" + i;

            GroupAPI api = new GroupAPI("127.0.0.1", 10100, basePort + i, testTotalOrder, testCausalOrder, ChatMessage::deserialize);
            clients.add(api);

            receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));

            JoinReply reply = api.join("CrashGroup", clientName);
            if (i == 0) groupId = reply.getGroupId();
        }

        Thread.sleep(4000);

        // Setup Listeners for SURVIVORS ONLY
        for (int i = 0; i < numClients; i++) {
            if (i == kamikazeIndex) continue;

            final int clientIndex = i;
            final GroupAPI api = clients.get(i);
            final int gId = groupId;

            new Thread(() -> {
                try {
                    while (!api.isShuttingDown()) {
                        GroupMessagePair pair = api.receive(Set.of(gId));
                        if (pair.getMessage() instanceof ChatMessage msg) {
                            receivedOrders.get(clientIndex).add(msg.getMessage());
                        }
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numClients);

        System.out.println("All clients preparing to send " + blastSize + " messages each...");
        System.out.println("Client-" + kamikazeIndex + " will crash at its message #" + crashPoint + "...");

        for (int i = 0; i < numClients; i++) {
            final int clientIndex = i;
            final GroupAPI api = clients.get(i);
            final int gId = groupId;

            new Thread(() -> {
                try {
                    startSignal.await(); // Wait for the starter pistol

                    for (int j = 1; j <= blastSize; j++) {
                        api.send(gId, new ChatMessage("Client-" + clientIndex, LocalDateTime.now(), "Msg-" + clientIndex + "-" + j));

                        // If this is the kamikaze client and we reached the crash point
                        if (clientIndex == kamikazeIndex && j == crashPoint) {
                            System.err.println("Client-" + kamikazeIndex + " crashed!");
                            api.shutdown();
                            break; // Stop sending
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Client-" + clientIndex + " thread interrupted.");
                } finally {
                    doneSignal.countDown();
                }
            }).start();
        }

        System.out.println("Starting!");
        startSignal.countDown();

        doneSignal.await();

        System.out.println("Finished queueing. Waiting 30 seconds...");
        Thread.sleep(30000);

        System.out.println("\n--- TEST RESULTS ---");
        boolean fail = false;

        for (int i = 0; i < numClients; i++) {
            if (i == kamikazeIndex) continue;
            System.out.println("Client-" + i + " received: " + receivedOrders.get(i).size() + " total messages.");
        }

        // Dynamically select the "source of truth"
        int referenceIndex = (kamikazeIndex == 0) ? 1 : 0;
        List<String> referenceList = receivedOrders.get(referenceIndex);

        if (referenceList.isEmpty()) {
            System.err.println("FAIL: No messages survived at all. Test is invalid.");
            fail = true;
        } else {
            if (testTotalOrder) {
                // TRM CHECK: Exact Match
                for (int i = 0; i < numClients; i++) {
                    if (i == kamikazeIndex || i == referenceIndex) continue;

                    if (!referenceList.equals(receivedOrders.get(i))) {
                        System.err.println("FAIL: Mismatch found between Client-" + referenceIndex + " and Client-" + i + ". TRM Takeover failed to sort correctly during concurrent blast.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: TRM Coordinator takeover verified!");
                }
            } else {
                // URM CHECK: Sort and Match
                List<String> sortedReference = new ArrayList<>(referenceList);
                Collections.sort(sortedReference);

                for (int i = 0; i < numClients; i++) {
                    if (i == kamikazeIndex || i == referenceIndex) continue;

                    List<String> sortedTarget = new ArrayList<>(receivedOrders.get(i));
                    Collections.sort(sortedTarget);

                    if (!sortedReference.equals(sortedTarget)) {
                        System.err.println("FAIL: URM violated! Client-" + referenceIndex + " and Client-" + i + " have different message sets.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: URM property verified!");
                }
            }
        }

        // Clean up
        System.out.println("Shutting down...");
        for (int i = 0; i < numClients; i++) {
            if (i != kamikazeIndex) {
                clients.get(i).shutdown();
            }
        }
        GMS.shutdown();
        gmsThread.join();

        assertFalse(fail, "Crash failure test failed!");
    }
}