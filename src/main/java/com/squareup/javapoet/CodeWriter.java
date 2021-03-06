/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.javapoet;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;
import static com.squareup.javapoet.Util.join;
import static com.squareup.javapoet.Util.stringLiteralWithDoubleQuotes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.SourceVersion;

/**
 * Converts a {@link JavaFile} to a string suitable to both human- and
 * javac-consumption. This honors imports, indentation, and deferred variable
 * names.
 */
final class CodeWriter {

    /**
     * Sentinel value that indicates that no user-provided package has been set.
     */
    private static final String NO_PACKAGE = new String();

    private final String indent;
    private final Appendable out;
    private int indentLevel;

    private boolean javadoc = false;
    private boolean comment = false;
    private String packageName = NO_PACKAGE;
    private final List<TypeSpec> typeSpecStack = new ArrayList<>();
    private final Set<String> staticImportClassNames;
    private final Set<String> staticImports;
    private final Map<String, ClassName> importedTypes;
    private final Map<String, ClassName> importableTypes = new LinkedHashMap<>();
    private final Set<String> referencedNames = new LinkedHashSet<>();
    private boolean trailingNewline;

    /**
     * When emitting a statement, this is the line of the statement currently
     * being written. The first line of a statement is indented normally and
     * subsequent wrapped lines are double-indented. This is -1 when the
     * currently-written line isn't part of a statement.
     */
    int statementLine = -1;

    CodeWriter(Appendable out) {
        this(out, "  ", Collections.<String> emptySet());
    }

    CodeWriter(Appendable out, String indent, Set<String> staticImports) {
        this(out, indent, Collections.<String, ClassName> emptyMap(), staticImports);
    }

    CodeWriter(Appendable out, String indent, Map<String, ClassName> importedTypes, Set<String> staticImports) {
        this.out = checkNotNull(out, "out == null");
        this.indent = checkNotNull(indent, "indent == null");
        this.importedTypes = checkNotNull(importedTypes, "importedTypes == null");
        this.staticImports = checkNotNull(staticImports, "staticImports == null");
        this.staticImportClassNames = new LinkedHashSet<>();
        for (String signature : staticImports) {
            this.staticImportClassNames.add(signature.substring(0, signature.lastIndexOf('.')));
        }
    }

    public Map<String, ClassName> importedTypes() {
        return this.importedTypes;
    }

    public CodeWriter indent() {
        return indent(1);
    }

    public CodeWriter indent(int levels) {
        this.indentLevel += levels;
        return this;
    }

    public CodeWriter unindent() {
        return unindent(1);
    }

    public CodeWriter unindent(int levels) {
        checkArgument(this.indentLevel - levels >= 0, "cannot unindent %s from %s", levels, this.indentLevel);
        this.indentLevel -= levels;
        return this;
    }

    public CodeWriter pushPackage(String packageName) {
        checkState(this.packageName == NO_PACKAGE, "package already set: %s", this.packageName);
        this.packageName = checkNotNull(packageName, "packageName == null");
        return this;
    }

    public CodeWriter popPackage() {
        checkState(this.packageName != NO_PACKAGE, "package already set: %s", this.packageName);
        this.packageName = NO_PACKAGE;
        return this;
    }

    public CodeWriter pushType(TypeSpec type) {
        this.typeSpecStack.add(type);
        return this;
    }

    public CodeWriter popType() {
        this.typeSpecStack.remove(this.typeSpecStack.size() - 1);
        return this;
    }

    public void emitComment(CodeBlock codeBlock) throws IOException {
        this.trailingNewline = true; // Force the '//' prefix for the comment.
        this.comment = true;
        try {
            emit(codeBlock);
            emit("\n");
        } finally {
            this.comment = false;
        }
    }

    public void emitJavadoc(CodeBlock javadocCodeBlock) throws IOException {
        if (javadocCodeBlock.isEmpty())
            return;

        emit("/**\n");
        this.javadoc = true;
        try {
            emit(javadocCodeBlock);
        } finally {
            this.javadoc = false;
        }
        emit(" */\n");
    }

    public void emitAnnotations(List<AnnotationSpec> annotations, boolean inline) throws IOException {
        for (AnnotationSpec annotationSpec : annotations) {
            annotationSpec.emit(this, inline);
            emit(inline ? " " : "\n");
        }
    }

    /**
     * Emits {@code modifiers} in the standard order. Modifiers in
     * {@code implicitModifiers} will not be emitted.
     */
    public void emitModifiers(Set<OpenModifier> modifiers, Set<OpenModifier> implicitModifiers) throws IOException {
        if (modifiers.isEmpty())
            return;
        for (OpenModifier modifier : Util.sortedSet(modifiers, OpenModifier.COMPARATOR)) {
            if (implicitModifiers.contains(modifier))
                continue;
            emitAndIndent(modifier.name().toLowerCase(Locale.US));
            emitAndIndent(" ");
        }
    }

    public void emitModifiers(Set<OpenModifier> modifiers) throws IOException {
        emitModifiers(modifiers, Collections.emptySet());
    }

