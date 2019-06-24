/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.thread;

import static com.oracle.svm.core.SubstrateOptions.MultiThreaded;
import static com.oracle.svm.core.SubstrateOptions.UseDedicatedVMThread;

import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

/**
 * Only one thread at a time can execute {@linkplain VMOperation}s. This mimics HotSpot's single
 * "VMThread", which probably saves a lot of locking or atomics. The execution order of VM
 * operations is not defined (the only exception are recursive VM operations, see below).
 * <p>
 * At the moment, we support three different processing modes:
 * <ul>
 * <li>Single threaded: if multi-threading is disabled (see
 * {@linkplain SubstrateOptions#MultiThreaded}), the single application thread can always directly
 * execute VM operations. Neither locking nor initiating a safepoint is necessary.</li>
 * <li>Temporary VM threads: if multi-threading is enabled, but no dedicated VM thread is used (see
 * {@linkplain SubstrateOptions#UseDedicatedVMThread}), VM operations are executed by the
 * application thread that queued the VM operation. For the time of the execution, the application
 * thread holds a lock to guarantee that it is the single temporary VM thread.</li>
 * <li>Dedicated VM thread: if {@linkplain SubstrateOptions#UseDedicatedVMThread} is enabled, a
 * dedicated VM thread is spawned during isolate startup and used for the execution of all VM
 * operations.</li>
 * </ul>
 *
 * It is possible that the execution of a VM operation triggers another VM operation explicitly or
 * implicitly (e.g. a GC). Such recursive VM operations are executed immediately (see
 * {@link #immediateQueues}).
 * <p>
 * If a VM operation was queued successfully, it is guaranteed that the VM operation will get
 * executed at some point in time. This is crucial for {@linkplain NativeVMOperation}s as their
 * mutable state (see {@linkplain NativeVMOperationData}) could be allocated on the stack.
 * <p>
 * To avoid unexpected exceptions, we do the following before queuing and executing a VM operation:
 * <ul>
 * <li>We make the yellow zone of the stack accessible. This avoids {@linkplain StackOverflowError}s
 * (especially if no dedicated VM thread is used).</li>
 * <li>We pause recurring callbacks because they can execute arbitrary Java code that can throw
 * exceptions.</li>
 * </ul>
 */
public final class VMOperationControl {
    private static VMThread dedicatedVmThread = null;

    private final WorkQueues mainQueues;
    private final WorkQueues immediateQueues;
    private final OpInProgress inProgress;

    @Platforms(Platform.HOSTED_ONLY.class)
    VMOperationControl() {
        this.mainQueues = new WorkQueues("main", true);
        this.immediateQueues = new WorkQueues("immediate", false);
        this.inProgress = new OpInProgress();
    }

    static VMOperationControl get() {
        return ImageSingletons.lookup(VMOperationControl.class);
    }

    public static void startVmThread() {
        assert UseDedicatedVMThread.getValue();
        assert get().mainQueues.isEmpty();

        dedicatedVmThread = new VMThread();
        Thread thread = new Thread(dedicatedVmThread, "VMThread");
        thread.setDaemon(true);
        thread.start();
        dedicatedVmThread.waitUntilStarted();
    }

    public static void stopVmThread() {
        assert UseDedicatedVMThread.getValue();
        JavaVMOperation.enqueueBlockingNoSafepoint("Stop VMThread", () -> {
            dedicatedVmThread.stop();
        });
        dedicatedVmThread.waitUntilStopped();
        assert get().mainQueues.isEmpty();
    }

