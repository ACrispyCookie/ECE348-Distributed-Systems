package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;
import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.api.ViewUpdateMessage;

public class MessageReceiver extends Thread {

    private final int groupId;
    private boolean shutdown = false;

    public MessageReceiver(int groupId) {
        this.groupId = groupId;
        setName("sca-message-receiver");
    }

    @Override
    public void run() {
        try {
            while (!ChatApp.isShuttingDown() && !shutdown) {
                Object message = ChatApp.getGroupAPI().receive(groupId, true);

                if (message instanceof ViewUpdateMessage update) {
                    handleUpdate(update);
                } else {
                    handleMessage((ChatMessage) message);
                }
            }
        } catch (InterruptedException ignored) {

        }
    }

    private void handleUpdate(ViewUpdateMessage update) {
        if (update.getAction() == ViewUpdateMessage.UpdateAction.JOINED) {
            ChatApp.getChatManager().memberJoined(update.getGroupId(), update.getClient());
        } else {
            ChatApp.getChatManager().memberLeft(update.getGroupId(), update.getClient());
        }
    }

    private void handleMessage(ChatMessage message) {
        ChatApp.getChatManager().printChatMessage(groupId, message);
    }

    public void shutdown() {
        shutdown = true;
        interrupt();
    }

    public int getGroupId() {
        return groupId;
    }
}
