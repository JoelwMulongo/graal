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
package com.oracle.svm.core.graal.snippets.riscv64;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.util.VMError;

/**
 * Implementation of C {@code va_list} handling for System V systems on RISCV64 (Linux). A
 * {@code va_list} is used for passing the arguments of a C varargs function ({@code ...} in the
 * argument list) to another function. Varargs functions use the same calling convention as other
 * functions, which entails passing the first few arguments in registers and the remaining arguments
 * (if any) on the stack. The {@code va_list} type is void*.
 * <p>
 * Reading a {@code va_list} requires knowing the types of the arguments. General-purpose values
 * (integers and pointers) are passed in the eight 64-bit registers {@code x10} to {@code x17}.
 * Floating-point values are passed in the six 64-bit registers {@code v12} through {@code v17}. The
 * callee is responsible for copying the contents of the registers used to pass variadic arguments
 * to the {@code vararg} save area, which must be contiguous with arguments passed on the stack.
 * <p>
 * Reading an argument from the {@code va_list} only necessitates to read the value pointer by
 * {@code va_list} and then incrementing the pointer using the size of the value read.
 * <p>
 * References:<br>
 * <cite>https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc</cite><br>
 */
final class PosixRISCV64VaListSnippets extends SubstrateTemplates implements Snippets {

    private PosixRISCV64VaListSnippets(OptionValues options, Providers providers) {
        super(options, providers);
    }

    @Snippet
    protected static double vaArgDoubleSnippet(Pointer vaList, int offset) {
        return vaList.readDouble(offset);
    }

    @Snippet
    protected static float vaArgFloatSnippet(Pointer vaList, int offset) {
        // float is always promoted to double when passed in varargs
        return (float) vaArgDoubleSnippet(vaList, offset);
    }

    @Snippet
    protected static long vaArgLongSnippet(Pointer vaList, int offset) {
        return vaList.readLong(offset);
    }

    @Snippet
    protected static int vaArgIntSnippet(Pointer vaList, int offset) {
        return (int) vaArgLongSnippet(vaList, offset);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new PosixRISCV64VaListSnippets(options, providers, lowerings);
    }

    private PosixRISCV64VaListSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);
        lowerings.put(VaListNextArgNode.class, new VaListSnippetsLowering());
    }

    protected class VaListSnippetsLowering implements NodeLoweringProvider<VaListNextArgNode> {

        private final SnippetInfo vaArgDouble = snippet(PosixRISCV64VaListSnippets.class, "vaArgDoubleSnippet");
        private final SnippetInfo vaArgFloat = snippet(PosixRISCV64VaListSnippets.class, "vaArgFloatSnippet");
        private final SnippetInfo vaArgLong = snippet(PosixRISCV64VaListSnippets.class, "vaArgLongSnippet");
        private final SnippetInfo vaArgInt = snippet(PosixRISCV64VaListSnippets.class, "vaArgIntSnippet");

        @Override
        public void lower(VaListNextArgNode node, LoweringTool tool) {
            SnippetInfo snippet;
            switch (node.getStackKind()) {
                case Double:
                    snippet = vaArgDouble;
                    break;
                case Float:
                    snippet = vaArgFloat;
                    break;
                case Long:
                    snippet = vaArgLong;
                    break;
                case Int:
                    // everything narrower than int is promoted to int when passed in varargs
                    snippet = vaArgInt;
                    break;
                default:
                    // getStackKind() should be at least int
                    throw VMError.shouldNotReachHere();
            }
            Arguments args = new Arguments(snippet, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("vaList", node.getVaList());
            args.add("offset", node.getParameterIndex() * 8);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
