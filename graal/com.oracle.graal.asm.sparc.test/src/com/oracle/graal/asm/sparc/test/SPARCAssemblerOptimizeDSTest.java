/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.asm.sparc.test;

import static com.oracle.graal.asm.sparc.SPARCAssembler.BPCC;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BPR;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BR;
import static com.oracle.graal.asm.sparc.SPARCAssembler.FBPCC;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.ANNUL;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Fcc0;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Xcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.Always;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Add;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.And;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Udivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.RCondition.Rc_z;
import static jdk.vm.ci.sparc.SPARC.g0;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler.Annul;
import com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag;
import com.oracle.graal.asm.sparc.SPARCAssembler.ControlTransferOp;
import com.oracle.graal.asm.sparc.SPARCAssembler.Op3Op;
import com.oracle.graal.asm.sparc.SPARCAssembler.Op3s;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;

@RunWith(Parameterized.class)
public class SPARCAssemblerOptimizeDSTest extends SPARCAssemblerTestBase {

    @Parameters(name = "{0}, {1}, {2}, {3}")
    public static Collection<Object[]> data() {
        List<Object[]> d = new ArrayList<>();
        makeConfiguration(d, new BranchGenerator(1 << 21) {
            @Override
            void emitBranch(SPARCMacroAssembler masm, Label l, ConditionFlag condition, Annul annul) {
                BR.emit(masm, condition, annul, l);
            }
        });
        makeConfiguration(d, new BranchGenerator(1 << 18) {
            @Override
            void emitBranch(SPARCMacroAssembler masm, Label l, ConditionFlag condition, Annul annul) {
                BPCC.emit(masm, Xcc, condition, annul, PREDICT_NOT_TAKEN, l);
            }
        });
        makeConfiguration(d, new BranchGenerator(1 << 15) {
            @Override
            void emitBranch(SPARCMacroAssembler masm, Label l, ConditionFlag condition, Annul annul) {
                BPR.emit(masm, Rc_z, annul, PREDICT_NOT_TAKEN, g0, l);
            }
        }, false);
        makeConfiguration(d, new BranchGenerator(1 << 18) {
            @Override
            void emitBranch(SPARCMacroAssembler masm, Label l, ConditionFlag condition, Annul annul) {
                FBPCC.emit(masm, Fcc0, condition, annul, PREDICT_NOT_TAKEN, l);
            }
        });
        return d;
    }

    private static void makeConfiguration(List<Object[]> d, BranchGenerator gen) {
        makeConfiguration(d, gen, true);
    }

    private static void makeConfiguration(List<Object[]> d, BranchGenerator gen, boolean conditionUsed) {
        int maxDisp = gen.getMaxDisp();
        InstructionGenerator g = new InstructionGenerator(gen);
        d.add(new Object[]{g, maxDisp, true, true});
        d.add(new Object[]{g, maxDisp + 1, false, true});

        // When delay slot is already stuffed
        g = new InstructionGenerator(gen) {
            @Override
            void emitDelaySlot(SPARCMacroAssembler masm) {
                Op3Op.emit(masm, And, g0, 0, g0);
            }
        };
        d.add(new Object[]{g, maxDisp, false, false});

        // When the delay slot is empty but the annul flag is set optimization should happen
        g = new InstructionGenerator(gen, ANNUL);
        d.add(new Object[]{g, maxDisp, true, true});

        // When delay slot is empty but the branch target cannot be copied because branch target
        // might trap
        g = new InstructionGenerator(gen, Udivx);
        d.add(new Object[]{g, maxDisp, false, false});

        if (conditionUsed) {
            // When conditional branch has a unconditional ConditionFlag, optimization must not be
            // done
            g = new InstructionGenerator(gen, Always);
            d.add(new Object[]{g, maxDisp, false, false});
        }
    }

    @Parameter(value = 0) public InstructionGenerator branchCreator;
    @Parameter(value = 1) public int maxDisp;
    @Parameter(value = 2) public boolean optimizationExpected;
    @Parameter(value = 3) public boolean optimizationExpectedBackward;