    public static boolean isDedicatedVmThread() {
        return isDedicatedVmThread(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean isDedicatedVmThread(IsolateThread thread) {
        if (UseDedicatedVMThread.getValue()) {
            return thread == dedicatedVmThread.getIsolateThread();
        }
        return false;
    }

    public static boolean mayExecuteVmOperations() {
        if (!MultiThreaded.getValue()) {
            return true;
        } else if (UseDedicatedVMThread.getValue()) {
            return isDedicatedVmThread();
        } else {
            return get().mainQueues.mutex.isOwner();
        }
    }

    public static void logRecentEvents(Log log) {
        /*
         * All reads in this method are racy as the currently executed VM operation could finish and
         * a different VM operation could start. So, the read data is not necessarily consistent.
         */
        VMOperationControl control = get();
        VMOperation op = control.inProgress.operation;
        if (op == null) {
            log.string("No VMOperation in progress").newline();
        } else {
            log.string("VMOperation in progress: ").string(op.getName()).newline();
            log.string("  causesSafepoint: ").bool(op.getCausesSafepoint()).newline();
            log.string("  queuingThread: ").zhex(control.inProgress.queueingThread.rawValue()).newline();
            log.string("  executingThread: ").zhex(control.inProgress.executingThread.rawValue()).newline();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    OpInProgress getInProgress() {
        return inProgress;
    }

    @Uninterruptible(reason = "Set the current VM operation as atomically as possible - this is mainly relevant for deopt test cases")
    void setInProgress(VMOperation operation, IsolateThread queueingThread, IsolateThread executingThread) {
        assert operation != null && queueingThread.isNonNull() && executingThread.isNonNull() || operation == null && queueingThread.isNull() && executingThread.isNull();
        inProgress.executingThread = executingThread;
        inProgress.operation = operation;
        inProgress.queueingThread = queueingThread;
    }

    void enqueue(JavaVMOperation operation) {
        enqueue(operation, WordFactory.nullPointer());
    }

    void enqueue(NativeVMOperationData data) {
        enqueue(data.getNativeVMOperation(), data);
    }

    /**
     * Enqueues a {@link VMOperation} and returns as soon as the operation was executed.
     */
    private void enqueue(VMOperation operation, NativeVMOperationData data) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            log().string("[VMOperationControl.enqueue:").string("  operation: ").string(operation.getName());
            if (!MultiThreaded.getValue()) {
                // no safepoint is needed, so we can always directly execute the operation
                assert !UseDedicatedVMThread.getValue();
                markAsQueued(operation, data);
                try {
                    operation.execute(data);
                } finally {
                    markAsFinished(operation, data, null);
                }
            } else if (mayExecuteVmOperations()) {
                // a recursive VM operation (either triggered implicitly or explicitly) -> execute
                // it right away
                immediateQueues.enqueueAndExecute(operation, data);
            } else if (UseDedicatedVMThread.getValue()) {
                // a thread queues an operation that the VM thread will execute
                assert !isDedicatedVmThread() : "the dedicated VM thread must execute and not queue VM operations";
                assert dedicatedVmThread.isRunning() : "must not queue VM operations before the VM thread is started or after it is shut down";
                VMThreads.THREAD_MUTEX.guaranteeNotOwner("could result in deadlocks otherwise");
                mainQueues.enqueueAndWait(operation, data);
            } else {
                // use the current thread to execute the operation under a lock
                VMThreads.THREAD_MUTEX.guaranteeNotOwner("could result in deadlocks otherwise");
                mainQueues.enqueueAndExecute(operation, data);
            }
            assert operation.isFinished(data);
            log().string("]").newline();
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    private static void markAsQueued(VMOperation operation, NativeVMOperationData data) {
        operation.setQueuingThread(data, CurrentIsolate.getCurrentThread());
        operation.setFinished(data, false);
    }

    private static void markAsFinished(VMOperation operation, NativeVMOperationData data, VMCondition operationFinished) {
        operation.setFinished(data, true);
        operation.setQueuingThread(data, WordFactory.nullPointer());
        if (operationFinished != null) {
            operationFinished.broadcast();
        }
    }

    /** Check if it is okay for this thread to block. */
    public static void guaranteeOkayToBlock(String message) {
        /* If the system is frozen at a safepoint, then it is not okay to block. */
        if (isFrozen()) {
            Log.log().string(message).newline();
            VMError.shouldNotReachHere("Should not reach here: Not okay to block.");
        }
    }

    /**
     * This method returns true if the application is currently stopped at a safepoint. This method
     * always returns false if {@linkplain SubstrateOptions#MultiThreaded} is disabled as no
     * safepoints are needed in that case.
     */
    public static boolean isFrozen() {
        boolean result = Safepoint.Master.singleton().isFrozen();
        assert !result || MultiThreaded.getValue();
        return result;
    }

    private static Log log() {
        return SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
    }

    /**
     * A dedicated VM thread that executes {@link VMOperation}s. If the option
     * {@link SubstrateOptions#UseDedicatedVMThread} is enabled, then this thread is the only one
     * that may initiate a safepoint. Therefore, it never gets blocked at a safepoint.
     */
    private static class VMThread implements Runnable {
        private volatile IsolateThread isolateThread;
        private boolean stopped;

        VMThread() {
            this.isolateThread = WordFactory.nullPointer();
            this.stopped = false;
        }

        @Override
        public void run() {
            this.isolateThread = CurrentIsolate.getCurrentThread();

            VMOperationControl control = VMOperationControl.get();
            WorkQueues queues = control.mainQueues;
            while (!stopped) {
                try {
                    queues.waitForWorkAndExecute();
                } catch (Throwable e) {
                    log().string("[VMOperation.execute caught: ").string(e.getClass().getName()).string("]").newline();
                    throw VMError.shouldNotReachHere(e);
                }
            }
            this.isolateThread = WordFactory.nullPointer();
        }

        public void waitUntilStarted() {
            while (isolateThread.isNull()) {
                Thread.yield();
            }
        }

        public void waitUntilStopped() {
            while (isolateThread.isNonNull()) {
                Thread.yield();
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public IsolateThread getIsolateThread() {
            return isolateThread;
        }

        public boolean isRunning() {
            return isolateThread.isNonNull();
        }

        public void stop() {
            assert VMOperation.isInProgress() : "must only be called from a VM operation";
            this.stopped = true;
        }
    }

    private static final class WorkQueues {
        private final NativeVMOperationQueue nativeNonSafepointOperations;
        private final NativeVMOperationQueue nativeSafepointOperations;
        private final JavaVMOperationQueue javaNonSafepointOperations;
        private final JavaVMOperationQueue javaSafepointOperations;

        /**
         * This mutex is used by the application threads and by the VM thread. Only normal lock
         * operations with a full transition may be used here. This restriction is necessary to
         * ensure that a VM operation that needs a safepoint can really bring all other threads to a
         * halt, even if those other threads also want to queue VM operations in the meanwhile.
         */
        private final VMMutex mutex;
        private final VMCondition operationQueued;
        private final VMCondition operationFinished;

        @Platforms(Platform.HOSTED_ONLY.class)
        WorkQueues(String prefix, boolean needsLocking) {
            this.nativeNonSafepointOperations = new NativeVMOperationQueue(prefix + "NativeNonSafepointOperations");
            this.nativeSafepointOperations = new NativeVMOperationQueue(prefix + "NativeSafepointOperations");
            this.javaNonSafepointOperations = new JavaVMOperationQueue(prefix + "JavaNonSafepointOperations");
            this.javaSafepointOperations = new JavaVMOperationQueue(prefix + "JavaSafepointOperations");
            this.mutex = createMutex(needsLocking);
            this.operationQueued = createCondition();
            this.operationFinished = createCondition();
        }

        boolean isEmpty() {
            return nativeNonSafepointOperations.isEmpty() && nativeSafepointOperations.isEmpty() && javaNonSafepointOperations.isEmpty() && javaSafepointOperations.isEmpty();
        }

        void waitForWorkAndExecute() {
            assert isDedicatedVmThread();
            assert !ThreadingSupportImpl.isRecurringCallbackRegistered(CurrentIsolate.getCurrentThread());
            lock();
            try {
                while (isEmpty()) {
                    operationQueued.block();
                }
                executeAllQueuedVMOperations();
            } finally {
                unlock();
            }
        }

        void enqueueAndWait(VMOperation operation, NativeVMOperationData data) {
            assert UseDedicatedVMThread.getValue();
            ThreadingSupportImpl.pauseRecurringCallback();
            try {
                lock();
                try {
                    enqueue(operation, data);
                    operationQueued.broadcast();
                    while (!operation.isFinished(data)) {
                        operationFinished.block();
                    }
                } finally {
                    unlock();
                }
            } finally {
                ThreadingSupportImpl.resumeRecurringCallback();
            }
        }

        void enqueueAndExecute(VMOperation operation, NativeVMOperationData data) {
            ThreadingSupportImpl.pauseRecurringCallback();
            try {
                lock();
                try {
                    enqueue(operation, data);
                    executeAllQueuedVMOperations();
                } finally {
                    assert isEmpty() : "all queued VM operations must have been processed";
                    unlock();
                }
            } finally {
                ThreadingSupportImpl.resumeRecurringCallback();
            }
        }

        private void enqueue(VMOperation operation, NativeVMOperationData data) {
            assertIsLocked();
            markAsQueued(operation, data);
            if (operation instanceof JavaVMOperation) {
                if (operation.getCausesSafepoint()) {
                    javaSafepointOperations.push((JavaVMOperation) operation);
                } else {
                    javaNonSafepointOperations.push((JavaVMOperation) operation);
                }
            } else if (operation instanceof NativeVMOperation) {
                assert operation == data.getNativeVMOperation();
                if (operation.getCausesSafepoint()) {
                    nativeSafepointOperations.push(data);
                } else {
                    nativeNonSafepointOperations.push(data);
                }
            } else {
                VMError.shouldNotReachHere();
            }
        }

        private void executeAllQueuedVMOperations() {
            assertIsLocked();

            // Drain the non-safepoint queues.
            drain(nativeNonSafepointOperations);
            drain(javaNonSafepointOperations);

            // Filter operations that need a safepoint but don't have any work to do.
            filterUnnecessary(nativeSafepointOperations);
            filterUnnecessary(javaSafepointOperations);

            // Drain the safepoint queues.
            if (!nativeSafepointOperations.isEmpty() || !javaSafepointOperations.isEmpty()) {
                String safepointReason = null;
                boolean startedSafepoint = false;
                boolean lockedForSafepoint = false;

                Safepoint.Master master = Safepoint.Master.singleton();
                if (!master.isFrozen()) {
                    startedSafepoint = true;
                    safepointReason = getSafepointReason(nativeSafepointOperations, javaSafepointOperations);
                    lockedForSafepoint = master.freeze(safepointReason);
                }

                try {
                    drain(nativeSafepointOperations);
                    drain(javaSafepointOperations);
                } finally {
                    if (startedSafepoint) {
                        master.thaw(safepointReason, lockedForSafepoint);
                    }
                }
            }
        }

        private static String getSafepointReason(NativeVMOperationQueue nativeSafepointOperations, JavaVMOperationQueue javaSafepointOperations) {
            NativeVMOperationData data = nativeSafepointOperations.peek();
            if (data.isNonNull()) {
                return data.getNativeVMOperation().getName();
            } else {
                VMOperation op = javaSafepointOperations.peek();
                assert op != null;
                return op.getName();
            }
        }

        private void drain(NativeVMOperationQueue workQueue) {
            assertIsLocked();
            if (!workQueue.isEmpty()) {
                Log trace = log();
                trace.string("[Worklist.drain:  queue: ").string(workQueue.name);
                while (!workQueue.isEmpty()) {
                    NativeVMOperationData data = workQueue.pop();
                    VMOperation operation = data.getNativeVMOperation();
                    try {
                        operation.execute(data);
                    } finally {
                        markAsFinished(operation, data, operationFinished);
                    }
                }
                trace.string("]").newline();
            }
        }

        private void drain(JavaVMOperationQueue workQueue) {
            assertIsLocked();
            if (!workQueue.isEmpty()) {
                Log trace = log();
                trace.string("[Worklist.drain:  queue: ").string(workQueue.name);
                while (!workQueue.isEmpty()) {
                    JavaVMOperation operation = workQueue.pop();
                    try {
                        operation.execute(WordFactory.nullPointer());
                    } finally {
                        markAsFinished(operation, WordFactory.nullPointer(), operationFinished);
                    }
                }
                trace.string("]").newline();
            }
        }

        private void filterUnnecessary(JavaVMOperationQueue workQueue) {
            Log trace = log();
            JavaVMOperation prev = null;
            JavaVMOperation op = workQueue.peek();
            while (op != null) {
                JavaVMOperation next = op.getNext();
                if (!op.hasWork(WordFactory.nullPointer())) {
                    trace.string("[Skipping unnecessary operation in queue ").string(workQueue.name).string(": ").string(op.getName());
                    workQueue.remove(prev, op);
                    markAsFinished(op, WordFactory.nullPointer(), operationFinished);
                } else {
                    prev = op;
                }
                op = next;
            }
        }

        private void filterUnnecessary(NativeVMOperationQueue workQueue) {
            Log trace = log();
            NativeVMOperationData prev = WordFactory.nullPointer();
            NativeVMOperationData data = workQueue.peek();
            while (data.isNonNull()) {
                NativeVMOperation op = data.getNativeVMOperation();
                NativeVMOperationData next = data.getNext();
                if (!op.hasWork(data)) {
                    trace.string("[Skipping unnecessary operation in queue ").string(workQueue.name).string(": ").string(op.getName());
                    workQueue.remove(prev, data);
                    markAsFinished(op, data, operationFinished);
                } else {
                    prev = data;
                }
                data = next;
            }
        }

        private void lock() {
            if (mutex != null) {
                mutex.lock();
            }
        }

        private void unlock() {
            if (mutex != null) {
                mutex.unlock();
            }
        }

        private void assertIsLocked() {
            if (mutex != null) {
                mutex.assertIsOwner("must be locked");
            }
        }

        @Platforms(value = Platform.HOSTED_ONLY.class)
        private static VMMutex createMutex(boolean needsLocking) {
            if (needsLocking) {
                return new VMMutex();
            }
            return null;
        }

        @Platforms(value = Platform.HOSTED_ONLY.class)
        private VMCondition createCondition() {
            if (mutex != null && UseDedicatedVMThread.getValue()) {
                return new VMCondition(mutex);
            }
            return null;
        }
    }

    protected abstract static class AllocationFreeQueue<T> {
        final String name;

        AllocationFreeQueue(String name) {
            this.name = name;
        }

        abstract boolean isEmpty();

        abstract void push(T element);

        abstract T pop();

        abstract T peek();

        abstract void remove(T prev, T remove);
    }

    /**
     * A queue that does not allocate because each element has a next pointer. This queue is
     * <em>not</em> multi-thread safe.
     */
    protected abstract static class JavaAllocationFreeQueue<T extends JavaAllocationFreeQueue.Element<T>> extends AllocationFreeQueue<T> {
        private T head;
        private T tail; // can point to an incorrect value if head is null

        JavaAllocationFreeQueue(String name) {
            super(name);
        }

        @Override
        public boolean isEmpty() {
            return head == null;
        }

        @Override
        public void push(T element) {
            assert element.getNext() == null : "must not already be queued";
            if (head == null) {
                head = element;
            } else {
                tail.setNext(element);
            }
            tail = element;
        }

        @Override
        public T pop() {
            if (head == null) {
                return null;
            }
            T resultElement = head;
            head = resultElement.getNext();
            resultElement.setNext(null);
            return resultElement;
        }

        @Override
        public T peek() {
            return head;
        }

        @Override
        void remove(T prev, T remove) {
            if (prev == null) {
                assert head == remove;
                head = remove.getNext();
                remove.setNext(null);
            } else {
                prev.setNext(remove.getNext());
            }
        }

        /** An element for an allocation-free queue. An element can be in at most one queue. */
        public interface Element<T extends Element<T>> {
            T getNext();

            void setNext(T newNext);
        }
    }

    protected static class JavaVMOperationQueue extends JavaAllocationFreeQueue<JavaVMOperation> {
        JavaVMOperationQueue(String name) {
            super(name);
        }
    }

    /**
     * Same implementation as {@link JavaAllocationFreeQueue} but for elements of type
     * {@link NativeVMOperationData}. We can't reuse the other implementation because we need to use
     * the semantics of {@link Word}.
     */
    protected static class NativeVMOperationQueue extends AllocationFreeQueue<NativeVMOperationData> {
        private NativeVMOperationData head;
        private NativeVMOperationData tail; // can point to an incorrect value if head is null

        NativeVMOperationQueue(String name) {
            super(name);
        }

        @Override
        public boolean isEmpty() {
            return head.isNull();
        }

        @Override
        public void push(NativeVMOperationData element) {
            assert element.getNext().isNull() : "must not already be queued";
            if (head.isNull()) {
                head = element;
            } else {
                tail.setNext(element);
            }
            tail = element;
        }

        @Override
        public NativeVMOperationData pop() {
            if (head.isNull()) {
                return WordFactory.nullPointer();
            }
            NativeVMOperationData resultElement = head;
            head = resultElement.getNext();
            resultElement.setNext(WordFactory.nullPointer());
            return resultElement;
        }

        @Override
        public NativeVMOperationData peek() {
            return head;
        }

        @Override
        void remove(NativeVMOperationData prev, NativeVMOperationData remove) {
            if (prev.isNull()) {
                assert head == remove;
                head = remove.getNext();
                remove.setNext(WordFactory.nullPointer());
            } else {
                prev.setNext(remove.getNext());
            }
        }
    }

    /**
     * This class holds the information about the {@link VMOperation} that is currently in progress.
     * We use this class to cache all values that another thread might want to query as we must not
     * access the {@link NativeVMOperationData} from another thread (it is allocated in native
     * memory that can be freed when the operation finishes).
     */
    protected static class OpInProgress {
        VMOperation operation;
        IsolateThread queueingThread;
        IsolateThread executingThread;

        public VMOperation getOperation() {
            return operation;
        }

        public IsolateThread getQueuingThread() {
            return queueingThread;
        }

        public IsolateThread getExecutingThread() {
            return executingThread;
        }
    }
}

@AutomaticFeature
class VMOperationControlFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(SafepointFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VMOperationControl.class, new VMOperationControl());
    }
}
