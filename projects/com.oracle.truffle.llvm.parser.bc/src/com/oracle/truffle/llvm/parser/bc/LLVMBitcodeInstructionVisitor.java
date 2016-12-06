/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.bc;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.api.LLVMBaseType;
import com.oracle.truffle.llvm.parser.api.LLVMType;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.parser.api.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.api.model.enums.AsmDialect;
import com.oracle.truffle.llvm.parser.api.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.api.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.api.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.api.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.api.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.api.model.types.AggregateType;
import com.oracle.truffle.llvm.parser.api.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.api.model.types.StructureType;
import com.oracle.truffle.llvm.parser.api.model.types.Type;
import com.oracle.truffle.llvm.parser.api.model.types.VectorType;
import com.oracle.truffle.llvm.parser.api.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.api.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.api.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.api.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.parser.bc.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.bc.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.bc.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

final class LLVMBitcodeInstructionVisitor implements InstructionVisitor {

    private final LLVMBitcodeFunctionVisitor method;

    private final InstructionBlock block;

    private final LLVMSymbolResolver symbols;

    private final NodeFactoryFacade factoryFacade;

    private final LLVMParserRuntime runtime;

    LLVMBitcodeInstructionVisitor(LLVMBitcodeFunctionVisitor method, InstructionBlock block, NodeFactoryFacade factoryFacade) {
        this.method = method;
        this.block = block;
        this.symbols = method.getSymbolResolver();
        this.factoryFacade = factoryFacade;
        this.runtime = method.getRuntime();
    }

