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

    private final Thread retransmitter;
    private final int RESEND_TIMEOUT = 5;

    public CatocManager(GroupAPI api) {
        this.api = api;
        groups = new HashMap<>();

        retransmitter = new Thread(this::retransmitter, "retransmitter");
        retransmitter.start();
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
        groups.putIfAbsent(groupId, new TRMState(groupId, clients, this));
    }

    public synchronized void removeGroup(int groupId) {
        groups.remove(groupId);
    }

    public synchronized List<GroupMessagePair> updateGroup(GMSUpdateReply reply) {
        Printer.printDebug("[CatocManager] Received from " + reply.getClient() + " with " + reply.getClientAction() + " and " + reply.getGroupId());
        return groups.get(reply.getGroupId()).update(reply.getClient(), reply.getClientAction())
                .stream().map(o -> new GroupMessagePair(reply.getGroupId(), o)).toList();
    }

    public synchronized int resend() throws InterruptedException {
        int count = 0;
        for (Map.Entry<Integer, TRMState> group : groups.entrySet()) {
            count += group.getValue().resend(api.getUsername(group.getKey()));
        }

        return count;
    }

    public synchronized void shutdown() {
        retransmitter.interrupt();
        groups.clear();
    }

    private void retransmitter() {
        while (!api.isShuttingDown()) {
            try {
                synchronized (this) {
                    int count = resend();
                    if (count == 0) {
                        Printer.printDebug("[CatocManager] Buffer empty sleeping");
                        this.wait();
                    } else {
                        Printer.printDebug("[CatocManager] Buffer not empty sleeping for less");
                    }
                }
                Thread.sleep(RESEND_TIMEOUT * 1000L);
            } catch (InterruptedException e) {
                if (!api.isShuttingDown()) Printer.printDebug("[CatocManager] Error running retransmitter!");
            }
        }
    }

    private class URMState {
        private int sequenceNumber;
        private final Set<String> receivedMessages; // TODO think about storing something else per client
        private final Map<String, Set<String>> receivedAcks;
        private final Map<String, Object> messageBuffer;

        private final int groupId;
        private final List<ClientInfo> clients;
        private final Object retransmissionCondition;

        public URMState(int groupId, List<ClientInfo> clients, Object condition) {
            this.sequenceNumber = 0;
            this.receivedMessages = new HashSet<>();
            this.receivedAcks = new HashMap<>();
            this.messageBuffer = new HashMap<>();

            this.groupId = groupId;
            this.clients = clients;
            this.retransmissionCondition = condition;
        }

        public void send(String myUser, Object data) {
            sequenceNumber++;
            // TODO Figure out if we rely on UDP loopback or else add it to the messageBuffer
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
                retransmissionCondition.notify();

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

        public int resend(String myUser) {
            int count = messageBuffer.size();
            Printer.printDebug("[CatocManager] Resending msges....");
            for (Map.Entry<String, Object> entry : messageBuffer.entrySet()) {
                String msgId = entry.getKey();
                List<ClientInfo> notAcked = clients.stream().filter(c -> !receivedAcks.get(msgId).contains(c.getUsername())).toList();
                basicSend(myUser, msgId, entry.getValue(), notAcked);
            }
            Printer.printDebug("[CatocManager] Resent " + count + " messages....");
            return count;
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
            basicSend(sender, messageId, data, clients);
        }

        private void basicSend(String sender, String messageId, Object data, List<ClientInfo> clients) {
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
    }

    private class TRMState {
        private final int groupId;
        private final List<ClientInfo> clients;
        private final URMState urm;

        public TRMState(int groupId, List<ClientInfo> clients, Object condition) {
            this.clients = new ArrayList<>(clients);
            this.groupId = groupId;
            urm = new URMState(groupId, this.clients, condition);
        }

        public void send(String myUser, SerializableObject data) {
            urm.send(myUser, new TRMRequest("1", data));
        }

        public List<SerializableObject> receive(String myUser, URMRequest request) {
            return urm.receive(myUser, request).stream().map(o -> ((TRMRequest) o).getData()).toList();
        }

        public int resend(String myUser) {
            return urm.resend(myUser);
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
    }
}