    @Test
    public void testOptimizeDelaySlotForward() {
        Label l1 = new Label();
        int branchPosition = masm.position();
        branchCreator.emitBranch(masm, l1);
        int delaySlotPosition = masm.position();
        branchCreator.emitDelaySlot(masm);
        for (int i = 0; i < maxDisp - 4; i++) {
            masm.nop();
        }
        masm.bind(l1);
        int targetPosition = masm.position();
        branchCreator.emitBranchTarget(masm);
        masm.nop();

        int delaySlotInsnOriginal = masm.getInt(delaySlotPosition);
        int branchInsnOriginal = masm.getInt(branchPosition);

        masm.peephole();

        int branchInsn = masm.getInt(branchPosition);
        int delaySlotInsn = masm.getInt(delaySlotPosition);

        ControlTransferOp branchOp = (ControlTransferOp) SPARCAssembler.getSPARCOp(branchInsn);
        if (optimizationExpected) {
            assertTrue(branchOp.getAnnul(branchInsn));
            Assert.assertEquals(masm.getInt(targetPosition), delaySlotInsn);
        } else {
            assertEquals("Annul bit must not be changed", branchOp.getAnnul(branchInsnOriginal), branchOp.getAnnul(branchInsn));
            assertEquals(delaySlotInsnOriginal, delaySlotInsn);
        }
    }

    @Test
    public void testOptimizeDelaySlotBackwards() {
        Label l1 = new Label();
        masm.bind(l1);
        int targetPosition = masm.position();

        branchCreator.emitBranchTarget(masm);

        for (int i = 0; i < maxDisp - 2; i++) {
            masm.nop();
        }

        int branchPosition = masm.position();
        branchCreator.emitBranch(masm, l1);

        int delaySlotPosition = masm.position();
        branchCreator.emitDelaySlot(masm);

        int delaySlotInsnOriginal = masm.getInt(delaySlotPosition);
        int branchInsnOriginal = masm.getInt(branchPosition);

        masm.peephole();

        int branchInsn = masm.getInt(branchPosition);
        int delaySlotInsn = masm.getInt(delaySlotPosition);

        ControlTransferOp branchOp = (ControlTransferOp) SPARCAssembler.getSPARCOp(branchInsn);
        if (optimizationExpectedBackward) {
            assertTrue(branchOp.getAnnul(branchInsn));
            Assert.assertEquals(masm.getInt(targetPosition), delaySlotInsn);
        } else {
            assertEquals("Annul bit must not be changed", branchOp.getAnnul(branchInsnOriginal), branchOp.getAnnul(branchInsn));
            assertEquals(delaySlotInsnOriginal, delaySlotInsn);
        }
    }

    public static class InstructionGenerator {
        protected Op3s branchTargetOp = Add;
        protected Annul annul = NOT_ANNUL;
        protected ConditionFlag condition = Equal;
        protected final BranchGenerator branchGenerator;

        public InstructionGenerator(BranchGenerator branchGenerator) {
            this.branchGenerator = branchGenerator;
        }

        public InstructionGenerator(BranchGenerator branchGenerator, Op3s branchTargetOp) {
            this(branchGenerator);
            this.branchTargetOp = branchTargetOp;
        }

        public InstructionGenerator(BranchGenerator branchGenerator, Annul annul) {
            this(branchGenerator);
            this.annul = annul;
        }

        public InstructionGenerator(BranchGenerator branchGenerator, ConditionFlag condition) {
            this(branchGenerator);
            this.condition = condition;
        }

        void emitBranch(SPARCMacroAssembler masm, Label l) {
            branchGenerator.emitBranch(masm, l, condition, annul);
        }

        void emitDelaySlot(SPARCMacroAssembler masm) {
            masm.nop();
        }

        void emitBranchTarget(SPARCMacroAssembler masm) {
            Op3Op.emit(masm, branchTargetOp, g0, 1, g0);
        }

        @Override
        public String toString() {
            return "[branchTargetOp=" + branchTargetOp + ", annul=" + annul + ", condition=" + condition + "]";
        }
    }

    public abstract static class BranchGenerator {
        private final int maxDisp;

        public BranchGenerator(int maxDisp) {
            super();
            this.maxDisp = maxDisp;
        }

        abstract void emitBranch(SPARCMacroAssembler masm, Label l, ConditionFlag condition, Annul annul);

        public int getMaxDisp() {
            return maxDisp;
        }
    }
}