    private LLVMExpressionNode[] getPhiWriteNodes() {
        List<LLVMExpressionNode> nodes = new ArrayList<>();
        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMBaseType baseType = phi.getValue().getType().getLLVMBaseType();
                LLVMExpressionNode phiWriteNode = factoryFacade.createFrameWrite(runtime, baseType, value, slot);
                nodes.add(phiWriteNode);
            }
        }
        return nodes.toArray(new LLVMExpressionNode[nodes.size()]);
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        final Type type = allocate.getPointeeType();
        int alignment;
        if (allocate.getAlign() == 0) {
            alignment = runtime.getByteAlignment(type);
        } else {
            alignment = 1 << (allocate.getAlign() - 1);
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }

        final int size = runtime.getByteSize(type);
        final Symbol count = allocate.getCount();
        final LLVMExpressionNode result;
        if (count instanceof NullConstant) {
            result = factoryFacade.createAlloc(runtime, type, size, alignment, null, null);
        } else if (count instanceof IntegerConstant) {
            result = factoryFacade.createAlloc(runtime, type, size * (int) ((IntegerConstant) count).getValue(), alignment, null, null);
        } else {
            LLVMExpressionNode num = symbols.resolve(count);
            result = factoryFacade.createAlloc(runtime, type, size, alignment, count.getType().getLLVMBaseType(), num);
        }

        createFrameWrite(result, allocate);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        LLVMExpressionNode lhs = symbols.resolve(operation.getLHS());
        LLVMExpressionNode rhs = symbols.resolve(operation.getRHS());

        LLVMExpressionNode target = null;
        if (operation.getType() instanceof VectorType) {
            Type operationType = operation.getType();
            final int size = runtime.getByteSize(operationType);
            final int alignment = runtime.getByteAlignment(operationType);
            target = factoryFacade.createAlloc(runtime, operationType, size, alignment, null, null);
        }

        final LLVMBaseType type = operation.getType().getLLVMBaseType();
        final LLVMArithmeticInstructionType opA = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operation.getOperator());
        if (opA != null) {
            final LLVMExpressionNode result = factoryFacade.createArithmeticOperation(runtime, lhs, rhs, opA, type, target);
            createFrameWrite(result, operation);
            return;
        }

        final LLVMLogicalInstructionType opL = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
        if (opL != null) {
            final LLVMExpressionNode result = factoryFacade.createLogicalOperation(runtime, lhs, rhs, opL, type, target);
            createFrameWrite(result, operation);
            return;
        }

        throw new RuntimeException("Missed a binary operator");
    }

    @Override
    public void visit(BranchInstruction branch) {
        method.addTerminatingInstruction(factoryFacade.createUnconditionalBranch(runtime, method.labels().get(branch.getSuccessor().getName()),
                        getPhiWriteNodes()), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(CallInstruction call) {
        final Type targetType = call.getType();
        final LLVMBaseType targetLLVMType = targetType.getLLVMBaseType();
        int argumentCount = getArgumentCount(call, targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final LLVMType[] argTypes = new LLVMType[argumentCount];
        int argIndex = 0;
        if (method.getRuntime().needsStackPointerArgument()) {
            argNodes[argIndex] = factoryFacade.createFrameRead(runtime, LLVMBaseType.ADDRESS, method.getStackSlot());
            argTypes[argIndex] = new LLVMType(LLVMBaseType.ADDRESS);
            argIndex++;
        }
        if (targetType instanceof StructureType) {
            final int size = runtime.getByteSize(targetType);
            final int align = runtime.getByteAlignment(targetType);
            argTypes[argIndex] = new LLVMType(LLVMBaseType.ADDRESS);
            argNodes[argIndex] = factoryFacade.createAlloc(runtime, targetType, size, align, null, null);
            argIndex++;
        }
        for (int i = 0; argIndex < argumentCount; i++, argIndex++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
            argTypes[argIndex] = LLVMTypeHelper.getLLVMType(call.getArgument(i).getType());
        }

        final Symbol target = call.getCallTarget();
        LLVMExpressionNode result = null;
        if (target instanceof FunctionDeclaration) {
            FunctionDeclaration targetDecl = (FunctionDeclaration) target;
            result = factoryFacade.tryCreateFunctionSubstitution(runtime, targetDecl, argNodes, targetDecl.getArgumentTypes().length);
        }
        if (result == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                result = createInlineAssemblerNode(inlineAsmConstant, argNodes, argTypes, targetLLVMType);

            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                result = factoryFacade.createFunctionCall(runtime, function, argNodes, argTypes, targetLLVMType);
            }
        }
        createFrameWrite(result, call);
    }

    private int getArgumentCount(CallInstruction call, final Type targetType) {
        int argumentCount = call.getArgumentCount();
        if (targetType instanceof StructureType) {
            argumentCount++;
        }
        if (method.getRuntime().needsStackPointerArgument()) {
            argumentCount++;
        }
        return argumentCount;
    }

    @Override
    public void visit(CastInstruction cast) {
        LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(cast.getOperator());
        LLVMExpressionNode fromNode = symbols.resolve(cast.getValue());
        Type from = cast.getValue().getType();
        Type to = cast.getType();

        LLVMExpressionNode result = factoryFacade.createCast(runtime, fromNode, to, from, type);
        createFrameWrite(result, cast);
    }

    @Override
    public void visit(CompareInstruction compare) {
        LLVMExpressionNode result = factoryFacade.createComparison(runtime,
                        compare.getOperator(),
                        compare.getLHS().getType(),
                        symbols.resolve(compare.getLHS()),
                        symbols.resolve(compare.getRHS()));

        createFrameWrite(result, compare);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        LLVMExpressionNode conditionNode = symbols.resolve(branch.getCondition());
        int trueIndex = method.labels().get(branch.getTrueSuccessor().getName());
        int falseIndex = method.labels().get(branch.getFalseSuccessor().getName());

        List<LLVMExpressionNode> trueConditionPhiWriteNodes = new ArrayList<>();
        List<LLVMExpressionNode> falseConditionPhiWriteNodes = new ArrayList<>();

        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMBaseType baseType = phi.getValue().getType().getLLVMBaseType();
                LLVMExpressionNode phiWriteNode = factoryFacade.createFrameWrite(runtime, baseType, value, slot);

                if (branch.getTrueSuccessor() == phi.getBlock()) {
                    trueConditionPhiWriteNodes.add(phiWriteNode);
                } else {
                    falseConditionPhiWriteNodes.add(phiWriteNode);
                }
            }
        }
        LLVMExpressionNode[] truePhiWriteNodes = trueConditionPhiWriteNodes.toArray(new LLVMExpressionNode[trueConditionPhiWriteNodes.size()]);
        LLVMExpressionNode[] falsePhiWriteNodes = falseConditionPhiWriteNodes.toArray(new LLVMExpressionNode[falseConditionPhiWriteNodes.size()]);
        LLVMControlFlowNode node = factoryFacade.createConditionalBranch(runtime, trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes);

        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        LLVMExpressionNode vector = symbols.resolve(extract.getVector());
        LLVMExpressionNode index = symbols.resolve(extract.getIndex());
        LLVMBaseType resultType = extract.getType().getLLVMBaseType();

        LLVMExpressionNode result = factoryFacade.createExtractElement(runtime, resultType, vector, index);

        createFrameWrite(result, extract);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        if (!(extract.getAggregate().getType() instanceof ArrayType || extract.getAggregate().getType() instanceof StructureType)) {
            throw new IllegalStateException("\'extractvalue\' can only extract elements of arrays and structs!");
        }
        final LLVMExpressionNode baseAddress = symbols.resolve(extract.getAggregate());
        final Type baseType = extract.getAggregate().getType();
        final int targetIndex = extract.getIndex();
        final LLVMBaseType resultType = extract.getType().getLLVMBaseType();

        LLVMExpressionNode targetAddress = baseAddress;

        final AggregateType aggregateType = (AggregateType) baseType;

        int offset = runtime.getIndexOffset(targetIndex, aggregateType);

        final Type targetType = aggregateType.getElementType(targetIndex);
        if (targetType != null && !((targetType instanceof StructureType) && (((StructureType) targetType).isPacked()))) {
            offset += runtime.getBytePadding(offset, targetType);
        }

        if (offset != 0) {
            final LLVMExpressionNode oneLiteralNode = factoryFacade.createLiteral(runtime, 1, LLVMBaseType.I32);
            targetAddress = factoryFacade.createGetElementPtr(runtime, LLVMBaseType.I32, targetAddress, oneLiteralNode, offset);
        }

        final LLVMExpressionNode result = factoryFacade.createExtractValue(runtime, resultType, targetAddress);
        createFrameWrite(result, extract);
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        final LLVMExpressionNode targetAddress = symbols.resolveElementPointer(gep.getBasePointer(), gep.getIndices());
        createFrameWrite(targetAddress, gep);
    }

    @Override
    public void visit(IndirectBranchInstruction branch) {
        int[] labelTargets = new int[branch.getSuccessorCount()];
        for (int i = 0; i < labelTargets.length; i++) {
            labelTargets[i] = method.labels().get(branch.getSuccessor(i).getName());
        }
        LLVMExpressionNode value = symbols.resolve(branch.getAddress());

        LLVMControlFlowNode node = factoryFacade.createIndirectBranch(runtime, value, labelTargets, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        final LLVMExpressionNode vector = symbols.resolve(insert.getVector());
        final LLVMExpressionNode index = symbols.resolve(insert.getIndex());
        final LLVMExpressionNode element = symbols.resolve(insert.getValue());
        final Type type = insert.getType();
        final LLVMBaseType resultType = type.getLLVMBaseType();
        final LLVMExpressionNode result = factoryFacade.createInsertElement(runtime, resultType, vector, type, element, index);
        createFrameWrite(result, insert);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        if (!(insert.getAggregate().getType() instanceof StructureType || insert.getAggregate().getType() instanceof ArrayType)) {
            throw new IllegalStateException("\'insertvalue\' can only insert values into arrays and structs!");
        }
        final AggregateType sourceType = (AggregateType) insert.getAggregate().getType();
        final LLVMExpressionNode sourceAggregate = symbols.resolve(insert.getAggregate());
        final LLVMExpressionNode valueToInsert = symbols.resolve(insert.getValue());
        final LLVMBaseType valueType = insert.getValue().getType().getLLVMBaseType();
        final int targetIndex = insert.getIndex();
        final int size = runtime.getByteSize(sourceType);
        final int alignment = runtime.getByteAlignment(sourceType);

        final LLVMExpressionNode resultAggregate = factoryFacade.createAlloc(runtime, sourceType, size, alignment, null, null);

        final int offset = runtime.getIndexOffset(targetIndex, sourceType);
        final LLVMExpressionNode result = factoryFacade.createInsertValue(runtime, resultAggregate, sourceAggregate,
                        runtime.getByteSize(sourceType), offset, valueToInsert, valueType);

        createFrameWrite(result, insert);
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMExpressionNode source = symbols.resolve(load.getSource());
        LLVMExpressionNode result = factoryFacade.createLoad(runtime, load.getType(), source);
        createFrameWrite(result, load);
    }

    @Override
    public void visit(PhiInstruction pi) {
    }

    @Override
    public void visit(ReturnInstruction ret) {
        LLVMControlFlowNode node;
        if (ret.getValue() == null) {
            node = factoryFacade.createRetVoid(runtime);
        } else {
            final Type type = ret.getValue().getType();
            method.getFrame().findFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID).setKind(type.getFrameSlotKind());
            final LLVMExpressionNode value = symbols.resolve(ret.getValue());
            node = factoryFacade.createNonVoidRet(runtime, value, type);
        }
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(SelectInstruction select) {
        final LLVMExpressionNode condition = symbols.resolve(select.getCondition());
        final LLVMExpressionNode trueValue = symbols.resolve(select.getTrueValue());
        final LLVMExpressionNode falseValue = symbols.resolve(select.getFalseValue());
        final Type type = select.getType();

        final LLVMExpressionNode result = factoryFacade.createSelect(runtime, type, condition, trueValue, falseValue);

        createFrameWrite(result, select);
    }

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        final LLVMExpressionNode vector1 = symbols.resolve(shuffle.getVector1());
        final LLVMExpressionNode vector2 = symbols.resolve(shuffle.getVector2());
        final LLVMExpressionNode mask = symbols.resolve(shuffle.getMask());

        final Type type = shuffle.getType();
        final int size = runtime.getByteSize(type);
        final int alignment = runtime.getByteAlignment(type);
        final LLVMExpressionNode destination = factoryFacade.createAlloc(runtime, type, size, alignment, null, null);

        final LLVMExpressionNode result = factoryFacade.createShuffleVector(runtime, type.getLLVMBaseType(), destination, vector1, vector2, mask);

        createFrameWrite(result, shuffle);
    }

    @Override
    public void visit(StoreInstruction store) {
        final LLVMExpressionNode pointerNode = symbols.resolve(store.getDestination());
        final LLVMExpressionNode valueNode = symbols.resolve(store.getSource());

        final Type type = store.getSource().getType();

        final LLVMExpressionNode node = factoryFacade.createStore(runtime, pointerNode, valueNode, type);

        method.addInstruction(node);
    }

    @Override
    public void visit(SwitchInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());
        int defaultLabel = method.labels().get(zwitch.getDefaultBlock().getName());
        int[] otherLabels = new int[zwitch.getCaseCount()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = symbols.resolve(zwitch.getCaseValue(i));
        }
        LLVMBaseType llvmType = zwitch.getCondition().getType().getLLVMBaseType();

        LLVMControlFlowNode node = factoryFacade.createSwitch(runtime, cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(SwitchOldInstruction zwitch) {
        final LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());

        final int defaultLabel = method.labels().get(zwitch.getDefaultBlock().getName());
        final int[] otherLabels = new int[zwitch.getCaseCount()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }

        final LLVMBaseType llvmType = zwitch.getCondition().getType().getLLVMBaseType();
        final LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            // the case value is always a long here regardless of the values actual type, implicit
            // casts to smaller types in the factoryfacade won't work
            switch (llvmType) {
                case I8:
                    cases[i] = factoryFacade.createLiteral(runtime, (byte) zwitch.getCaseValue(i), llvmType);
                    break;
                case I16:
                    cases[i] = factoryFacade.createLiteral(runtime, (short) zwitch.getCaseValue(i), llvmType);
                    break;
                case I32:
                    cases[i] = factoryFacade.createLiteral(runtime, (int) zwitch.getCaseValue(i), llvmType);
                    break;
                default:
                    cases[i] = factoryFacade.createLiteral(runtime, zwitch.getCaseValue(i), llvmType);
            }
        }

        final LLVMControlFlowNode node = factoryFacade.createSwitch(runtime, cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        method.addTerminatingInstruction(factoryFacade.createUnreachableNode(runtime), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(VoidCallInstruction call) {
        final Symbol target = call.getCallTarget();
        final int argumentCount;
        int explicitArgumentCount = call.getArgumentCount();
        if (method.getRuntime().needsStackPointerArgument()) {
            argumentCount = explicitArgumentCount + 1;
        } else {
            argumentCount = explicitArgumentCount;
        }
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final LLVMType[] argsType = new LLVMType[argumentCount];

        int argIndex = 0;
        if (method.getRuntime().needsStackPointerArgument()) {
            args[argIndex] = factoryFacade.createFrameRead(runtime, LLVMBaseType.ADDRESS, method.getStackSlot());
            argsType[argIndex] = new LLVMType(LLVMBaseType.ADDRESS);
            argIndex++;
        }
        for (int i = 0; i < explicitArgumentCount; i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = LLVMTypeHelper.getLLVMType(call.getArgument(i).getType());
            argIndex++;
        }

        LLVMExpressionNode node = null;
        if (target instanceof FunctionDeclaration) {
            // number of arguments of the caller so llvm intrinsics can distinguish varargs
            final int parentArgCount = method.getArgCount();
            node = factoryFacade.tryCreateFunctionSubstitution(runtime, (FunctionDeclaration) target, args, parentArgCount);
        }
        if (node == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                node = createInlineAssemblerNode(inlineAsmConstant, args, argsType, call.getType().getLLVMBaseType());
            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                node = factoryFacade.createFunctionCall(runtime, function, args, argsType, call.getType().getLLVMBaseType());
            }
        }
        method.addInstruction(node);
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source) {
        final LLVMExpressionNode node = factoryFacade.createFrameWrite(runtime, source.getType().getLLVMBaseType(), result, method.getSlot(source.getName()));
        method.addInstruction(node);
    }

    private LLVMExpressionNode createInlineAssemblerNode(InlineAsmConstant inlineAsmConstant, LLVMExpressionNode[] argNodes, LLVMType[] argsType, LLVMBaseType retType) {
        if (inlineAsmConstant.hasSideEffects()) {
            LLVMLogger.info("Parsing Inline Assembly Constant with Sideeffects!");
        }
        if (inlineAsmConstant.needsAlignedStack()) {
            throw new UnsupportedOperationException("Assembly Expressions that require an aligned Stack are not supported yet!");
        }
        if (inlineAsmConstant.getDialect() != AsmDialect.AT_T) {
            throw new UnsupportedOperationException("Unsupported Assembly Dialect: " + inlineAsmConstant.getDialect());
        }
        return factoryFacade.createInlineAssemblerExpression(runtime, inlineAsmConstant.getAsmExpression(), inlineAsmConstant.getAsmFlags(), argNodes, argsType, retType);
    }
}
