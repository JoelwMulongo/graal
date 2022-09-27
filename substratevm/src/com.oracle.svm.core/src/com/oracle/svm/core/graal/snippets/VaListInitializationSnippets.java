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
package com.oracle.svm.core.graal.snippets;

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

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.VaListInitializationNode;
import com.oracle.svm.core.graal.stackvalue.LoweredStackValueNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;

public final class VaListInitializationSnippets extends SubstrateTemplates implements Snippets {
    public static final StackValueNode.StackSlotIdentity vaListIdentity = new StackValueNode.StackSlotIdentity("VaListInitializationSnippets.vaListIdentity", true);

    @Snippet
    private static void vaListInitialization(Pointer vaList) {
        Pointer vaListPointer = (Pointer) LoweredStackValueNode.loweredStackValue(FrameAccess.wordSize(), FrameAccess.wordSize(), vaListIdentity);
        vaListPointer.writeWord(0, vaList);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new VaListInitializationSnippets(options, providers, lowerings);
    }

    VaListInitializationSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);
        lowerings.put(VaListInitializationNode.class, new VaListSnippetsLowering());
    }

    protected class VaListSnippetsLowering implements NodeLoweringProvider<VaListInitializationNode> {

        private final SnippetInfo vaListInitialization = snippet(VaListInitializationSnippets.class, "vaListInitialization");

        @Override
        public void lower(VaListInitializationNode node, LoweringTool tool) {
            Arguments args = new Arguments(vaListInitialization, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("vaList", node.getVaList());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}

@AutomaticallyRegisteredFeature
class VaListInitializationSnippetsFeature implements InternalFeature {

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                                  Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        new VaListInitializationSnippets(options, providers, lowerings);
    }
}
