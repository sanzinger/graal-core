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

import java.util.EnumSet;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.sparc.SPARC;

import org.junit.Before;

import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.test.GraalTest;

public class SPARCAssemblerTestBase extends GraalTest {

    protected SPARCMacroAssembler masm;

    private static EnumSet<SPARC.CPUFeature> computeFeatures() {
        EnumSet<SPARC.CPUFeature> features = EnumSet.noneOf(SPARC.CPUFeature.class);
        features.add(SPARC.CPUFeature.CBCOND);
        return features;
    }

    private static TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        Architecture arch = new SPARC(computeFeatures());
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Before
    public void setup() {
        TargetDescription target = createTarget();
        masm = new SPARCMacroAssembler(target, null);
    }
}
