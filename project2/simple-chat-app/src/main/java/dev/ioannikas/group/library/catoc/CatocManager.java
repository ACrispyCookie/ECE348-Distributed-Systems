package dev.ioannikas.group.library.catoc;

import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CatocManager {

    private final GroupAPI api;
    private final Map<Integer, TRMState> groups;
    private final Map<Integer, TRMState> disabled;
    private final boolean trmEnabled;
    private final boolean crmEnabled;

    private final Thread retransmitter;
    private final long RESEND_TIMEOUT = 100;

    public CatocManager(GroupAPI api, boolean trmEnabled, boolean crmEnabled) {
        this.api = api;
        groups = new HashMap<>();
        disabled = new HashMap<>();
        this.trmEnabled = trmEnabled;
        this.crmEnabled = crmEnabled;
        retransmitter = new Thread(this::retransmitter, "retransmitter");
        retransmitter.start();
    }

    public synchronized CompletableFuture<Integer> send(int groupId, String myUsername, SerializableObject data) {
        return groups.get(groupId).send(myUsername, data);
    }

    public synchronized List<GroupMessagePair> receive(String myUsername, NormalRequest request) {
        return groups.get(request.getGroupId()).receive(myUsername, request)
                .stream().map(o -> new GroupMessagePair(request.getGroupId(), o)).toList();
    }

    public synchronized void addGroup(int groupId, String username, List<ClientInfo> clients) {
        if (disabled.containsKey(groupId) && disabled.get(groupId).getInitialUsername().equals(username))
            groups.put(groupId, disabled.remove(groupId));
        disabled.remove(groupId);
        groups.putIfAbsent(groupId, new TRMState(groupId, username, clients, this, trmEnabled, crmEnabled));
    }

    public synchronized void removeGroup(int groupId) {
        disabled.put(groupId, groups.remove(groupId));
    }

    public synchronized List<GroupMessagePair> updateGroup(GMSUpdateReply reply) {
        Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] Received update from " + reply.getClient() + " with " + reply.getClientAction() + " and " + reply.getGroupId());
        return groups.get(reply.getGroupId()).update(api.getUsername(reply.getGroupId()), reply.getClient(), reply.getClientAction())
                .stream().map(o -> new GroupMessagePair(reply.getGroupId(), o)).toList();
    }

    public synchronized int resend() throws InterruptedException {
        int count = 0;
        for (Map.Entry<Integer, TRMState> group : groups.entrySet()) {
            count += group.getValue().resend(api.getUsername(group.getKey()));
        }

        return count;
    }

    public void shutdown() throws InterruptedException {
        retransmitter.interrupt();
        retransmitter.join();
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
                        if (api.isShuttingDown())
                            return;
                    } else {
                        Printer.printDebug("[CatocManager] Buffer not empty sleeping for less");
                    }
                }
                Thread.sleep(RESEND_TIMEOUT);
            } catch (InterruptedException e) {
                if (!api.isShuttingDown()) Printer.printDebug("[CatocManager] Error running retransmitter!");
            }
        }
    }

    private class BMState {
        private Map<String, Integer> sequenceNumbers;
        private Map<String, Map<Integer, URMRequest>> pending;

        private int groupId;
        private final List<ClientInfo> clients;
        private final Object retrConditional;

        public BMState(int groupId, List<ClientInfo> clients, Object retrConditional) {
            this.sequenceNumbers = new HashMap<>();
            this.pending = new HashMap<>();
            this.groupId = groupId;
            this.clients = clients;
            this.retrConditional = retrConditional;
        }

        public void send(String myUser, URMRequest msg) {
            for (ClientInfo info : clients) {
                int seqNum = sequenceNumbers.merge(info.getUsername(), 1, (current, start) -> current + 1);
                pending.computeIfAbsent(info.getUsername(), k -> new HashMap<>()).put(seqNum, msg);

                BMRequest bmReq = new BMRequest(myUser, seqNum, msg);
                basicSend(bmReq, info);
            }

            synchronized (retrConditional) { retrConditional.notifyAll(); }
        }

        public URMRequest receive(String myUser, NormalRequest normal) {
            Object payload = normal.getData();
            if (payload instanceof BMRequest req) {
//                Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") Received BM Request from " + req.getSender() + " with ID " + req.getSequenceNumber());
                Optional<ClientInfo> sender = clients.stream()
                        .filter(c -> c.getUsername().equals(req.getSender()))
                        .findFirst();

                if (sender.isPresent()) {
                    basicSend(new BMAckRequest(myUser, req.getSequenceNumber()), sender.get());
                    return req.getData();
                }
            } else if (payload instanceof BMAckRequest ack) {
//                Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") Received BM ACKRequest from " + ack.getSender() + " with ID " + ack.getSequenceNumber());
                Map<Integer, URMRequest> userPending = pending.get(ack.getSender());
                if (userPending != null) {
                    userPending.remove(ack.getSequenceNumber());
                }
                return null;
            }
            return null;
        }

        public int resend(String myUser) {
            int count = 0;
            for (String user : pending.keySet()) {
                Optional<ClientInfo> sender = clients.stream()
                        .filter(c -> c.getUsername().equals(user))
                        .findFirst();

                if (sender.isPresent()) {
                    Set<Map.Entry<Integer, URMRequest>> entrySet = pending.get(user).entrySet();
                    count += entrySet.size();
                    for (Map.Entry<Integer, URMRequest> toSend : entrySet) {
                        BMRequest bmReq = new BMRequest(myUser, toSend.getKey(), toSend.getValue());
                        basicSend(bmReq, sender.get());
                    }
                }
            }
            return count;
        }

        public void handleFailure(String username) {
            Map<Integer, URMRequest> seqs = pending.remove(username);
            if (seqs != null) {
                seqs.clear();
            }

            sequenceNumbers.remove(username);
        }

        private void basicSend(Object payload, ClientInfo target) {
            try {
                NormalRequest request = new NormalRequest(groupId, payload);
                api.getNetworkManager().sendNormal(request, new InetSocketAddress(target.getIpAddress(), target.getPort()));
            } catch (IOException e) {
                Printer.printErr("[BMState] Error sending UDP packet...");
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
        private final BMState bm;

        public URMState(int groupId, List<ClientInfo> clients, Object condition) {
            this.sequenceNumber = 0;
            this.receivedMessages = new HashSet<>();
            this.receivedAcks = new HashMap<>();
            this.messageBuffer = new HashMap<>();

            this.groupId = groupId;
            this.clients = clients;
            this.bm = new BMState(groupId, clients, condition);
        }

        public void send(String myUser, Object data) {
            sequenceNumber++;
            bm.send(myUser, new URMRequest(myUser, myUser + sequenceNumber, data));
        }

        public List<Object> receive(String myUser, NormalRequest normal) {
            URMRequest request = bm.receive(myUser, normal);
            if (request == null)
                return new ArrayList<>();

            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") Received URM Request from " + request.getSender() + " with ID " + request.getMessageId());
            String msgId = request.getMessageId();
            String sender = request.getSender();
            List<Object> readyMsgs = new ArrayList<>();

            if (receivedMessages.contains(msgId) && !messageBuffer.containsKey(msgId))
                return readyMsgs;

            if (!receivedMessages.contains(msgId)) {
                receivedMessages.add(msgId);
                messageBuffer.put(msgId, request.getData());

                if (!sender.equals(myUser))
                    bm.send(myUser, new URMRequest(myUser, msgId, request.getData()));
            }

            receivedAcks.computeIfAbsent(msgId, k -> new HashSet<>()).add(sender);
            Set<String> usernames = clients.stream()
                    .map(ClientInfo::getUsername)
                    .collect(Collectors.toSet());

            if (receivedAcks.get(msgId).containsAll(usernames) && messageBuffer.containsKey(msgId)) {
                readyMsgs.add(messageBuffer.remove(msgId));
            }

            return readyMsgs;
        }

        public int resend(String myUser) {
            return bm.resend(myUser);
        }

        public List<Object> handleFailure(String username) {
            bm.handleFailure(username);
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
    }

    private class TRMState {
        private int sequenceNumber;
        private int delivered;
        private int deliveryNumber;
        private final Map<String, SerializableObject> messages;
        private final Map<Integer, String> deliveryOrder;
        private String sendRequestId;
        private CompletableFuture<Integer> sendRequest;

        private final int groupId;
        private String initialUsername;
        private final List<ClientInfo> clients;
        private final URMState urm;
        private final boolean trmEnabled;
        private final boolean crmEnabled;

        public TRMState(int groupId, String initialUsername, List<ClientInfo> clients, Object condition, boolean trmEnabled, boolean crmEnabled) {
            this.sequenceNumber = 0;
            this.deliveryNumber = 0;
            this.delivered = 0;
            this.messages = new HashMap<>();
            this.deliveryOrder = new HashMap<>();
            this.sendRequest = null;
            this.sendRequestId = null;

            this.trmEnabled = trmEnabled;
            this.crmEnabled = crmEnabled;
            this.clients = new ArrayList<>(clients);
            this.groupId = groupId;
            this.initialUsername = initialUsername;
            urm = new URMState(groupId, this.clients, condition);
        }

        public CompletableFuture<Integer> send(String myUser, SerializableObject data) {
            if (trmEnabled) {
                if (crmEnabled) {
                    sendRequestId = myUser + (sequenceNumber + 1);
                    sendRequest = new CompletableFuture<>();
                }
                sequenceNumber++;
                urm.send(myUser, new TRMRequest(myUser + sequenceNumber, data));

                return sendRequest;
            } else {
                urm.send(myUser, data);
            }
            return sendRequest;
        }

        public List<SerializableObject> receive(String myUser, NormalRequest request) {
            if (trmEnabled) {
                List<Object> received = urm.receive(myUser, request);

                List<SerializableObject> ready = new ArrayList<>();
                for (Object trmRequest : received) {
                    String msgId;

                    if (trmRequest instanceof TRMRequest msg) {
                        ready.addAll(receiveTRMMessage(myUser, msg));
                        msgId = msg.getMessageId();
                    } else {
                        ready.addAll(receiveTRMNumber((TRMNumberRequest) trmRequest));
                        msgId = ((TRMNumberRequest) trmRequest).getMessageId();
                    }

                    if (sendRequestId != null && sendRequestId.equals(msgId)) {
                        sendRequest.complete(0);
                        sendRequestId = null;
                        sendRequest = null;
                    }
                }

                return ready;
            } else {
                return urm.receive(myUser, request).stream().map(o -> (SerializableObject) o).toList();
            }
        }

        public int resend(String myUser) {
            return urm.resend(myUser);
        }

        public List<SerializableObject> update(String myUser, ClientInfo user, GMSUpdateReply.Action action) {
            boolean wasICoordinator = amICoordinator(myUser);
            updateGroup(user, action);
            boolean shouldTakeover = !wasICoordinator && amICoordinator(myUser);

            List<SerializableObject> ready = new ArrayList<>();
            if (trmEnabled) {
                if (action == GMSUpdateReply.Action.JOINED && amICoordinator(myUser) && !user.getUsername().equals(myUser)) {
                    Printer.printDebug("[CatocManager] Sending Welcome Sequence #" + deliveryNumber + " to new member.");
                    urm.send(myUser, new TRMNumberRequest("", deliveryNumber));
                } else if (action == GMSUpdateReply.Action.LEFT) {
                    List<Object> received = urm.handleFailure(user.getUsername());
                    for (Object trmRequest : received) {
                        ready.addAll(handleFault(myUser, trmRequest));
                    }

                    if (shouldTakeover)
                        takeoverSequencing(myUser);
                }
            } else {
                ready.addAll(urm.handleFailure(user.getUsername()).stream().map(o -> (SerializableObject) o).toList());
            }
            return ready;
        }

        private void updateGroup(ClientInfo user, GMSUpdateReply.Action action) {
            if (action == GMSUpdateReply.Action.JOINED) {
                clients.add(user);
            } else if (action == GMSUpdateReply.Action.LEFT) {
                clients.remove(user);
            }
        }

        private List<SerializableObject> handleFault(String myUser, Object request) {
            List<SerializableObject> ready = new ArrayList<>();

            if (request instanceof TRMRequest msg) {
                ready.addAll(receiveTRMMessage(myUser, msg));
            } else if (request instanceof TRMNumberRequest msg) {
                ready.addAll(receiveTRMNumber(msg));
            }

            return ready;
        }

        private List<SerializableObject> receiveTRMMessage(String myUser, TRMRequest request) {
            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") Received TRM Request with ID " + request.getMessageId() + " with data " + request.getData());
            messages.put(request.getMessageId(), request.getData());
            List<SerializableObject> objects = checkBuffer();
            if (amICoordinator(myUser)) {
                deliveryNumber++;
                Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") Sending TRM Number with ID " + request.getMessageId() + " with number " + deliveryNumber);
                urm.send(myUser, new TRMNumberRequest(request.getMessageId(), deliveryNumber));
            }
            return objects;
        }

        private List<SerializableObject> receiveTRMNumber(TRMNumberRequest request) {
            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") Received TRM Number with ID " + request.getMessageId() + " with number " + request.getDeliveryNumber());

            /* Fix for newly joined member not having up-to-date delivery numbers*/
            if (this.delivered == 0 && request.getMessageId().isEmpty()) {
                this.delivered = request.getDeliveryNumber();
                this.deliveryNumber = request.getDeliveryNumber();
                return new ArrayList<>();
            } else if (request.getMessageId().isEmpty()) {
                return new ArrayList<>();
            }

            deliveryOrder.put(request.getDeliveryNumber(), request.getMessageId());
            deliveryNumber = Math.max(deliveryNumber, request.getDeliveryNumber());
            return checkBuffer();
        }

        private List<SerializableObject> checkBuffer() {
            List<SerializableObject> ready = new ArrayList<>();

            String id = deliveryOrder.get(delivered + 1);
            while (id != null && messages.get(id) != null) {
                ready.add(messages.remove(id));
                Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] (Group " + groupId + ") TRM Request with ID " + id + " is being delivered");
                deliveryOrder.remove(delivered + 1);
                delivered++;
                id = deliveryOrder.get(delivered + 1);
            }

            return ready;
        }

        private void takeoverSequencing(String myUser) {
            for (String msgId : messages.keySet()) {
                if (!deliveryOrder.containsValue(msgId)) {
                    deliveryNumber++;
                    Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] Coordinator Takeover! Sequencing stranded message " + msgId + " as #" + deliveryNumber);
                    urm.send(myUser, new TRMNumberRequest(msgId, deliveryNumber));
                }
            }
        }

        private boolean amICoordinator(String myUser) {
            return clients.get(0).getUsername().equals(myUser);
        }

        public String getInitialUsername() {
            return initialUsername;
        }
    }
}
