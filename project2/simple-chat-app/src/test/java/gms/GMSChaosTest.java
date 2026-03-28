package gms;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.api.LeaveReply;
import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GMSChaosTest {

    private static final int GMS_PORT = 10100;
    private static Thread serverThread;

    private final int NUM_CLIENTS = 20;
    private final List<GroupAPI> clients = new ArrayList<>();

    @BeforeAll
    static void startGMS() throws InterruptedException {
        serverThread = new Thread(() -> GMS.main(new String[]{String.valueOf(GMS_PORT)}));
        serverThread.start();
        Thread.sleep(300);
    }

    @AfterAll
    static void shutdownGMS() throws InterruptedException {
        assertSame(0, GMS.connectedClients());
        GMS.shutdown();
        serverThread.join();
    }

    @BeforeEach
    void setUp() throws Exception {
        for (int i = 0; i < NUM_CLIENTS; i++) {
            GroupAPI client = new GroupAPI("127.0.0.1", GMS_PORT, 10200 + i, false, false, ChatMessage::deserialize);
            clients.add(client);
        }
    }

    @AfterEach
    void tearDown() {
        for (GroupAPI client : clients) {
            client.shutdown();
        }
    }

    @Test
    void testRealisticConcurrentChaos() throws InterruptedException, IOException {
        int opsPerClient = 15;
        int totalOperations = NUM_CLIENTS * opsPerClient;

        // 1 Thread dedicated exactly to 1 Client
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CLIENTS);
        AtomicInteger failedOperations = new AtomicInteger(0);

        String[] groupNames = {"General", "Help", "Random", "DistributedSystems"};

        System.out.println("Starting Chaos Test: " + NUM_CLIENTS + " clients doing " + opsPerClient + " operations each...");

        for (int i = 0; i < NUM_CLIENTS; i++) {
            final GroupAPI client = clients.get(i);

            executor.submit(() -> {
                Random random = new Random();
                List<Integer> myGroups = new ArrayList<>();

                for (int op = 0; op < opsPerClient; op++) {
                    try {
                        int action = random.nextInt(2);

                        if (action == 0 || myGroups.isEmpty()) {
                            // JOIN A RANDOM GROUP
                            String groupToJoin = groupNames[random.nextInt(groupNames.length)];
                            String username = "User_" + client.getNetworkManager().getPort();

                            JoinReply reply = client.join(groupToJoin, username);

                            // Check for NONE specifically to ensure it was a clean success
                            if (reply != null && reply.getReason() == JoinReply.FailReason.NONE) {
                                if (!myGroups.contains(reply.getGroupId())) {
                                    myGroups.add(reply.getGroupId());
                                }
                            } else if (reply != null && reply.getReason() != JoinReply.FailReason.USERNAME_TAKEN){
                                failedOperations.incrementAndGet();
                            }

                        } else {
                            // LEAVE A RANDOM GROUP
                            int listIndex = random.nextInt(myGroups.size());
                            int groupIdToLeave = myGroups.remove(listIndex);

                            client.leave(groupIdToLeave);
                        }
                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                        e.printStackTrace();
                    }
                }

                try {
                    client.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(finished, "Chaos test timed out!");

        System.out.println("Chaos complete. Failed ops: " + failedOperations.get());

        assertTrue(failedOperations.get() < (totalOperations * 0.1),
                "Too many operations failed! Thread-safety issues likely in GMS.");

        // Verify the server isn't frozen.
        GroupAPI testClient = new GroupAPI("127.0.0.1", GMS_PORT, 7777, false, false, ChatMessage::deserialize);
        JoinReply finalJoin = testClient.join("SurvivorGroup", "Survivor");

        assertNotNull(finalJoin, "Server stopped responding to joins after chaos test!");
        assertSame(JoinReply.FailReason.NONE, finalJoin.getReason(), "Final join failed after chaos!");

        testClient.shutdown();
    }
}