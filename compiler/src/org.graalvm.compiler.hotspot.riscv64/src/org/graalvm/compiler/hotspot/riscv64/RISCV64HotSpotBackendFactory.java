/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.riscv64;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.AddressLoweringHotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegisters;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.java.DefaultSuitesCreator;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.AddressLoweringByUsePhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

@ServiceProvider(HotSpotBackendFactory.class)
public class RISCV64HotSpotBackendFactory extends HotSpotBackendFactory {
    @Override
    public String getName() {
        return "community";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        try {
            return Class.forName("jdk.vm.ci.riscv64.RISCV64").asSubclass(Architecture.class);
        } catch (ClassNotFoundException e) {
            // Running Native Image for RISC-V requires a JDK with JVMCI for RISC-V
            return null;
        }
    }

    @Override
    protected Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection, HotSpotHostForeignCallsProvider foreignCalls, MetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes, OptionValues options) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(graalRuntime,
                        compilerConfiguration,
                        config,
                        wordTypes,
                        metaAccess,
                        constantReflection,
                        snippetReflection,
                        foreignCalls,
                        replacements,
                        options,
                        target);
        return plugins;
    }

    @Override
    protected HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new HotSpotBackend(runtime, providers) {
            @Override
            public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
                throw GraalError.unimplemented();
            }

            @Override
            public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
                throw GraalError.unimplemented();
            }
        };
    }

    @Override
    protected HotSpotRegistersProvider createRegisters() {
        try {
            Class<?> riscv64 = Class.forName("jdk.vm.ci.riscv64.RISCV64");
            Class<?> riscv64HotSpotRegisterConfig = Class.forName("jdk.vm.ci.hotspot.riscv64.RISCV64HotSpotRegisterConfig");
            Register tp = (Register) riscv64HotSpotRegisterConfig.getField("tp").get(null);
            Register x27 = (Register) riscv64.getField("x27").get(null);
            Register sp = (Register) riscv64HotSpotRegisterConfig.getField("sp").get(null);
            return new HotSpotRegisters(tp, x27, sp);
        } catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException e) {
            e.printStackTrace();
            throw GraalError.shouldNotReachHere("Running Native Image for RISC-V requires a JDK with JVMCI for RISC-V");
        }
    }

    @Override
    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, HotSpotWordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new RISCV64HotSpotForeignCallsProvider(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    @Override
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    HotSpotRegistersProvider registers, HotSpotReplacementsImpl replacements, OptionValues options) {
        DefaultSuitesCreator suitesCreator = new DefaultSuitesCreator(compilerConfiguration, plugins);
        BasePhase<CoreProviders> addressLoweringPhase = new AddressLoweringByUsePhase(null);
        return new AddressLoweringHotSpotSuitesProvider(suitesCreator, config, runtime, addressLoweringPhase);
    }

    @Override
    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        return new RISCV64HotSpotLoweringProvider(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    protected Value[] createNativeABICallerSaveRegisters(@SuppressWarnings("unused") GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(regConfig.getAllocatableRegisters().asList());
        // Removing callee-saved registers.
        try {
            Class<?> riscv64 = Class.forName("jdk.vm.ci.riscv64.RISCV64");
            /* General Purpose Registers. */
            callerSave.remove(riscv64.getField("x2").get(null));
            callerSave.remove(riscv64.getField("x8").get(null));
            callerSave.remove(riscv64.getField("x9").get(null));
            callerSave.remove(riscv64.getField("x10").get(null));
            callerSave.remove(riscv64.getField("x19").get(null));
            callerSave.remove(riscv64.getField("x20").get(null));
            callerSave.remove(riscv64.getField("x21").get(null));
            callerSave.remove(riscv64.getField("x22").get(null));
            callerSave.remove(riscv64.getField("x23").get(null));
            callerSave.remove(riscv64.getField("x24").get(null));
            callerSave.remove(riscv64.getField("x25").get(null));
            callerSave.remove(riscv64.getField("x26").get(null));
            callerSave.remove(riscv64.getField("x27").get(null));
            /* Floating-Point Registers. */
            callerSave.remove(riscv64.getField("f8").get(null));
            callerSave.remove(riscv64.getField("f9").get(null));
            callerSave.remove(riscv64.getField("f10").get(null));
            callerSave.remove(riscv64.getField("f19").get(null));
            callerSave.remove(riscv64.getField("f20").get(null));
            callerSave.remove(riscv64.getField("f21").get(null));
            callerSave.remove(riscv64.getField("f22").get(null));
            callerSave.remove(riscv64.getField("f23").get(null));
            callerSave.remove(riscv64.getField("f24").get(null));
            callerSave.remove(riscv64.getField("f25").get(null));
            callerSave.remove(riscv64.getField("f26").get(null));
            callerSave.remove(riscv64.getField("f27").get(null));
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            throw GraalError.shouldNotReachHere("Running Native Image for RISC-V requires a JDK with JVMCI for RISC-V");
        }

        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "RISCV64";
    }
}
