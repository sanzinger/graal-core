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

package com.oracle.graal.replacements;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.RemNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;

/**
 * These snippets are used as fallback for architectures which don't have a remainder operation
 * (AARCH64 and SPARC). We use <code>n % d == n - Truncate(n / d) * d</code> for it instead. This is
 * not correct for some edge cases, so we have to fix it up using these snippets.
 */
public class FloatRemSnippets extends SnippetTemplate.AbstractTemplates implements Snippets {

    private final SnippetTemplate.SnippetInfo drem;
    private final SnippetTemplate.SnippetInfo frem;

    public FloatRemSnippets(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
        super(providers, snippetReflection, target);
        drem = snippet(FloatRemSnippets.class, "dremSnippet");
        frem = snippet(FloatRemSnippets.class, "fremSnippet");
    }

    public void lower(RemNode node, LoweringTool tool) {
        JavaKind kind = node.stamp().getStackKind();
        assert kind == JavaKind.Float || kind == JavaKind.Double;
        if (node instanceof SafeNode) {
            // We already introduced the necessary checks, nothing to do.
            return;
        }
        SnippetTemplate.SnippetInfo snippet = kind == JavaKind.Float ? frem : drem;
        StructuredGraph graph = node.graph();
        Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
        args.add("x", node.getX());
        args.add("y", node.getY());
        template(args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, tool, args);
    }

    @Snippet
    public static float fremSnippet(float x, float y) {
        // JVMS: If either value1' or value2' is NaN, the result is NaN.
        // JVMS: If the dividend is an infinity or the divisor is a zero or both, the result is NaN.
        if (Float.isInfinite(x) || y == 0.0f || Float.isNaN(y)) {
            return Float.NaN;
        }
        // JVMS: If the dividend is finite and the divisor is an infinity, the result equals the
        // dividend.
        // JVMS: If the dividend is a zero and the divisor is finite, the result equals the
        // dividend.
        if (x == 0.0f || Float.isInfinite(y)) {
            return x;
        }

        float result = safeRem(x, y);

        // JVMS: If neither value1' nor value2' is NaN, the sign of the result equals the sign of
        // the dividend.
        if (result == 0.0f && x < 0.0f) {
            return -result;
        }
        return result;
    }

    @Snippet
    public static double dremSnippet(double x, double y) {
        // JVMS: If either value1' or value2' is NaN, the result is NaN.
        // JVMS: If the dividend is an infinity or the divisor is a zero or both, the result is NaN.
        if (Double.isInfinite(x) || y == 0.0 || Double.isNaN(y)) {
            return Double.NaN;
        }
        // JVMS: If the dividend is finite and the divisor is an infinity, the result equals the
        // dividend.
        // JVMS: If the dividend is a zero and the divisor is finite, the result equals the
        // dividend.
        if (x == 0.0 || Double.isInfinite(y)) {
            return x;
        }

        double result = safeRem(x, y);

        // JVMS: If neither value1' nor value2' is NaN, the sign of the result equals the sign of
        // the dividend.
        if (result == 0.0 && x < 0.0) {
            return -result;
        }
        return result;
    }

    @NodeIntrinsic(SafeFloatRemNode.class)
    private static native float safeRem(float x, float y);

    @NodeIntrinsic(SafeFloatRemNode.class)
    private static native double safeRem(double x, double y);

    /**
     * Marker interface to distinguish untreated nodes from ones where we have installed the
     * additional checks.
     */
    private interface SafeNode {
    }

    @NodeInfo
    // static class SafeFloatRemNode extends FloatRemNode implements SafeNode {
    static class SafeFloatRemNode extends RemNode implements SafeNode {
        public static final NodeClass<SafeFloatRemNode> TYPE = NodeClass.create(SafeFloatRemNode.class);

        protected SafeFloatRemNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y);
        }
    }
}
