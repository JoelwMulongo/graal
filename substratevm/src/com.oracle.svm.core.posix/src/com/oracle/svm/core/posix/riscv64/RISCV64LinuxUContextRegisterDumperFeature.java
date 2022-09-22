/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.riscv64;

import static com.oracle.svm.core.RegisterDumper.dumpReg;

import org.graalvm.compiler.core.riscv64.RISCV64ReflectionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.riscv64.RISCV64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.util.VMError;

@Platforms({Platform.LINUX_RISCV64.class})
@AutomaticallyRegisteredFeature
class RISCV64LinuxUContextRegisterDumperFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        Class<?> riscv64 = RISCV64ReflectionUtil.getArch(false);
        VMError.guarantee(RISCV64ReflectionUtil.readStaticField(riscv64, "x27").equals(RISCV64ReservedRegisters.heapBaseRegisterCandidate));
        VMError.guarantee(RISCV64ReflectionUtil.readStaticField(riscv64, "x23").equals(RISCV64ReservedRegisters.threadRegisterCandidate));
        ImageSingletons.add(RegisterDumper.class, new RISCV64LinuxUContextRegisterDumper());
    }
}

class RISCV64LinuxUContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, Signal.ucontext_t uContext, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        Signal.mcontext_linux_riscv64_t sigcontext = uContext.uc_mcontext_linux_riscv64();
        dumpReg(log, "PC  ", sigcontext.gregs().read(0), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X1  ", sigcontext.gregs().read(1), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X2  ", sigcontext.gregs().read(2), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X3  ", sigcontext.gregs().read(3), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X4  ", sigcontext.gregs().read(4), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X5  ", sigcontext.gregs().read(5), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X6  ", sigcontext.gregs().read(6), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X7  ", sigcontext.gregs().read(7), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X8  ", sigcontext.gregs().read(8), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X9  ", sigcontext.gregs().read(9), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X10 ", sigcontext.gregs().read(10), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X11 ", sigcontext.gregs().read(11), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X12 ", sigcontext.gregs().read(12), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X13 ", sigcontext.gregs().read(13), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X14 ", sigcontext.gregs().read(14), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X15 ", sigcontext.gregs().read(15), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X16 ", sigcontext.gregs().read(16), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X17 ", sigcontext.gregs().read(17), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X18 ", sigcontext.gregs().read(18), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X19 ", sigcontext.gregs().read(19), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X20 ", sigcontext.gregs().read(20), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X21 ", sigcontext.gregs().read(21), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X22 ", sigcontext.gregs().read(22), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X23 ", sigcontext.gregs().read(23), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X24 ", sigcontext.gregs().read(24), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X25 ", sigcontext.gregs().read(25), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X26 ", sigcontext.gregs().read(26), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X27 ", sigcontext.gregs().read(27), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X28 ", sigcontext.gregs().read(28), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X29 ", sigcontext.gregs().read(29), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X30 ", sigcontext.gregs().read(30), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X31 ", sigcontext.gregs().read(31), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getHeapBase(Signal.ucontext_t uContext) {
        Signal.GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return WordFactory.pointer(regs.read(27));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getThreadPointer(Signal.ucontext_t uContext) {
        Signal.GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return WordFactory.pointer(regs.read(4));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getSP(Signal.ucontext_t uContext) {
        Signal.GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return WordFactory.pointer(regs.read(2));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getIP(Signal.ucontext_t uContext) {
        // gregs[0] holds the program counter.
        Signal.GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return WordFactory.pointer(regs.read(0));
    }
}
