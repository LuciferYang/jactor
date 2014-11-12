package arksea.jactor;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 线程监控者，当线程ChildTask异常退出时负责将其重启
 * $Author: arksea $
 */
public class Supervisor implements Thread.UncaughtExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(Supervisor.class.getName());
    private final RestartStrategies restartStrategies;
    private final Map<String, ChildTask> childMap = new HashMap();
    private final List<ChildInfo> childList = new LinkedList();
    private final long JOIN_CHILD_TIMEOUT = 5000;
    private final String regname;
    private long restartCount = 0;
    private long lastRestartTime = 0;

    protected Supervisor(String name, RestartStrategies restart, ChildInfo[] childs) {
        logger.trace("Supervisor '" + name + "' starting childs");
        this.regname = name;
        this.restartStrategies = restart;
        for (ChildInfo c : childs) {
            this.childList.add(c);
        }
        startAll();
        logger.trace("Supervisor '" + name + "' started all childs");
    }

    protected void stopAll() {
        logger.trace("Supervisor '" + regname + "' stopping childs");
        for (ChildInfo i : childList) {
            ChildTask t = childMap.get(i.getName());
            t.normalStopRequest();
            TaskContext.instance().remove(i.getName());
        }
        logger.trace("Supervisor '" + regname + "' stopped all childs");
    }

    private void startAll() {
        for (ChildInfo c : childList) {
            createChild(c);
        }
        for (ChildInfo c : childList) {
            Thread child = childMap.get(c.getName());
            child.start();
        }
    }

    private Thread createChild(ChildInfo info) {
        try {
            ChildTask child;
            if (Actor.class.isAssignableFrom(info.getClazz())) {
                Constructor con = info.getClazz().getConstructor(String.class, long.class, info.getArgsClass());
                child = (ChildTask) con.newInstance(info.getName(), info.getMaxQueueLen(), info.getArgs());
                child.setDaemon(info.isDaemon());
                TaskContext.instance().put(info.getName(), (Actor) child);
            } else {
                Constructor con = info.getClazz().getConstructor(String.class, info.getArgsClass());
                child = (ChildTask) con.newInstance(info.getName(), info.getArgs());
                child.setDaemon(info.isDaemon());
            }
            child.setUncaughtExceptionHandler(this);
            childMap.put(info.getName(), child);
            return child;
        } catch (Throwable ex) {
            throw new RuntimeException("Supervisor '" + regname + "' create ChildTask '" + info.getName() + "' failed", ex);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable ex) {
        String name = t.getName();
        logger.error("ChildTask '" + name + "' exited because Exception", ex);
        switch (restartStrategies) {
            case ONE_FOR_ONE:
                stopOneForOne(name);
                break;
            case ONE_FOR_ALL:
                stopOneForAll(name);
                break;
            case REST_FOR_ONE:
                stopRestForOne(name);
                break;
        }
        ++restartCount;
        long now = System.currentTimeMillis();
        long time = now - lastRestartTime;
        lastRestartTime = now;
        if (time < 10000) {
            if (restartCount > 10) {
                logger.error("ChileTask异常重启次数短时间内超过10次，将被停止："+regname);
                TaskContext.instance().stop(regname);
            }
        } else {
            restartCount = 1;
        }
    }

    private void stopOneForOne(String name) {
        for (ChildInfo i : childList) {
            if (i.getName().equals(name)) {
                TaskContext.instance().remove(name);
                Thread child = createChild(i);
                child.start();
                break;
            }
        }
    }

    private void stopOneForAll(String name) {
        for (ChildInfo i : childList) {
            TaskContext.instance().remove(name);
            ChildTask child = childMap.get(i.getName());
            child.normalStopRequest();
            try {
                child.join(JOIN_CHILD_TIMEOUT);
            } catch (InterruptedException ex) {
                logger.warn("Supervisor '" + regname + "' join ChildTask '" + name + "' interruped", ex);
            }
            if (child.isAlive()) {
                child.stop();//超时则强制退出线程
                logger.warn("Supervisor '" + regname + "' join ChildTask '" + name + "' timeout");
            }
        }
        for (ChildInfo i : childList) {
            Thread child = createChild(i);
            child.start();
        }
    }

    private void stopRestForOne(String name) {
        boolean rest = false;
        for (ChildInfo i : childList) {
            if (rest || i.getName().equals(name)) {
                if (rest) {
                    TaskContext.instance().remove(name);
                    ChildTask child = childMap.get(i.getName());
                    child.normalStopRequest();
                    try {
                        child.join(JOIN_CHILD_TIMEOUT);
                    } catch (InterruptedException ex) {
                        logger.warn("Supervisor '" + regname + "' join ChildTask '" + name + "' interruped", ex);
                    }
                    if (child.isAlive()) {
                        child.stop();//超时则强制退出线程
                        logger.warn("Supervisor '" + regname + "' join ChildTask '" + name + "' timeout");
                    }
                }
                rest = true;
            }
        }
        rest = false;
        for (ChildInfo i : childList) {
            if (rest || i.getName().equals(name)) {
                Thread child = createChild(i);
                child.start();
                rest = true;
            }
        }
    }
}
