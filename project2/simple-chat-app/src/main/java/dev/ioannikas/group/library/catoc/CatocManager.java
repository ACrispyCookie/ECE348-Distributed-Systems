package dev.ioannikas.group.library.catoc;

import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;
import dev.ioannikas.group.library.serializable.requests.TRMRequest;
import dev.ioannikas.group.library.serializable.requests.URMRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class CatocManager {

    private final GroupAPI api;
    private final Map<Integer, TRMState> groups;

    public CatocManager(GroupAPI api) {
        this.api = api;
        groups = new HashMap<>();
    }

    public synchronized void send(int groupId, String myUsername, SerializableObject data) {
        groups.get(groupId).send(myUsername, data);
    }

    public synchronized List<GroupMessagePair> receive(String myUsername, NormalRequest request) {
        Printer.printDebug("[CatocManager] Received from " + request.getData() + " with " + request.getGroupId());
        return groups.get(request.getGroupId()).receive(myUsername, (URMRequest) request.getData())
                .stream().map(o -> new GroupMessagePair(request.getGroupId(), o)).toList();
    }

    public synchronized void addGroup(int groupId, List<ClientInfo> clients) {
        groups.putIfAbsent(groupId, new TRMState(groupId, clients));
    }

    public synchronized void removeGroup(int groupId) {
        groups.remove(groupId);
    }

    public synchronized List<GroupMessagePair> updateGroup(GMSUpdateReply reply) {
        Printer.printDebug("[CatocManager] Received from " + reply.getClient() + " with " + reply.getClientAction() + " and " + reply.getGroupId());
        return groups.get(reply.getGroupId()).update(reply.getClient(), reply.getClientAction())
                .stream().map(o -> new GroupMessagePair(reply.getGroupId(), o)).toList();
    }

    public void shutdown() {
        TRMState.shutdown();
    }

    private class URMState {
        private int sequenceNumber;
        private final Set<String> receivedMessages; // TODO think about storing something else per client
        private final Map<String, Set<String>> receivedAcks;
        private final Map<String, Object> messageBuffer;

        private final int groupId;
        private final List<ClientInfo> clients;
        private final static Thread retransmitter = new Thread(URMState::retransmitter, "retransmitter");

        public URMState(int groupId, List<ClientInfo> clients) {
            this.sequenceNumber = 0;
            this.receivedMessages = new HashSet<>();
            this.receivedAcks = new HashMap<>();
            this.messageBuffer = new HashMap<>();

            this.groupId = groupId;
            this.clients = clients;
        }

        public void send(String myUser, Object data) {
            sequenceNumber++;
            basicSend(myUser, myUser + sequenceNumber, data);
        }

        public List<Object> receive(String myUser, URMRequest request) {
            String msgId = request.getMessageId();
            String sender = request.getSender();
            List<Object> readyMsgs = new ArrayList<>();

            if (receivedMessages.contains(msgId) && !messageBuffer.containsKey(msgId))
                return readyMsgs;

            if (!receivedMessages.contains(msgId)) {
                receivedMessages.add(msgId);
                messageBuffer.put(msgId, request.getData());

                if (!sender.equals(myUser))
                    basicSend(myUser, msgId, request.getData());
            }

            receivedAcks.computeIfAbsent(msgId, k -> new HashSet<>()).add(sender);
            Set<String> usernames = clients.stream()
                    .map(ClientInfo::getUsername)
                    .collect(Collectors.toSet());

            if (receivedAcks.get(msgId).containsAll(usernames)) {
                readyMsgs.add(messageBuffer.remove(msgId));
                receivedAcks.remove(msgId);
            }

            return readyMsgs;
        }

        public List<Object> handleFailure() {
            List<Object> readyMsgs = new ArrayList<>();

            Set<String> usernames = clients.stream()
                    .map(ClientInfo::getUsername)
                    .collect(Collectors.toSet());

            Iterator<Map.Entry<String, Set<String>>> iterator = receivedAcks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> entry = iterator.next();

                if (entry.getValue().containsAll(usernames)) {
                    readyMsgs.add(messageBuffer.remove(entry.getKey()));
                    iterator.remove();
                }
            }

            return readyMsgs;
        }

        private void basicSend(String sender, String messageId, Object data) {
            for (ClientInfo clientInfo : clients) {
                try {
                    URMRequest urmRequest = new URMRequest(sender, messageId, data);
                    api.getNetworkManager().sendNormal(new NormalRequest(groupId, urmRequest), new InetSocketAddress(clientInfo.getIpAddress(), clientInfo.getPort()));
                } catch (IOException e) {
                    Printer.printErr("[CatocManager] Error sending request to client " + clientInfo.getUsername() + " on group " + groupId);
                    e.printStackTrace();
                }
            }
        }

        private static void retransmitter() {
//        while (!GroupAPI.isShuttingDown()) {
//
//        }
        }

        public static void shutdown() {
            retransmitter.interrupt();
        }

    }

    private class TRMState {
        private final int groupId;
        private final List<ClientInfo> clients;
        private final URMState urm;

        public TRMState(int groupId, List<ClientInfo> clients) {
            this.clients = new ArrayList<>(clients);
            this.groupId = groupId;
            urm = new URMState(groupId, this.clients);
        }

        public void send(String myUser, SerializableObject data) {
            urm.send(myUser, new TRMRequest("1", data));
        }

        public List<SerializableObject> receive(String myUser, URMRequest request) {
            return urm.receive(myUser, request).stream().map(o -> ((TRMRequest) o).getData()).toList();
        }

        public List<SerializableObject> update(ClientInfo user, GMSUpdateReply.Action action) {
            updateGroup(user, action);
            if (action == GMSUpdateReply.Action.LEFT)
                return urm.handleFailure().stream().map(o -> ((TRMRequest) o).getData()).toList();
            return new ArrayList<>();
        }

        private void updateGroup(ClientInfo user, GMSUpdateReply.Action action) {
            if (action == GMSUpdateReply.Action.JOINED) {
                clients.add(user);
            } else if (action == GMSUpdateReply.Action.LEFT) {
                clients.remove(user);
            }
        }

        public static void shutdown() {
            URMState.shutdown();
        }
    }
}
