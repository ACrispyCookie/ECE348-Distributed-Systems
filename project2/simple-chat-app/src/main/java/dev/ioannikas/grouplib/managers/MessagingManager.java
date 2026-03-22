package dev.ioannikas.grouplib.managers;

import dev.ioannikas.grouplib.GroupAPI;

import java.util.List;

public class MessagingManager extends GeneralManager {
    
    public MessagingManager(GroupAPI groupAPI) {
        super(groupAPI);
    }

    @Override
    protected void onShutdown() {

    }

    @Override
    protected List<Thread> getThreads() {
        return List.of();
    }
}
