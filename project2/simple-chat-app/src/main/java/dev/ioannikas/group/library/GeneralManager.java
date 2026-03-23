package dev.ioannikas.group.library;

import dev.ioannikas.group.GroupAPI;

import java.util.List;

public abstract class GeneralManager {

    private boolean shuttingDown;
    private final GroupAPI groupAPI;
    private final List<Thread> threads;
    protected abstract void onShutdown();
    protected abstract List<Thread> getThreads();

    public GeneralManager(GroupAPI groupAPI) {
        this.shuttingDown = false;
        this.groupAPI = groupAPI;
        this.threads = getThreads();
    }

    protected void start() {
        threads.forEach(Thread::start);
    }

    public void shutdown() {
        shuttingDown = true;
        onShutdown();
        threads.forEach(Thread::interrupt);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public GroupAPI getAPI() {
        return groupAPI;
    }
}
