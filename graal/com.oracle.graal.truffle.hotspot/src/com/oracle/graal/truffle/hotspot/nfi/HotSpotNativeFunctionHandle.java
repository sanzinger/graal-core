/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot.nfi;

import java.util.Arrays;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CompilerDirectives;

public class HotSpotNativeFunctionHandle implements NativeFunctionHandle {

    private final HotSpotNativeFunctionPointer pointer;
    private final Class<?> returnType;
    private final Class<?>[] argumentTypes;

    InstalledCode code;

    public HotSpotNativeFunctionHandle(HotSpotNativeFunctionPointer pointer, Class<?> returnType, Class<?>... argumentTypes) {
        this.pointer = pointer;
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
    }

    HotSpotNativeFunctionPointer getPointer() {
        return pointer;
    }

    Class<?> getReturnType() {
        return returnType;
    }

    Class<?>[] getArgumentTypes() {
        return argumentTypes;
    }

    @SuppressWarnings("try")
    private void traceCall(Object... args) {
        try (Scope s = Debug.scope("GNFI")) {
            if (Debug.isLogEnabled()) {
                Debug.log("[GNFI] %s%s", pointer.getName(), Arrays.toString(args));
            }
        }
    }

    @SuppressWarnings("try")
    private void traceResult(Object result) {
        try (Scope s = Debug.scope("GNFI")) {
            if (Debug.isLogEnabled()) {
                Debug.log("[GNFI] %s --> %s", pointer.getName(), result);
            }
        }
    }

    @Override
    public Object call(Object... args) {
        assert checkArgs(args);
        try {
            if (CompilerDirectives.inInterpreter()) {
                traceCall(args);
            }
            Object res = code.executeVarargs(this, args);
            if (CompilerDirectives.inInterpreter()) {
                traceResult(res);
            }
            return res;
        } catch (InvalidInstalledCodeException e) {
            CompilerDirectives.transferToInterpreter();
            throw JVMCIError.shouldNotReachHere("Execution of GNFI Callstub failed: " + pointer.getName());
        }
    }

    private boolean checkArgs(Object... args) {
        assert args.length == argumentTypes.length : this + " expected " + argumentTypes.length + " args, got " + args.length;
        for (int i = 0; i < argumentTypes.length; i++) {
            Object arg = args[i];
            assert arg != null;
            Class<?> expectedType = argumentTypes[i];
            if (expectedType.isPrimitive()) {
                JavaKind kind = JavaKind.fromJavaClass(expectedType);
                expectedType = kind.toBoxedJavaClass();
            }
            assert expectedType == arg.getClass() : this + " expected arg " + i + " to be " + expectedType.getName() + ", not " + arg.getClass().getName();

        }
        return true;
    }

    @Override
    public String toString() {
        return pointer.getName() + Arrays.toString(argumentTypes);
    }
}
