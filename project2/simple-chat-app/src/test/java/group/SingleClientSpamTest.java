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

public class SingleClientSpamTest {

    @Test
    void runRapidBlastTest() throws Exception {
        System.out.println("--- Starting Single Client Spam Test ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        System.out.println("Waiting 2 seconds for GMS to start...");
        Thread.sleep(2000);

        GroupAPI alice = new GroupAPI("127.0.0.1", 10100, 10201, false, ChatMessage::deserialize);
        List<String> receivedOrder = Collections.synchronizedList(new ArrayList<>());

        System.out.println("Alice connecting to group...");
        JoinReply aliceJoin = alice.join("SpamGroup", "Alice");
        int groupId = aliceJoin.getGroupId();

        int spamCount = 10000;
        System.out.println("Alice is spamming " + spamCount + " messages instantly...");

        CountDownLatch messageLatch = new CountDownLatch(spamCount);
        Thread aliceReceiver = new Thread(() -> {
            try {
                while (!alice.isShuttingDown()) {
                    GroupMessagePair pair = alice.receive(List.of(groupId));
                    if (pair.getMessage() instanceof ChatMessage msg) {
                        receivedOrder.add(msg.getMessage());
                        messageLatch.countDown();
                    }
                }
            } catch (Exception e) {
                // Expected when shutting down
            }
        });
        aliceReceiver.start();

        // Send the messages
        for (int i = 1; i <= spamCount; i++) {
            alice.send(groupId, new ChatMessage("Alice", LocalDateTime.now(), "Message-" + i));
        }

        System.out.println("Finished sending. Waiting for network buffers to drain...");
        boolean allReceived = messageLatch.await(120, TimeUnit.SECONDS);

        System.out.println("\n--- TEST RESULTS ---");
        System.out.println("Alice received: " + receivedOrder.size() + " out of " + spamCount + " messages.");

        int returnExit = 0;
        if (!allReceived) {
            System.err.println("FAIL: Test timed out! Alice dropped messages. Retransmitter failed to rescue them.");
            returnExit = 1;
        } else if (receivedOrder.size() != spamCount) {
            System.err.println("FAIL: Count mismatch!");
            returnExit = 1;
        } else {
            System.out.println("SUCCESS: Alice received all messages!");
        }

        // Clean up
        System.out.println("Shutting down...");
        alice.shutdown();
        GMS.shutdown();

        assertFalse(returnExit == 1, "Test failed — see output above for details.");
    }
}