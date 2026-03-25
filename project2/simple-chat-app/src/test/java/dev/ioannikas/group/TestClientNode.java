package dev.ioannikas.group;

public class TestClientNode {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) return;

        int udpPort = Integer.parseInt(args[0]);
        String username = args[1];
        String groupName = args[2];

        GroupAPI.init("10.0.0.8", 10100, udpPort);

        System.out.println("Node " + username + " started.");

        int groupID = GroupAPI.join(groupName, username).getGroupId();
        System.out.println("Node " + username + " joined " + groupName);

        Thread.sleep(5000);

        GroupAPI.leave(groupID);
        System.out.println("Node " + username + " left.");

        System.exit(0);
    }
}