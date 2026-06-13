package com.genymobile.scrcpy.control;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LifecycleManagerTest {

    /**
     * Records stop()/join() invocations into a shared log, to assert teardown ordering.
     */
    private static final class RecordingComponent implements LifecycleComponent {
        private final String name;
        private final List<String> log;
        private final boolean joinIsNoOp;
        private final boolean throwOnJoin;

        RecordingComponent(String name, List<String> log) {
            this(name, log, false, false);
        }

        RecordingComponent(String name, List<String> log, boolean joinIsNoOp, boolean throwOnJoin) {
            this.name = name;
            this.log = log;
            this.joinIsNoOp = joinIsNoOp;
            this.throwOnJoin = throwOnJoin;
        }

        @Override
        public void stop() {
            log.add(name + ".stop");
        }

        @Override
        public void join() throws InterruptedException {
            if (throwOnJoin) {
                log.add(name + ".join-interrupted");
                throw new InterruptedException();
            }
            if (joinIsNoOp) {
                return;
            }
            log.add(name + ".join");
        }
    }

    @Test
    public void testStopsThenJoinsInRegistrationOrder() throws InterruptedException {
        List<String> log = new ArrayList<>();
        LifecycleManager manager = new LifecycleManager();
        manager.register(new RecordingComponent("a", log));
        manager.register(new RecordingComponent("b", log));
        manager.register(new RecordingComponent("c", log));

        manager.stopAll();
        Assert.assertEquals(Arrays.asList("a.stop", "b.stop", "c.stop"), log);

        manager.joinAll();
        Assert.assertEquals(Arrays.asList("a.stop", "b.stop", "c.stop", "a.join", "b.join", "c.join"), log);
    }

    @Test
    public void testNoOpJoinComponentIsStoppedButNotJoined() throws InterruptedException {
        // Models the keep-active thread: stopped (interrupted) but never joined.
        List<String> log = new ArrayList<>();
        LifecycleManager manager = new LifecycleManager();
        manager.register(new RecordingComponent("keepActive", log, true, false));
        manager.register(new RecordingComponent("recv", log));
        manager.register(new RecordingComponent("sender", log));

        manager.stopAll();
        manager.joinAll();

        Assert.assertEquals(
                Arrays.asList("keepActive.stop", "recv.stop", "sender.stop", "recv.join", "sender.join"),
                log);
    }

    @Test
    public void testRegisterIgnoresNull() throws InterruptedException {
        List<String> log = new ArrayList<>();
        LifecycleManager manager = new LifecycleManager();
        manager.register(null);
        manager.register(new RecordingComponent("only", log));
        manager.register(null);

        manager.stopAll();
        manager.joinAll();

        Assert.assertEquals(Arrays.asList("only.stop", "only.join"), log);
    }

    @Test
    public void testJoinPropagatesInterruptedExceptionAndStopsJoining() {
        List<String> log = new ArrayList<>();
        LifecycleManager manager = new LifecycleManager();
        manager.register(new RecordingComponent("a", log, false, true)); // throws on join
        manager.register(new RecordingComponent("b", log));

        manager.stopAll();
        Assert.assertEquals(Arrays.asList("a.stop", "b.stop"), log);

        try {
            manager.joinAll();
            Assert.fail("expected InterruptedException");
        } catch (InterruptedException expected) {
            // 'a' attempted its join (and threw); 'b' must not have been joined
            Assert.assertEquals(Arrays.asList("a.stop", "b.stop", "a.join-interrupted"), log);
        }
    }
}
