import dev.ioannikas.gms.managers.GroupManager;
import dev.ioannikas.grouplib.network.serializable.gms.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupManagerTest {

    private GroupManager groupManager;

    @BeforeEach
    void setUp() {
        groupManager = new GroupManager();
    }

    @Test
    void testFindOrCreateGroup() {
        int groupId1 = groupManager.findOrCreateGroup("DistributedSystems");
        int groupId2 = groupManager.findOrCreateGroup("DistributedSystems"); // Should not create a new one
        int groupId3 = groupManager.findOrCreateGroup("Networks");

        assertTrue(groupId1 > 0, "Group ID should be strictly positive");
        assertEquals(groupId1, groupId2, "Should return the same ID for the same group name");
        assertNotEquals(groupId1, groupId3, "Different groups should have different IDs");
    }

    @Test
    void testAddToGroupSuccess() {
        int groupId = groupManager.findOrCreateGroup("TestGroup");
        ClientInfo client = new ClientInfo("Alice", "127.0.0.1", 5000);

        GroupManager.ReturnCode result = groupManager.addToGroup(groupId, client);

        assertEquals(GroupManager.ReturnCode.SUCCESS, result);
        assertEquals(1, groupManager.getClients(groupId).size());
    }

    @Test
    void testPreventDuplicateUsernames() {
        int groupId = groupManager.findOrCreateGroup("TestGroup");
        ClientInfo client1 = new ClientInfo("Alice", "127.0.0.1", 5000);
        ClientInfo client2 = new ClientInfo("Alice", "192.168.1.5", 5001); // Same username!

        groupManager.addToGroup(groupId, client1);
        GroupManager.ReturnCode duplicateResult = groupManager.addToGroup(groupId, client2);

        assertEquals(GroupManager.ReturnCode.CLIENT_ALREADY_EXISTS, duplicateResult);
        assertEquals(1, groupManager.getClients(groupId).size(), "Group size should not increase");
    }

    @Test
    void testLeaveGroup() {
        int groupId = groupManager.findOrCreateGroup("TestGroup");
        groupManager.addToGroup(groupId, new ClientInfo("Bob", "127.0.0.1", 5000));

        GroupManager.ReturnCode leaveResult = groupManager.removeFromGroup(groupId, "Bob");
        GroupManager.ReturnCode leaveAgain = groupManager.removeFromGroup(groupId, "Bob");

        assertEquals(GroupManager.ReturnCode.SUCCESS, leaveResult);
        assertEquals(GroupManager.ReturnCode.CLIENT_NOT_FOUND, leaveAgain, "Should fail if user isn't there");
        assertTrue(groupManager.getClients(groupId).isEmpty());
    }
}