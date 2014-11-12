package arksea.jactor;

/**
 *
 * @author arksea
 */
public interface IMessageMatcher {

    Message match(Message msg) throws Throwable;
}
