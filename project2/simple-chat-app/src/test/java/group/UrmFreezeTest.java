package group;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.gms.GMS;
import dev.ioannikas.group.gms.serializable.PacketTCP;
// IMPORTANT: Ensure this path matches your actual project structure
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;

import dev.ioannikas.group.gms.serializable.requests.GMSRequest;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UrmFreezeTest {

    @Test
    void testUrmBlocksUntilUnanimous() throws Exception {
        System.out.println("--- Starting URM Freeze & Rescue Test ---");

        Thread gmsThread = new Thread(() -> GMS.main(new String[]{"10100"}));
        gmsThread.start();
        Thread.sleep(2000);

        List<List<String>> receivedOrders = new ArrayList<>();
        receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));
        receivedOrders.add(Collections.synchronizedList(new ArrayList<>()));

        // Start two clients (URM enabled, TRM disabled for pure testing)
        GroupAPI client0 = new GroupAPI("127.0.0.1", 10100, 10200, false, false, ChatMessage::deserialize);
        GroupAPI client1 = new GroupAPI("127.0.0.1", 10100, 10201, false, false, ChatMessage::deserialize);

        int groupId = client0.join("FreezeGroup", "Client-0").getGroupId();
        client1.join("FreezeGroup", "Client-1");

        for (int i = 0; i < 2; i++) {
            final int index = i;
            final GroupAPI api = (i == 0) ? client0 : client1;
            new Thread(() -> {
                try {
                    while (!api.isShuttingDown()) {
                        GroupMessagePair pair = api.receive(Set.of(groupId));
                        if (pair.getMessage() instanceof ChatMessage msg) {
                            receivedOrders.get(index).add(msg.getMessage());
                        }
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        System.out.println("Spawning zombie client (TCP alive, UDP dead)...");
        SocketChannel zombieTcp = SocketChannel.open(new InetSocketAddress("127.0.0.1", 10100));

        GMSJoinRequest joinReq = new GMSJoinRequest("ZombieUser", 0, "FreezeGroup", "127.0.0.1", 10299);
        ByteBuffer buffer = PacketTCP.serialize(joinReq);
        while (buffer.hasRemaining()) {
            zombieTcp.write(buffer);
        }

        Thread.sleep(5000);

        System.out.println("Client-0 sending a message. It should freeze...");
        client0.send(groupId, new ChatMessage("Client-0", LocalDateTime.now(), "Orphan-Message"));

        // CatocManager should perfectly buffer it and refuse to deliver it.
        Thread.sleep(4000);

        System.out.println("Checking deliveries during the freeze...");
        assertEquals(0, receivedOrders.get(0).size(), "FAIL: Client-0 delivered early!");
        assertEquals(0, receivedOrders.get(1).size(), "FAIL: Client-1 delivered early!");
        System.out.println("SUCCESS: URM successfully blocked delivery! (0 messages delivered)");

        System.out.println("Killing the Zombie Client TCP Connection...");
        zombieTcp.close();

        // Give GMS 4 seconds to detect the broken pipe and broadcast the LEFT update
        Thread.sleep(4000);

        System.out.println("Checking deliveries after the rescue...");

        // The CatocManager should process the LEFT update, realize the Zombie is gone,
        // instantly achieve unanimous ACKs among the survivors, and deliver the message!
        assertEquals(1, receivedOrders.get(0).size(), "FAIL: Client-0 failed to rescue the message!");
        assertEquals(1, receivedOrders.get(1).size(), "FAIL: Client-1 failed to rescue the message!");

        System.out.println("SUCCESS: URM delivered the frozen message!");

        // Cleanup
        client0.shutdown();
        client1.shutdown();
        GMS.shutdown();
    }
}