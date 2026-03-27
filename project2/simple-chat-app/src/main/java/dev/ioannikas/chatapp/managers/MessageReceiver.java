package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;
import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.ViewUpdateMessage;

import java.util.concurrent.ExecutionException;

public class MessageReceiver extends Thread {

    private boolean shutdown = false;

    public MessageReceiver() {
        setName("sca-message-receiver");
    }

    @Override
    public void run() {
        while (!ChatApp.isShuttingDown() && !shutdown) {
            try {
                GroupMessagePair message = GroupAPI.receive(ChatApp.getChatManager().getGroups());

                if (message.getMessage() instanceof ViewUpdateMessage update) {
                    handleUpdate(update);
                } else {
                    handleMessage(message.getGroupId(), (ChatMessage) message.getMessage());
                }
            } catch (InterruptedException ignored) {

            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleUpdate(ViewUpdateMessage update) {
        if (update.getAction() == ViewUpdateMessage.UpdateAction.JOINED) {
            ChatApp.getChatManager().memberJoined(update.getGroupId(), update.getClient());
        } else {
            ChatApp.getChatManager().memberLeft(update.getGroupId(), update.getClient());
        }
    }

    private void handleMessage(int groupId, ChatMessage message) {
        ChatApp.getChatManager().printChatMessage(groupId, message);
    }

    public void shutdown() {
        shutdown = true;
        interrupt();
    }
}
