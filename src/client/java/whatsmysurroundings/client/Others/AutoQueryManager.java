package whatsmysurroundings.client.Others;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;

public class AutoQueryManager {
    // 1. 任务列表
    private static final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
    private static int nextTaskId = 0;

    // 2. 注册任务（外部调用）
    public static int addTask(Runnable action, int intervalTicks) {
        ScheduledTask task = new ScheduledTask(nextTaskId++, action, intervalTicks);
        tasks.add(task);
        return task.id;
    }

    // 3. 控制接口
    public static void removeTask(int taskId) {
        tasks.removeIf(task -> task.id == taskId);
    }

    // 4. 核心计时循环（在客户端初始化时注册一次）
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (ScheduledTask task : tasks) {
                if (!task.enabled)
                    continue;
                task.tickCounter++;
                if (task.tickCounter >= task.intervalTicks) {
                    task.action.run();
                    task.tickCounter = 0;
                }
            }
        });
    }

    // 5. 内部任务类
    private static class ScheduledTask {
        final int id;
        final Runnable action;
        final int intervalTicks;
        int tickCounter = 0;
        boolean enabled = true;

        ScheduledTask(int id, Runnable action, int intervalTicks) {
            this.id = id;
            this.action = action;
            this.intervalTicks = intervalTicks;
        }
    }
}