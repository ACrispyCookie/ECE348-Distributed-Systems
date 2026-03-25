package dev.ioannikas.group;

import dev.ioannikas.group.api.JoinReply;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChaosClientNode {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) System.exit(1);

        int udpPort = Integer.parseInt(args[0]);
        String username = args[1];
        int numOperations = Integer.parseInt(args[2]);

        GroupAPI.init("127.0.0.1", 10100, udpPort); // Connect to GMS

        Random random = new Random();
        String[] groupNames = {"General", "Help", "Random", "DistributedSystems"};
        List<Integer> myGroups = new ArrayList<>();
        int failedOps = 0;

        for (int i = 0; i < numOperations; i++) {
            try {
                int action = random.nextInt(2);

                if (action == 0 || myGroups.isEmpty()) {
                    // JOIN
                    String groupToJoin = groupNames[random.nextInt(groupNames.length)];
                    JoinReply reply = GroupAPI.join(groupToJoin, username);

                    if (reply != null && reply.getReason() != JoinReply.FailReason.UNKNOWN) {
                        if (!myGroups.contains(reply.getGroupId())) {
                            myGroups.add(reply.getGroupId());
                        }
                    } else {
                        failedOps++;
                    }
                } else {
                    // LEAVE
                    int groupIdToLeave = myGroups.get(random.nextInt(myGroups.size()));
                    GroupAPI.leave(groupIdToLeave);
                    myGroups.remove(Integer.valueOf(groupIdToLeave));
                }

                // Tiny sleep to simulate real network gaps
                Thread.sleep(random.nextInt(10));

            } catch (Exception e) {
                failedOps++;
            }
        }

        GroupAPI.shutdown();

        // If more than 10% of operations failed, exit with an error code!
        if (failedOps > (numOperations * 0.1)) {
            System.exit(2); // 2 means "Too many failures"
        }

        System.exit(0); // 0 means "Success"
    }
}