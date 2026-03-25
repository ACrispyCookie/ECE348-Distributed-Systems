import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GMSChaosTest {

    private static final int GMS_PORT = 10100;
    private static Thread serverThread;
    private final int NUM_CLIENTS = 20;
    private final int OPS_PER_CLIENT = 100; // 20 clients * 100 ops = 2000 total operations

    @BeforeAll
    static void startGMS() throws InterruptedException {
        serverThread = new Thread(() -> GMS.main(new String[]{String.valueOf(GMS_PORT)}));
        serverThread.start();
        Thread.sleep(500); // Give server time to bind
    }

    @AfterAll
    static void shutdownGMS() throws InterruptedException {
        serverThread.interrupt();
        serverThread.join();
    }

    @Test
    void testProcessBasedConcurrentChaos() throws Exception {
        System.out.println("Starting Process Chaos Test with " + (NUM_CLIENTS * OPS_PER_CLIENT) + " operations...");

        List<Process> activeProcesses = new ArrayList<>();

        // 1. Launch 20 independent JVMs simultaneously
        for (int i = 0; i < NUM_CLIENTS; i++) {
            int udpPort = 6000 + i;
            String username = "ChaosUser_" + i;
            Process p = launchChaosNode(udpPort, username, OPS_PER_CLIENT);
            activeProcesses.add(p);
        }

        // 2. Wait for the dust to settle
        int failedProcesses = 0;
        for (Process p : activeProcesses) {
            // Wait up to 30 seconds for each process to finish its 100 operations
            boolean finished = p.waitFor(30, TimeUnit.SECONDS);

            assertTrue(finished, "A chaos process deadlocked and timed out!");

            if (p.exitValue() != 0) {
                failedProcesses++;
            }
        }

        System.out.println("Chaos complete. Failed client nodes: " + failedProcesses);

        // 3. Assert the system survived
        assertTrue(failedProcesses < (NUM_CLIENTS * 0.2),
                "Too many client nodes crashed! Thread-safety issues likely in GMS.");

        // 4. --- THE SURVIVOR CHECK ---
        // Verify the server isn't frozen by launching one final clean process
        Process survivor = launchChaosNode(7777, "Survivor", 1); // Just 1 operation (Join)
        assertTrue(survivor.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, survivor.exitValue(), "Server stopped responding to joins after chaos test!");
    }

    // --- UTILITY TO LAUNCH THE ATTACKER JVM ---
    private Process launchChaosNode(int udpPort, String username, int numOperations) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = "dev.ioannikas.group.ChaosClientNode";

        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-cp", classpath, className,
                String.valueOf(udpPort), username, String.valueOf(numOperations)
        );

        // Optional: If you want to see the errors from the clients in your main console, uncomment this:
        // builder.inheritIO();

        return builder.start();
    }
}