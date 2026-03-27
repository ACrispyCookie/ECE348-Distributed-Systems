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

public class SingleClientSpamTest {

    public static void main(String[] args) throws Exception {
        System.out.println("--- Starting Single Client Spam Test ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        System.out.println("Waiting 2 seconds for GMS to start...");
        Thread.sleep(2000);

        GroupAPI alice = new GroupAPI("127.0.0.1", 10100, 20201, ChatMessage::deserialize);
        List<String> receivedOrder = Collections.synchronizedList(new ArrayList<>());

        System.out.println("Alice connecting to group...");
        JoinReply aliceJoin = alice.join("SpamGroup", "Alice");
        int groupId = aliceJoin.getGroupId();

        Thread.sleep(1000);
        Thread aliceReceiver = new Thread(() -> {
            try {
                while (!alice.isShuttingDown()) {
                    GroupMessagePair pair = alice.receive(List.of(groupId));
                    if (pair.getMessage() instanceof ChatMessage msg) {
                        receivedOrder.add(msg.getMessage());
                    }
                }
            } catch (Exception e) {
                // Expected when shutting down
            }
        });
        aliceReceiver.start();

        int spamCount = 1000;
        System.out.println("Alice is spamming " + spamCount + " messages instantly...");
        for (int i = 1; i <= spamCount; i++) {
            alice.send(groupId, new ChatMessage("Alice", LocalDateTime.now(), "Message-" + i));
        }

        System.out.println("Finished sending. Waiting 10 seconds for local buffers to drain...");
        Thread.sleep(100000);

        System.out.println("\n--- TEST RESULTS ---");
        System.out.println("Alice received: " + receivedOrder.size() + " out of " + spamCount + " messages.");

        if (receivedOrder.size() != spamCount)
            System.err.println("FAIL: Alice dropped her own messages! Local self-injection failed.");

        // Clean up
        System.out.println("Shutting down...");
        alice.shutdown();
        GMS.shutdown();

        System.exit(0);
    }
}