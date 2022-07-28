/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.util.ArrayList;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class SubstrateRISCV64RegisterConfig implements SubstrateRegisterConfig {

    private final TargetDescription target;
    private final int nativeParamsStackOffset;
    private final RegisterArray generalParameterRegs;
    private final RegisterArray fpParameterRegs;
    private final RegisterArray allocatableRegs;
    private final RegisterArray calleeSaveRegisters;
    private final RegisterAttributes[] attributesMap;
    private final MetaAccessProvider metaAccess;

    public SubstrateRISCV64RegisterConfig(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, boolean preserveFramePointer) {
        this.target = target;
        this.metaAccess = metaAccess;

        try {
            Class<?> riscv64 = Class.forName("jdk.vm.ci.riscv64.RISCV64");

            Register x0 = (Register) ReflectionUtil.lookupField(riscv64, "x0").get(null);
            Register x1 = (Register) ReflectionUtil.lookupField(riscv64, "x1").get(null);
            Register x2 = (Register) ReflectionUtil.lookupField(riscv64, "x2").get(null);
            Register x3 = (Register) ReflectionUtil.lookupField(riscv64, "x3").get(null);
            Register x8 = (Register) ReflectionUtil.lookupField(riscv64, "x8").get(null);
            Register x9 = (Register) ReflectionUtil.lookupField(riscv64, "x9").get(null);
            Register x10 = (Register) ReflectionUtil.lookupField(riscv64, "x10").get(null);
            Register x11 = (Register) ReflectionUtil.lookupField(riscv64, "x11").get(null);
            Register x12 = (Register) ReflectionUtil.lookupField(riscv64, "x12").get(null);
            Register x13 = (Register) ReflectionUtil.lookupField(riscv64, "x13").get(null);
            Register x14 = (Register) ReflectionUtil.lookupField(riscv64, "x14").get(null);
            Register x15 = (Register) ReflectionUtil.lookupField(riscv64, "x15").get(null);
            Register x16 = (Register) ReflectionUtil.lookupField(riscv64, "x16").get(null);
            Register x17 = (Register) ReflectionUtil.lookupField(riscv64, "x17").get(null);
            Register x18 = (Register) ReflectionUtil.lookupField(riscv64, "x18").get(null);
            Register x19 = (Register) ReflectionUtil.lookupField(riscv64, "x19").get(null);
            Register x20 = (Register) ReflectionUtil.lookupField(riscv64, "x20").get(null);
            Register x21 = (Register) ReflectionUtil.lookupField(riscv64, "x21").get(null);
            Register x22 = (Register) ReflectionUtil.lookupField(riscv64, "x22").get(null);
            Register x23 = (Register) ReflectionUtil.lookupField(riscv64, "x23").get(null);
            Register x24 = (Register) ReflectionUtil.lookupField(riscv64, "x24").get(null);
            Register x25 = (Register) ReflectionUtil.lookupField(riscv64, "x25").get(null);
            Register x26 = (Register) ReflectionUtil.lookupField(riscv64, "x26").get(null);
            Register x27 = (Register) ReflectionUtil.lookupField(riscv64, "x27").get(null);

            Register f8 = (Register) ReflectionUtil.lookupField(riscv64, "f8").get(null);
            Register f9 = (Register) ReflectionUtil.lookupField(riscv64, "f9").get(null);
            Register f10 = (Register) ReflectionUtil.lookupField(riscv64, "f10").get(null);
            Register f11 = (Register) ReflectionUtil.lookupField(riscv64, "f11").get(null);
            Register f12 = (Register) ReflectionUtil.lookupField(riscv64, "f12").get(null);
            Register f13 = (Register) ReflectionUtil.lookupField(riscv64, "f13").get(null);
            Register f14 = (Register) ReflectionUtil.lookupField(riscv64, "f14").get(null);
            Register f15 = (Register) ReflectionUtil.lookupField(riscv64, "f15").get(null);
            Register f16 = (Register) ReflectionUtil.lookupField(riscv64, "f16").get(null);
            Register f17 = (Register) ReflectionUtil.lookupField(riscv64, "f17").get(null);
            Register f18 = (Register) ReflectionUtil.lookupField(riscv64, "f18").get(null);
            Register f19 = (Register) ReflectionUtil.lookupField(riscv64, "f19").get(null);
            Register f20 = (Register) ReflectionUtil.lookupField(riscv64, "f20").get(null);
            Register f21 = (Register) ReflectionUtil.lookupField(riscv64, "f21").get(null);
            Register f22 = (Register) ReflectionUtil.lookupField(riscv64, "f22").get(null);
            Register f23 = (Register) ReflectionUtil.lookupField(riscv64, "f23").get(null);
            Register f24 = (Register) ReflectionUtil.lookupField(riscv64, "f24").get(null);
            Register f25 = (Register) ReflectionUtil.lookupField(riscv64, "f25").get(null);
            Register f26 = (Register) ReflectionUtil.lookupField(riscv64, "f26").get(null);
            Register f27 = (Register) ReflectionUtil.lookupField(riscv64, "f27").get(null);

            RegisterArray allRegisters = (RegisterArray) ReflectionUtil.lookupField(riscv64, "allRegisters").get(null);

            generalParameterRegs = new RegisterArray(x10, x11, x12, x13, x14, x15, x16, x17);
            fpParameterRegs = new RegisterArray(f10, f11, f12, f13, f14, f15, f16, f17);

            nativeParamsStackOffset = 0;

            ArrayList<Register> regs = new ArrayList<>(allRegisters.asList());
            regs.remove(x2); // sp
            regs.remove(x0); // zero

            if (preserveFramePointer) {
                regs.remove(x8);
            }
            /*
             * If enabled, the heapBaseRegister and threadRegister are x27 and x4, respectively. See
             * RISCV64ReservedRegisters and ReservedRegisters for more information.
             */
            regs.remove(ReservedRegisters.singleton().getHeapBaseRegister());
            regs.remove(ReservedRegisters.singleton().getThreadRegister());
            regs.remove(x1); // ra
            regs.remove(x3); // gp
            allocatableRegs = new RegisterArray(regs);

            switch (config) {
                case NORMAL:
                    calleeSaveRegisters = new RegisterArray();
                    break;

                case NATIVE_TO_JAVA:
                    calleeSaveRegisters = new RegisterArray(x2, x8, x9, x18, x19, x20, x21, x22, x23, x24, x25, x26, x27,
                                    f8, f9, f18, f19, f20, f21, f22, f23, f24, f25, f26, f27);
                    break;

                default:
                    throw shouldNotReachHere();

            }

            attributesMap = RegisterAttributes.createMap(this, allRegisters);
        } catch (ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
            throw shouldNotReachHere("Running Native Image for RISC-V requires a JDK with JVMCI for RISC-V");
        }
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        try {
            Class<?> riscv64 = Class.forName("jdk.vm.ci.riscv64.RISCV64");
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                case Object:
                    return (Register) ReflectionUtil.lookupField(riscv64, "x10").get(null);
                case Float:
                case Double:
                    return (Register) ReflectionUtil.lookupField(riscv64, "f10").get(null);
                case Void:
                    return null;
                default:
                    throw VMError.shouldNotReachHere();
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
            throw shouldNotReachHere("Running Native Image for RISC-V requires a JDK with JVMCI for RISC-V");
        }
    }

    @Override
    public RegisterArray getAllocatableRegisters() {
        return allocatableRegs;
    }

    @Override
    public RegisterArray getCalleeSaveRegisters() {
        return calleeSaveRegisters;
    }

    @Override
    public RegisterArray getCallerSaveRegisters() {
        return getAllocatableRegisters();
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return true;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    @Override
    public RegisterArray getCallingConventionRegisters(Type t, JavaKind kind) {
        throw VMError.unimplemented();
    }

    private int javaStackParameterAssignment(ValueKindFactory<?> valueKindFactory, AllocatableValue[] locations, int index, JavaKind kind, int currentStackOffset, boolean isOutgoing) {
        /* All parameters within Java are assigned slots of at least 8 bytes */
        ValueKind<?> valueKind = valueKindFactory.getValueKind(kind.getStackKind());
        int alignment = Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
        locations[index] = StackSlot.get(valueKind, currentStackOffset, !isOutgoing);
        return currentStackOffset + alignment;
    }

    @Override
    public CallingConvention getCallingConvention(Type t, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        boolean isEntryPoint = type.nativeABI() && !type.outgoing;

        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentFP = 0;

        /*
         * We have to reserve a slot between return address and outgoing parameters for the deopt
         * frame handle. Exception: calls to native methods.
         */
        int currentStackOffset = (type.nativeABI() ? nativeParamsStackOffset : target.wordSize);

        JavaKind[] kinds = new JavaKind[locations.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, (ResolvedJavaType) parameterTypes[i], metaAccess, target);
            kinds[i] = kind;

            Register register = null;
            if (type.kind == SubstrateCallingConventionKind.ForwardReturnValue) {
                VMError.guarantee(i == 0, "Method with calling convention ForwardReturnValue cannot have more than one parameter");
                register = getReturnRegister(kind);
            } else {
                switch (kind) {
                    case Byte:
                    case Boolean:
                    case Short:
                    case Char:
                    case Int:
                    case Long:
                    case Object:
                        if (currentGeneral < generalParameterRegs.size()) {
                            register = generalParameterRegs.get(currentGeneral++);
                        }
                        break;
                    case Float:
                    case Double:
                        if (currentFP < fpParameterRegs.size()) {
                            register = fpParameterRegs.get(currentFP++);
                        }
                        break;
                    default:
                        throw shouldNotReachHere();
                }

            }
            if (register != null) {
                boolean useJavaKind = isEntryPoint && Platform.includedIn(Platform.LINUX.class);
                locations[i] = register.asValue(valueKindFactory.getValueKind(useJavaKind ? kind : kind.getStackKind()));
            } else {
                if (type.nativeABI()) {
                    if (Platform.includedIn(Platform.LINUX.class)) {
                        ValueKind<?> valueKind = valueKindFactory.getValueKind(type.outgoing ? kind.getStackKind() : kind);
                        int alignment = Math.max(kind.getByteCount(), target.wordSize);
                        locations[i] = StackSlot.get(valueKind, currentStackOffset, !type.outgoing);
                        currentStackOffset = currentStackOffset + alignment;
                    } else {
                        throw VMError.shouldNotReachHere();
                    }
                } else {
                    currentStackOffset = javaStackParameterAssignment(valueKindFactory, locations, i, kind, currentStackOffset, type.outgoing);
                }
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : ObjectLayout.getCallSignatureKind(isEntryPoint, (ResolvedJavaType) returnType, metaAccess, target);
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(valueKindFactory.getValueKind(returnKind.getStackKind()));
        return new SubstrateCallingConvention(type, kinds, currentStackOffset, returnLocation, locations);
    }

    @Override
    public RegisterArray filterAllocatableRegisters(PlatformKind kind, RegisterArray registers) {
        throw unimplemented();
    }

}