    /**
     * Emit type variables with their bounds. This should only be used when
     * declaring type variables; everywhere else bounds are omitted.
     */
    public void emitTypeVariables(List<TypeVariableName> typeVariables) throws IOException {
        if (typeVariables.isEmpty())
            return;

        emit("<");
        boolean firstTypeVariable = true;
        for (TypeVariableName typeVariable : typeVariables) {
            if (!firstTypeVariable)
                emit(", ");
            emit("$L", typeVariable.name);
            boolean firstBound = true;
            for (TypeName bound : typeVariable.bounds) {
                emit(firstBound ? " extends $T" : " & $T", bound);
                firstBound = false;
            }
            firstTypeVariable = false;
        }
        emit(">");
    }

    public CodeWriter emit(String s) throws IOException {
        return emitAndIndent(s);
    }

    public CodeWriter emit(String format, Object... args) throws IOException {
        return emit(CodeBlock.of(format, args));
    }

    public CodeWriter emit(CodeBlock codeBlock) throws IOException {
        int a = 0;
        ClassName deferredTypeName = null; // used by "import static" logic
        ListIterator<String> partIterator = codeBlock.formatParts.listIterator();
        while (partIterator.hasNext()) {
            String part = partIterator.next();
            switch (part) {
                case "$L":
                    emitLiteral(codeBlock.args.get(a++));
                    break;

                case "$N":
                    emitAndIndent((String) codeBlock.args.get(a++));
                    break;

                case "$S":
                    String string = (String) codeBlock.args.get(a++);
                    // Emit null as a literal null: no quotes.
                    emitAndIndent(string != null ? stringLiteralWithDoubleQuotes(string, this.indent) : "null");
                    break;

                case "$T":
                    TypeName typeName = (TypeName) codeBlock.args.get(a++);
                    if (typeName.isAnnotated()) {
                        typeName.emitAnnotations(this);
                        typeName = typeName.withoutAnnotations();
                    }
                    // defer "typeName.emit(this)" if next format part will be
                    // handled by the default case
                    if (typeName instanceof ClassName && partIterator.hasNext()) {
                        if (!codeBlock.formatParts.get(partIterator.nextIndex()).startsWith("$")) {
                            ClassName candidate = (ClassName) typeName;
                            if (this.staticImportClassNames.contains(candidate.canonicalName)) {
                                checkState(deferredTypeName == null, "pending type for static import?!");
                                deferredTypeName = candidate;
                                break;
                            }
                        }
                    }
                    typeName.emit(this);
                    break;

                case "$$":
                    emitAndIndent("$");
                    break;

                case "$>":
                    indent();
                    break;

                case "$<":
                    unindent();
                    break;

                case "$[":
                    checkState(this.statementLine == -1, "statement enter $[ followed by statement enter $[");
                    this.statementLine = 0;
                    break;

                case "$]":
                    checkState(this.statementLine != -1, "statement exit $] has no matching statement enter $[");
                    if (this.statementLine > 0) {
                        unindent(2); // End a multi-line statement. Decrease the
                                     // indentation level.
                    }
                    this.statementLine = -1;
                    break;

                default:
                    // handle deferred type
                    if (deferredTypeName != null) {
                        if (part.startsWith(".")) {
                            if (emitStaticImportMember(deferredTypeName.canonicalName, part)) {
                                // okay, static import hit and all was emitted,
                                // so clean-up and jump to next part
                                deferredTypeName = null;
                                break;
                            }
                        }
                        deferredTypeName.emit(this);
                        deferredTypeName = null;
                    }
                    emitAndIndent(part);
                    break;
            }
        }
        return this;
    }

    private static String extractMemberName(String part) {
        checkArgument(Character.isJavaIdentifierStart(part.charAt(0)), "not an identifier: %s", part);
        for (int i = 1; i <= part.length(); i++) {
            if (!SourceVersion.isIdentifier(part.substring(0, i))) {
                return part.substring(0, i - 1);
            }
        }
        return part;
    }

    private boolean emitStaticImportMember(String canonical, String part) throws IOException {
        String partWithoutLeadingDot = part.substring(1);
        if (partWithoutLeadingDot.isEmpty())
            return false;
        char first = partWithoutLeadingDot.charAt(0);
        if (!Character.isJavaIdentifierStart(first))
            return false;
        String explicit = canonical + "." + extractMemberName(partWithoutLeadingDot);
        String wildcard = canonical + ".*";
        if (this.staticImports.contains(explicit) || this.staticImports.contains(wildcard)) {
            emitAndIndent(partWithoutLeadingDot);
            return true;
        }
        return false;
    }

    private void emitLiteral(Object o) throws IOException {
        if (o instanceof TypeSpec) {
            TypeSpec typeSpec = (TypeSpec) o;
            typeSpec.emit(this, null, Collections.emptySet());
        } else if (o instanceof AnnotationSpec) {
            AnnotationSpec annotationSpec = (AnnotationSpec) o;
            annotationSpec.emit(this, true);
        } else if (o instanceof CodeBlock) {
            CodeBlock codeBlock = (CodeBlock) o;
            emit(codeBlock);
        } else {
            emitAndIndent(String.valueOf(o));
        }
    }

