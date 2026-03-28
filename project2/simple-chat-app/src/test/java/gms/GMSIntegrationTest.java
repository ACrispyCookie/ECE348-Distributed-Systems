package gms;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class GMSIntegrationTest {

    private static final int GMS_PORT = 10100;
    private static Thread serverThread;
    private GroupAPI client1;
    private GroupAPI client2;

    @BeforeAll
    static void startGMS() throws InterruptedException {
        serverThread = new Thread(() -> GMS.main(new String[]{String.valueOf(GMS_PORT)}));
        serverThread.start();
        Thread.sleep(200);
    }

    @AfterAll
    static void shutdownGMS() throws InterruptedException {
        GMS.shutdown();
        serverThread.join();
    }

    @BeforeEach
    void setUp() throws Exception {
        client1 = new GroupAPI("127.0.0.1", GMS_PORT, 10200, false, ChatMessage::deserialize);
        client2 = new GroupAPI("127.0.0.1", GMS_PORT, 10201, false, ChatMessage::deserialize);
    }

    @AfterEach
    void tearDown() {
        if (client1 != null) client1.shutdown();
        if (client2 != null) client2.shutdown();
    }

    @Test
    void testTwoClientsCanJoinSameGroup() {
        JoinReply reply1 = client1.join("GlobalChat", "User1");
        JoinReply reply2 = client2.join("GlobalChat", "User2");

        assertNotNull(reply1, "Reply1 should not be null");
        assertNotNull(reply2, "Reply2 should not be null");
        assertSame(JoinReply.FailReason.NONE, reply1.getReason(), "User1 should join successfully");
        assertSame(JoinReply.FailReason.NONE, reply2.getReason(), "User2 should join successfully");

        assertEquals(reply1.getGroupId(), reply2.getGroupId(), "Both users should be assigned the same Group ID");
    }

    @Test
    void testLeaveGroupFiresUpdate() throws InterruptedException, ExecutionException {
        client1.join("Chat", "User1");
        int groupId = client2.join("Chat", "User2").getGroupId();
        client1.leave(groupId);

        Object updatePacket = client2.receive(List.of(groupId));
        assertNotNull(updatePacket, "Client2 should receive a view update after Client1 leaves");
    }
}