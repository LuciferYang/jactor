jactor
======

    Java轻量级Actor库，仿照Erlang的Actor范式实现。

###使用实例
假设需要将下面这么一个简单类的同步接口改造成基于消息的Actor
```
public class EventHandler {
    private String event;
    private int count = 0;
    public synchronized void sendEvent(String e) {
        this.event = e;
        this.count ++;
    }
    public synchronized int getEventCount() {
        return count;
    }
}
```

实现一个Actor需要继承自arksea.jactor.Actor，并实现4个方法：
```
    protected void handle_info(Message msg, String from) throws Throwable {}
    protected Message handle_call(Message msg, String from) throws Throwable {}
    protected void init() throws Throwable {}
    protected void terminate(Throwable ex) {}
```

除了上面4个方法外，下面实现的Actor暴露了两个API（Erlang的一种编写范式，将异步通信以API方式暴露）
```
public class EventHandler extends arksea.jactor.Actor<EventHandler.State> {
    private static final TaskContext context = TaskContext.instance();
    //API
    public static void sendEvent(String type) {
        context.send("event_handler", new Message("event",type));
    }
    public static Integer getEventCount() {
        Message ret = context.call("event_handler", new Message("getEventCount",null), 5000);
        return (Integer)ret.value;
    }
    //Actor的状态，Actor因为错误被重启后这个状态会被继承
    public static class State {
        String type;
        Integer changeCount;
    }

    public EventHandler(String name, long maxQueueLen, State state) {
        super(name, maxQueueLen, state);
    }

    private final static Logger logger = LoggerFactory.getLogger(Actor1.class.getName());

    public static ChildInfo createChildInfo(String name, String type, int maxQueueSize) {
        State s = new State();
        s.type = type;
        s.changeCount = 0;
        return new ChildInfo(name, EventHandler.class, s, maxQueueSize);
    }
    @Override
    protected void handle_info(Message msg, String from) throws Throwable {
        switch (msg.name) {
            case "event" :
                String newType = (String)msg.value;
                logger.info(msg.toString()+",oldEvent="+state.type+",newEvent="+newType);
                state.type = newType;
                state.changeCount ++;
                break;
        }
    }
    @Override
    protected Message handle_call(Message msg, String from) throws Throwable {
        switch (msg.name) {
            case "getEventCount" :
                return new Message("ok", state.changeCount);
            default:
                return new ThrowMessage("unknow request");
        }
    }
    @Override
    protected void init() throws Throwable {
        logger.debug("init()");
    }
    @Override
    protected void terminate(Throwable ex) {
        logger.debug("terminate",ex);
    }
}

```
启动这个Actor
```
    public static void main(String[] args) {
        DOMConfigurator.configure("log4j.xml");
        ChildInfo[] childs = new ChildInfo[1];
        childs[0] = EventHandler.createChildInfo("event_handler", "none", 10);
        TaskContext context = TaskContext.instance();
        context.start("event_handler_sup", RestartStrategies.ONE_FOR_ONE, childs);
        EventHandler.sendEvent("event1");
        EventHandler.sendEvent("event2");
        EventHandler.sendEvent("event3");
        Integer count = EventHandler.getEventCount();
        System.out.println("event count=" + count);
    }
```
运行后的结果
```
2014-11-18 19:14:56 TRACE arksea.jactor.Supervisor.<init>(Supervisor.java:33) Supervisor 'event_handler_sup' starting childs
2014-11-18 19:14:56 TRACE arksea.jactor.Supervisor.<init>(Supervisor.java:38) Supervisor 'event_handler_sup' started all childs
2014-11-18 19:14:56 TRACE arksea.jactor.Actor.run(Actor.java:220) ChildTask 'event_handler' start up
2014-11-18 19:14:56 DEBUG arksea.jactor.demo.EventHandler.init(EventHandler.java:64) init()
2014-11-18 19:14:56 INFO arksea.jactor.demo.EventHandler.handle_info(EventHandler.java:47) event:event1,oldEvent=none,newEvent=event1
2014-11-18 19:14:56 INFO arksea.jactor.demo.EventHandler.handle_info(EventHandler.java:47) event:event2,oldEvent=event1,newEvent=event2
2014-11-18 19:14:56 INFO arksea.jactor.demo.EventHandler.handle_info(EventHandler.java:47) event:event3,oldEvent=event2,newEvent=event3
event count=3
```
###Jactor缘起

  说是轻量级Actor库，那是从代码量来说的，如果从并发粒度的来看，与Akka对比，这就是个
重量级的Actor库了---每个Actor都是一个Thread。

  显然，你就要问了，我们有Akka了，你为啥要另搞一个呢，这就要说说jactor的缘起了。之前
曾经用Erlang给用户实现了一个简单的服务端模型，后来因为各种原因，需要迁移到Java平台。
这个工程因为客户端不能做修改，也就意味着协议不能修改，而这个项目的通讯协议对Erlang的
消息通信模型是有些依赖的。

  首先我自然是想到了Akka，但是经过研究尝试发现，Akka的消息通信模型与Erlang很有些不同，
比如对于未匹配消息的处理，Erlang是扔回MailBox等待处理，而Akka则是抛弃并抛出异常。
Erlang的这种策略对于同异步间的转换，是相当灵活的，而Akka则受到一定的限制。用Akka，你的
项目最好完全基于Actor，要知道在普通线程和Akka的Actor间的通信有些别扭。

  基于Thread的Actor的模型，即使在并发粒度上，虽然粗糙了一点，但也有自己的用武之地啊。
比如某些类型的任务，最好在单个进程中调度效率来得高，而我们又不想使用原生的Thread来实现，
我们想要获得Actor线程失败后自动重启的能力，希望用消息进行通信而无需进行复杂的同步操作，
还想在同异步间进行方便的转换，好吧，这时候可以试试jactor。

  以后有机会我会尝试用一些例子来说明Erlang与Akka在此处不同所带来的困难，也说说Akka在普通
线程与Actor间进行通信时的尴尬。不管怎么说，jactor就这么诞生了，虽然实现有些笨拙，但在那些
需求比较特殊的地方使用效果还是不错的。
  
  最后，实在是没忍住，吐槽一下Java方法的显式异常声明，对于框架的实现者来说，需要抛出未知
类型的异常那是家常便饭，各种throws Throwable啊，差点没被恶心死。这一点scala就及时的浪子回
头，可惜Java估计是要永远沉沦的了。
