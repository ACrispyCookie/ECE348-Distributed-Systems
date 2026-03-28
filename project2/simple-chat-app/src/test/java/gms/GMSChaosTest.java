package gms;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GMSChaosTest {

    private static final int GMS_PORT = 10100;
    private static Thread serverThread;

    private final int NUM_CLIENTS = 20;
    private final List<GroupAPI> clients = new ArrayList<>();

    private final Map<GroupAPI, List<Integer>> clientActiveGroups = new ConcurrentHashMap<>();

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
            GroupAPI client = new GroupAPI("127.0.0.1", GMS_PORT, 10200 + i, false, ChatMessage::deserialize);
            clients.add(client);
            clientActiveGroups.put(client, new CopyOnWriteArrayList<>());
        }
    }

    @AfterEach
    void tearDown() {
        for (GroupAPI client : clients) {
            client.shutdown();
        }
    }

    @Test
    void testRandomConcurrentChaos() throws InterruptedException, IOException {
        int numOperations = 2000;
        int numThreads = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Random random = new Random();
        AtomicInteger failedOperations = new AtomicInteger(0);

        String[] groupNames = {"General", "Help", "Random", "DistributedSystems"};

        System.out.println("Starting Chaos Test with " + numOperations + " operations...");

        for (int i = 0; i < numOperations; i++) {
            executor.submit(() -> {
                try {
                    GroupAPI client = clients.get(random.nextInt(NUM_CLIENTS));
                    List<Integer> myGroups = clientActiveGroups.get(client);

                    int action = random.nextInt(2);

                    if (action == 0 || myGroups.isEmpty()) {
                        // JOIN A RANDOM GROUP
                        String groupToJoin = groupNames[random.nextInt(groupNames.length)];
                        String username = "User_" + client.getNetworkManager().getPort();

                        JoinReply reply = client.join(groupToJoin, username);
                        if (reply != null && reply.getReason() != JoinReply.FailReason.UNKNOWN) {
                            if (!myGroups.contains(reply.getGroupId())) {
                                myGroups.add(reply.getGroupId());
                            }
                        } else {
                            failedOperations.incrementAndGet();
                        }

                    } else {
                        // LEAVE A RANDOM GROUP
                        int groupIdToLeave = myGroups.get(random.nextInt(myGroups.size()));
                        client.leave(groupIdToLeave);
                        myGroups.remove(Integer.valueOf(groupIdToLeave));

                    }
                } catch (Exception e) {
                    failedOperations.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(finished, "Chaos test timed out! Possible deadlock in GMS or Client APIs.");

        System.out.println("Chaos complete. Failed ops: " + failedOperations.get());

        // Assert that the system survived without excessive failures
        // (A few failures might be acceptable if a user tries to leave a group the moment another thread kicks them, etc.)
        assertTrue(failedOperations.get() < (numOperations * 0.1),
                "Too many operations failed! Thread-safety issues likely in GMS.");

        // --- THE SURVIVOR CHECK ---
        // Let's verify the server isn't frozen.
        GroupAPI testClient = new GroupAPI("127.0.0.1", GMS_PORT, 7777, false, ChatMessage::deserialize);
        JoinReply finalJoin = testClient.join("SurvivorGroup", "Survivor");

        assertNotNull(finalJoin, "Server stopped responding to joins after chaos test!");
        assertSame(JoinReply.FailReason.NONE, finalJoin.getReason(), "Final join failed after chaos!");

        testClient.shutdown();
    }
}