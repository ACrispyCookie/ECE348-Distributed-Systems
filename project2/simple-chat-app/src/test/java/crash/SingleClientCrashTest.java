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

import static org.junit.jupiter.api.Assertions.assertFalse;

public class SingleClientCrashTest {

    @Test
    void runMidBroadcastCrashTest() throws Exception {

        String prop = System.getProperty("total", "true");
        boolean testTotalOrder = Boolean.parseBoolean(prop);

        int numClients = 3;
        int blastSize = 3000;
        int crashPoint = 2800;
        int basePort = 10200;

        System.out.println("--- Starting " + (testTotalOrder ? "TRM" : "URM") + " Mid-Broadcast Crash Test with " + numClients + " clients ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        Thread.sleep(3000);

        List<GroupAPI> clients = new ArrayList<>();
        List<List<String>> receivedOrders = new ArrayList<>();

        System.out.println("Clients connecting to group...");
        int groupId = -1;

        for (int i = 0; i < numClients; i++) {
            String clientName = "Client-" + i;

            GroupAPI api = new GroupAPI("127.0.0.1", 10100, basePort + i, testTotalOrder, false, ChatMessage::deserialize);
            clients.add(api);

            receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));

            JoinReply reply = api.join("CrashGroup", clientName);
            if (i == 0) groupId = reply.getGroupId();
        }

        Thread.sleep(3000); // Wait for GMS to sync everyone

        // Setup Listeners for the SURVIVORS only (Client 1 to N-1)
        for (int i = 1; i < numClients; i++) {
            final int clientIndex = i;
            final GroupAPI api = clients.get(i);
            final int gId = groupId;

            new Thread(() -> {
                try {
                    while (!api.isShuttingDown()) {
                        GroupMessagePair pair = api.receive(List.of(gId));
                        if (pair.getMessage() instanceof ChatMessage msg) {
                            receivedOrders.get(clientIndex).add(msg.getMessage());
                        }
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        System.out.println("Client-0 is starting a " + blastSize + " message blast...");
        System.out.println("Client-0 will crash at message " + crashPoint + "...");

        final int gId = groupId;
        Thread kamikazeThread = new Thread(() -> {
            try {
                GroupAPI kamikazeClient = clients.get(0);
                for (int i = 1; i <= blastSize; i++) {
                    kamikazeClient.send(gId, new ChatMessage("Client-0", LocalDateTime.now(), "KamikazeMsg-" + i));

                    if (i == crashPoint) {
                        System.err.println("Client-0 crashed!");
                        kamikazeClient.shutdown();
                        return; // Stop the JVM loop
                    }
                }
            } catch (Exception e) {
                System.out.println("Client-0's thread died: " + e.getMessage());
            }
        });

        kamikazeThread.start();
        kamikazeThread.join(); // Wait for Client-0 to finish crashing

        System.out.println("Waiting 10 seconds...");
        Thread.sleep(120000); // Fixed wait because we don't know exactly how many messages survived

        System.out.println("\n--- TEST RESULTS ---");
        boolean fail = false;

        for (int i = 1; i < numClients; i++) {
            System.out.println("Client-" + i + " received: " + receivedOrders.get(i).size() + " messages.");
        }

        // We use Client-1 as the new "source of truth" since Client-0 is dead
        List<String> referenceList = receivedOrders.get(1);

        if (referenceList.isEmpty()) {
            System.err.println("FAIL: No messages survived at all. Test is invalid.");
            fail = true;
        } else {
            if (testTotalOrder) {
                // TRM CHECK: Exact Match
                for (int i = 2; i < numClients; i++) {
                    if (!referenceList.equals(receivedOrders.get(i))) {
                        System.err.println("FAIL: Mismatch found between Client-1 and Client-" + i + ". TRM Takeover failed to sort correctly.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: TRM Coordinator Takeover Verified!");
                    System.out.println("Client-0 crashed, but the remaining clients perfectly sequenced the surviving packets.");
                }
            } else {
                // URM CHECK: Sort and Match
                List<String> sortedReference = new ArrayList<>(referenceList);
                Collections.sort(sortedReference);

                for (int i = 2; i < numClients; i++) {
                    List<String> sortedTarget = new ArrayList<>(receivedOrders.get(i));
                    Collections.sort(sortedTarget);

                    if (!sortedReference.equals(sortedTarget)) {
                        System.err.println("FAIL: URM violated! Client-1 and Client-" + i + " have different message sets.");
                        fail = true;
                        break;
                    }
                }
                if (!fail) {
                    System.out.println("SUCCESS: URM property verified!");
                    System.out.println("Even though Client-0 crashed mid-send, the survivors relayed the remaining packets perfectly.");
                }
            }
        }

        // Clean up
        System.out.println("Shutting down...");
        for (int i = 1; i < numClients; i++) {
            clients.get(i).shutdown();
        }
        GMS.shutdown();

        assertFalse(fail, "Crash Failure Test Failed!");
    }
}