/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.riscv64;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.Register;

public final class RISCV64ReservedRegisters extends ReservedRegisters {

    public static Register threadRegisterCandidate;
    public static Register heapBaseRegisterCandidate;
    public static Register stackBaseRegisterCandidate;

    static {
        try {
            stackBaseRegisterCandidate = (Register) ReflectionUtil.lookupField(Class.forName("jdk.vm.ci.riscv64.RISCV64"), "x2").get(null);
            threadRegisterCandidate = (Register) ReflectionUtil.lookupField(Class.forName("jdk.vm.ci.riscv64.RISCV64"), "x4").get(null);
            heapBaseRegisterCandidate = (Register) ReflectionUtil.lookupField(Class.forName("jdk.vm.ci.riscv64.RISCV64"), "x27").get(null);
        } catch (ClassNotFoundException | IllegalAccessException e) {
            // Running Native Image for RISC-V requires a JDK with JVMCI for RISC-V
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    RISCV64ReservedRegisters() {
        super(stackBaseRegisterCandidate, threadRegisterCandidate, heapBaseRegisterCandidate);
    }
}
