import dev.ioannikas.group.gms.GMS;
import org.junit.jupiter.api.*;

class GMSIntegrationTest {

    private static final int GMS_PORT = 10100;
    private static Thread serverThread;
    private Process client1Process;
    private Process client2Process;

    @BeforeAll
    static void startGMS() throws InterruptedException {
        serverThread = new Thread(() -> GMS.main(new String[]{String.valueOf(GMS_PORT)}));
        serverThread.start();
        Thread.sleep(500); // Give the server time to bind to the port
    }

    @AfterAll
    static void shutdownGMS() {
        serverThread.interrupt();
    }

    @AfterEach
    void tearDown() {
        if (client1Process != null) client1Process.destroy();
        if (client2Process != null) client2Process.destroy();
    }

    @Test
    void testTwoClientsCanJoinSameGroupViaProcesses() throws Exception {
        client1Process = launchClient(6001, "User1", "GlobalChat");
        client2Process = launchClient(6002, "User2", "GlobalChat");

        int exitCode1 = client1Process.waitFor();
        int exitCode2 = client2Process.waitFor();

        Assertions.assertEquals(0, exitCode1, "Client 1 crashed!");
        Assertions.assertEquals(0, exitCode2, "Client 2 crashed!");
    }

    private Process launchClient(int udpPort, String username, String groupName) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + "/bin/java";
        String classpath = System.getProperty("java.class.path");
        String className = "dev.ioannikas.group.TestClientNode";

        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-cp", classpath, className,
                String.valueOf(udpPort), username, groupName
        );

        builder.inheritIO();

        return builder.start();
    }
}