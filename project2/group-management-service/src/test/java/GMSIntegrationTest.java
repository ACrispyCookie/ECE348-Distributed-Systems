import dev.ioannikas.gms.singlethread.SingleThreadGMS;
import dev.ioannikas.grouplib.;
import dev.ioannikas.grouplib.api.JoinReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GMSIntegrationTest {

    private final int GMS_PORT = 5050; // Use a distinct port for testing
    private Thread serverThread;
    private GroupAPI client1;
    private GroupAPI client2;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Start GMS in a background thread
        serverThread = new Thread(() -> {
            SingleThreadGMS.main(new String[]{String.valueOf(GMS_PORT)});
        });
        serverThread.start();

        // Give the server 100ms to bind to the port
        Thread.sleep(100);

        // 2. Initialize test clients
        client1 = new GroupAPI();
        client1.init("127.0.0.1", GMS_PORT, 6001); // Client 1 UDP port

        client2 = new GroupAPI();
        client2.init("127.0.0.1", GMS_PORT, 6002); // Client 2 UDP port
    }

    @AfterEach
    void tearDown() {
        client1.shutdown();
        client2.shutdown();
        // You'll need to implement a public shutdown() in SingleThreadGMS
        // SingleThreadGMS.shutdown(); 
        serverThread.interrupt();
    }

    @Test
    void testTwoClientsCanJoinSameGroup() {
        JoinReply reply1 = client1.join("GlobalChat", "User1");
        JoinReply reply2 = client2.join("GlobalChat", "User2");

        assertTrue(reply1.isSuccess(), "User1 should join successfully");
        assertTrue(reply2.isSuccess(), "User2 should join successfully");

        assertEquals(reply1.getGroupId(), reply2.getGroupId(), "Both users should be in the same group ID");

        // At this point, User2's reply should contain User1 in its peer list (if your API supports returning peers)
    }

    @Test
    void testLeaveGroupFiresUpdate() throws InterruptedException {
        // This tests the assignment requirement: "Each time group membership changes, 
        // the GMS notifies all member processes accordingly."

        client1.join("Chat", "User1");
        int groupId = client2.join("Chat", "User2").getGroupId();

        // User1 leaves
        client1.leave(groupId);

        // Client2 should receive a GMSUpdateReply automatically via TCP
        // We use block=true to wait for it.
        Object updatePacket = client2.receive(groupId, true);

        assertNotNull(updatePacket, "Client2 should receive a view update");
        // Assert that the update packet shows User1 is gone
    }
}