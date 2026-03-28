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

import static org.junit.jupiter.api.Assertions.assertFalse;

public class SingleClientCrashTest {

    @Test
    void runMidBroadcastCrashTest() throws Exception {
        String prop = System.getProperty("total", "true");
        boolean testTotalOrder = Boolean.parseBoolean(prop);
        String propC = System.getProperty("causal", "true");
        boolean testCausalOrder = Boolean.parseBoolean(propC);
        String type = testTotalOrder ? testCausalOrder ? "CTRM" : "TRM" : "URM";

        // --- CRASH CONFIGURATION ---
        // 0 = Coordinator crashes. 1 or 2 = Regular client crashes.
        int kamikazeIndex = Integer.parseInt(System.getProperty("crashIndex", "1"));

        int numClients = 3;
        int blastSize = 10000;
        int crashPoint = 9000;
        int basePort = 10200;

        System.out.println("--- Starting " + type + " Mid-Broadcast Crash Test ---");
        System.out.println("Target: Client-" + kamikazeIndex + (kamikazeIndex == 0 ? " (COORDINATOR)" : " (REGULAR CLIENT)"));

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        Thread.sleep(2000);

        List<GroupAPI> clients = new ArrayList<>();
        List<List<String>> receivedOrders = new ArrayList<>();

        System.out.println("Clients connecting to group...");
        int groupId = -1;

        // Client-0 joins first, making it the Coordinator
        for (int i = 0; i < numClients; i++) {
            String clientName = "Client-" + i;

            GroupAPI api = new GroupAPI("127.0.0.1", 10100, basePort + i, testTotalOrder, testCausalOrder, ChatMessage::deserialize);
            clients.add(api);

            receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));

            JoinReply reply = api.join("CrashGroup", clientName);
            if (i == 0) groupId = reply.getGroupId();
        }

        Thread.sleep(5000); // Wait for GMS to sync everyone

        // Setup Listeners for SURVIVORS ONLY (Skip the kamikaze client)
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

        System.out.println("Client-" + kamikazeIndex + " is starting a " + blastSize + " message spam...");
        System.out.println("Client-" + kamikazeIndex + " will crash at message " + crashPoint + "...");

        final int gId = groupId;
        Thread kamikazeThread = new Thread(() -> {
            try {
                GroupAPI kamikazeClient = clients.get(kamikazeIndex);
                for (int i = 1; i <= blastSize; i++) {
                    kamikazeClient.send(gId, new ChatMessage("Client-" + kamikazeIndex, LocalDateTime.now(), "KamikazeMsg-" + i));

                    if (i == crashPoint) {
                        System.err.println("Client-" + kamikazeIndex + " crashed!");
                        kamikazeClient.shutdown();
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Client-" + kamikazeIndex + "'s thread died: " + e.getMessage());
            }
        });

        kamikazeThread.start();
        kamikazeThread.join(); // Wait for the crash

        System.out.println("Waiting 30 seconds for buffers to drain...");
        Thread.sleep(30000); // Lowered sleep to 10s for faster testing, increase if it drops packets

        System.out.println("\n--- TEST RESULTS ---");
        boolean fail = false;

        for (int i = 0; i < numClients; i++) {
            if (i == kamikazeIndex) continue;
            System.out.println("Client-" + i + " received: " + receivedOrders.get(i).size() + " messages.");
        }

        // Dynamically select the "source of truth" (If Client 0 died, use Client 1. If 1 died, use 0).
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
                        System.err.println("FAIL: Mismatch found between Client-" + referenceIndex + " and Client-" + i + ". TRM Takeover failed to sort correctly.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: TRM Coordinator Takeover Verified!");
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

        assertFalse(fail, "Crash Failure Test Failed!");
    }
}