    /**
     * Returns the best name to identify {@code className} with in the current
     * context. This uses the available imports and the current scope to find
     * the shortest name available. It does not honor names visible due to
     * inheritance.
     */
    String lookupName(ClassName className) {
        // Find the shortest suffix of className that resolves to className.
        // This uses both local type
        // names (so `Entry` in `Map` refers to `Map.Entry`). Also uses imports.
        boolean nameResolved = false;
        for (ClassName c = className; c != null; c = c.enclosingClassName()) {
            ClassName resolved = resolve(c.simpleName());
            nameResolved = resolved != null;

            if (Objects.equals(resolved, c)) {
                int suffixOffset = c.simpleNames().size() - 1;
                return join(".", className.simpleNames().subList(suffixOffset, className.simpleNames().size()));
            }
        }

        // If the name resolved but wasn't a match, we're stuck with the fully
        // qualified name.
        if (nameResolved) {
            return className.canonicalName;
        }

        // If the class is in the same package, we're done.
        if (Objects.equals(this.packageName, className.packageName())) {
            this.referencedNames.add(className.topLevelClassName().simpleName());
            return join(".", className.simpleNames());
        }

        // We'll have to use the fully-qualified name. Mark the type as
        // importable for a future pass.
        if (!this.javadoc) {
            importableType(className);
        }

        return className.canonicalName;
    }

    private void importableType(ClassName className) {
        if (className.packageName().isEmpty()) {
            return;
        }
        ClassName topLevelClassName = className.topLevelClassName();
        String simpleName = topLevelClassName.simpleName();
        ClassName replaced = this.importableTypes.put(simpleName, topLevelClassName);
        if (replaced != null) {
            this.importableTypes.put(simpleName, replaced); // On collision,
                                                            // prefer the first
                                                            // inserted.
        }
    }

    /**
     * Returns the class referenced by {@code simpleName}, using the current
     * nesting context and imports.
     */
    // TODO(jwilson): also honor superclass members when resolving names.
    private ClassName resolve(String simpleName) {
        // Match a child of the current (potentially nested) class.
        for (int i = this.typeSpecStack.size() - 1; i >= 0; i--) {
            TypeSpec typeSpec = this.typeSpecStack.get(i);
            for (TypeSpec visibleChild : typeSpec.typeSpecs) {
                if (Objects.equals(visibleChild.name, simpleName)) {
                    return stackClassName(i, simpleName);
                }
            }
        }

        // Match the top-level class.
        if (this.typeSpecStack.size() > 0 && Objects.equals(this.typeSpecStack.get(0).name, simpleName)) {
            return ClassName.get(this.packageName, simpleName);
        }

        // Match an imported type.
        ClassName importedType = this.importedTypes.get(simpleName);
        if (importedType != null)
            return importedType;

        // No match.
        return null;
    }

    /**
     * Returns the class named {@code simpleName} when nested in the class at
     * {@code stackDepth}.
     */
    private ClassName stackClassName(int stackDepth, String simpleName) {
        ClassName className = ClassName.get(this.packageName, this.typeSpecStack.get(0).name);
        for (int i = 1; i <= stackDepth; i++) {
            className = className.nestedClass(this.typeSpecStack.get(i).name);
        }
        return className.nestedClass(simpleName);
    }

    /**
     * Emits {@code s} with indentation as required. It's important that all
     * code that writes to {@link #out} does it through here, since we emit
     * indentation lazily in order to avoid unnecessary trailing whitespace.
     */
    CodeWriter emitAndIndent(String s) throws IOException {
        boolean first = true;
        for (String line : s.split("\n", -1)) {
            // Emit a newline character. Make sure blank lines in Javadoc &
            // comments look good.
            if (!first) {
                if ((this.javadoc || this.comment) && this.trailingNewline) {
                    emitIndentation();
                    this.out.append(this.javadoc ? " *" : "//");
                }
                this.out.append('\n');
                this.trailingNewline = true;
                if (this.statementLine != -1) {
                    if (this.statementLine == 0) {
                        indent(2); // Begin multiple-line statement. Increase
                                   // the indentation level.
                    }
                    this.statementLine++;
                }
            }

            first = false;
            if (line.isEmpty())
                continue; // Don't indent empty lines.

            // Emit indentation and comment prefix if necessary.
            if (this.trailingNewline) {
                emitIndentation();
                if (this.javadoc) {
                    this.out.append(" * ");
                } else if (this.comment) {
                    this.out.append("// ");
                }
            }

            this.out.append(line);
            this.trailingNewline = false;
        }
        return this;
    }

    private void emitIndentation() throws IOException {
        for (int j = 0; j < this.indentLevel; j++) {
            this.out.append(this.indent);
        }
    }

    /**
     * Returns the types that should have been imported for this code. If there
     * were any simple name collisions, that type's first use is imported.
     */
    Map<String, ClassName> suggestedImports() {
        Map<String, ClassName> result = new LinkedHashMap<>(this.importableTypes);
        result.keySet().removeAll(this.referencedNames);
        return result;
    }
}
