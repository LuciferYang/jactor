package arksea.jactor;

/**
 *
 * @author arksea
 * @param <T> 传入Actor的状态参数类型
 */
public class ChildInfo<T> {

    private final String name;
    private final boolean daemon;
    private final Class clazz;
    //最大消息队列长度，当设置为0时，将不检测队列长度
    //但这很危险，请谨慎抉择
    private final long maxQueueLen;
    private final T args;
    private Class argsClass;

    public ChildInfo(String name, Class clz, T args) {
        this.name = name;
        this.clazz = clz;
        this.args = args;
        this.maxQueueLen = 10;
        this.daemon = false;
        this.argsClass = args.getClass();
    }

    public ChildInfo(String name, Class clz, T args, long maxQueueLen) {
        this.name = name;
        this.clazz = clz;
        this.args = args;
        this.daemon = false;
        this.maxQueueLen = maxQueueLen;
        this.argsClass = args.getClass();
    }

    public ChildInfo(String name, Class clz, T args, long maxQueueLen, boolean daemon) {
        this.name = name;
        this.clazz = clz;
        this.args = args;
        this.maxQueueLen = maxQueueLen;
        this.daemon = daemon;
        this.argsClass = args.getClass();
    }

    public Class getClazz() {
        return clazz;
    }

    public String getName() {
        return name;
    }

    public T getArgs() {
        return args;
    }

    public long getMaxQueueLen() {
        return maxQueueLen;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public Class getArgsClass() {
        return argsClass;
    }

    public void setArgsClass(Class argsClass) {
        this.argsClass = argsClass;
    }
}
