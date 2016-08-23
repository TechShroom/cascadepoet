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
import static com.squareup.javapoet.Util.requireExactlyOneOf;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;

/** A generated class, interface, or enum declaration. */
public final class TypeSpec {

    public final Kind kind;
    public final String name;
    public final CodeBlock anonymousTypeArguments;
    public final CodeBlock javadoc;
    public final List<AnnotationSpec> annotations;
    public final Set<OpenModifier> modifiers;
    public final List<TypeVariableName> typeVariables;
    public final TypeName superclass;
    public final List<TypeName> superinterfaces;
    public final Map<String, TypeSpec> enumConstants;
    public final List<FieldSpec> fieldSpecs;
    public final CodeBlock staticBlock;
    public final CodeBlock initializerBlock;
    public final List<MethodSpec> methodSpecs;
    public final List<TypeSpec> typeSpecs;
    public final List<Element> originatingElements;

    private TypeSpec(Builder builder) {
        this.kind = builder.kind;
        this.name = builder.name;
        this.anonymousTypeArguments = builder.anonymousTypeArguments;
        this.javadoc = builder.javadoc.build();
        this.annotations = Util.immutableList(builder.annotations);
        this.modifiers = Util.immutableSet(builder.modifiers);
        this.typeVariables = Util.immutableList(builder.typeVariables);
        this.superclass = builder.superclass;
        this.superinterfaces = Util.immutableList(builder.superinterfaces);
        this.enumConstants = Util.immutableMap(builder.enumConstants);
        this.fieldSpecs = Util.immutableList(builder.fieldSpecs);
        this.staticBlock = builder.staticBlock.build();
        this.initializerBlock = builder.initializerBlock.build();
        this.methodSpecs = Util.immutableList(builder.methodSpecs);
        this.typeSpecs = Util.immutableList(builder.typeSpecs);

        List<Element> originatingElementsMutable = new ArrayList<>();
        originatingElementsMutable.addAll(builder.originatingElements);
        for (TypeSpec typeSpec : builder.typeSpecs) {
            originatingElementsMutable.addAll(typeSpec.originatingElements);
        }
        this.originatingElements = Util.immutableList(originatingElementsMutable);
    }

    public boolean hasModifier(OpenModifier modifier) {
        return this.modifiers.contains(modifier);
    }

    public static Builder classBuilder(String name) {
        return new Builder(Kind.CLASS, checkNotNull(name, "name == null"), null);
    }

    public static Builder classBuilder(ClassName className) {
        return classBuilder(checkNotNull(className, "className == null").simpleName());
    }

    public static Builder interfaceBuilder(String name) {
        return new Builder(Kind.INTERFACE, checkNotNull(name, "name == null"), null);
    }

    public static Builder interfaceBuilder(ClassName className) {
        return interfaceBuilder(checkNotNull(className, "className == null").simpleName());
    }

    public static Builder enumBuilder(String name) {
        return new Builder(Kind.ENUM, checkNotNull(name, "name == null"), null);
    }

    public static Builder enumBuilder(ClassName className) {
        return enumBuilder(checkNotNull(className, "className == null").simpleName());
    }

    public static Builder anonymousClassBuilder(String typeArgumentsFormat, Object... args) {
        return new Builder(Kind.CLASS, null, CodeBlock.builder().add(typeArgumentsFormat, args).build());
    }

    public static Builder annotationBuilder(String name) {
        return new Builder(Kind.ANNOTATION, checkNotNull(name, "name == null"), null);
    }

    public static Builder annotationBuilder(ClassName className) {
        return annotationBuilder(checkNotNull(className, "className == null").simpleName());
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this.kind, this.name, this.anonymousTypeArguments);
        builder.javadoc.add(this.javadoc);
        builder.annotations.addAll(this.annotations);
        builder.modifiers.addAll(this.modifiers);
        builder.typeVariables.addAll(this.typeVariables);
        builder.superclass = this.superclass;
        builder.superinterfaces.addAll(this.superinterfaces);
        builder.enumConstants.putAll(this.enumConstants);
        builder.fieldSpecs.addAll(this.fieldSpecs);
        builder.methodSpecs.addAll(this.methodSpecs);
        builder.typeSpecs.addAll(this.typeSpecs);
        builder.initializerBlock.add(this.initializerBlock);
        builder.staticBlock.add(this.staticBlock);
        return builder;
    }

    void emit(CodeWriter codeWriter, String enumName, Set<OpenModifier> implicitModifiers) throws IOException {
        // Nested classes interrupt wrapped line indentation. Stash the current
        // wrapping state and put
        // it back afterwards when this type is complete.
        int previousStatementLine = codeWriter.statementLine;
        codeWriter.statementLine = -1;

        try {
            codeWriter.pushType(this);
            if (enumName != null) {
                codeWriter.emitJavadoc(this.javadoc);
                codeWriter.emitAnnotations(this.annotations, false);
                codeWriter.emit("$L", enumName);
                if (!this.anonymousTypeArguments.formatParts.isEmpty()) {
                    codeWriter.emit("(");
                    codeWriter.emit(this.anonymousTypeArguments);
                    codeWriter.emit(")");
                }
                if (this.fieldSpecs.isEmpty() && this.methodSpecs.isEmpty() && this.typeSpecs.isEmpty()) {
                    return; // Avoid unnecessary braces "{}".
                }
                codeWriter.emit(" {\n");
            } else if (this.anonymousTypeArguments != null) {
                TypeName supertype = !this.superinterfaces.isEmpty() ? this.superinterfaces.get(0) : this.superclass;
                codeWriter.emit("new $T(", supertype);
                codeWriter.emit(this.anonymousTypeArguments);
                codeWriter.emit(") {\n");
            } else {
                codeWriter.emitJavadoc(this.javadoc);
                codeWriter.emitAnnotations(this.annotations, false);
                codeWriter.emitModifiers(this.modifiers, Util.union(implicitModifiers, this.kind.asMemberModifiers));
                if (this.kind == Kind.ANNOTATION) {
                    codeWriter.emit("$L $L", "@interface", this.name);
                } else {
                    codeWriter.emit("$L $L", this.kind.name().toLowerCase(Locale.US), this.name);
                }
                codeWriter.emitTypeVariables(this.typeVariables);

                List<TypeName> extendsTypes;
                List<TypeName> implementsTypes;
                if (this.kind == Kind.INTERFACE) {
                    extendsTypes = this.superinterfaces;
                    implementsTypes = Collections.emptyList();
                } else {
                    extendsTypes = this.superclass.equals(ClassName.OBJECT) ? Collections.<TypeName> emptyList()
                            : Collections.singletonList(this.superclass);
                    implementsTypes = this.superinterfaces;
                }

                if (!extendsTypes.isEmpty()) {
                    codeWriter.emit(" extends");
                    boolean firstType = true;
                    for (TypeName type : extendsTypes) {
                        if (!firstType)
                            codeWriter.emit(",");
                        codeWriter.emit(" $T", type);
                        firstType = false;
                    }
                }

                if (!implementsTypes.isEmpty()) {
                    codeWriter.emit(" implements");
                    boolean firstType = true;
                    for (TypeName type : implementsTypes) {
                        if (!firstType)
                            codeWriter.emit(",");
                        codeWriter.emit(" $T", type);
                        firstType = false;
                    }
                }

                codeWriter.emit(" {\n");
            }

            codeWriter.indent();
            boolean firstMember = true;
            for (Iterator<Map.Entry<String, TypeSpec>> i = this.enumConstants.entrySet().iterator(); i.hasNext();) {
                Map.Entry<String, TypeSpec> enumConstant = i.next();
                if (!firstMember)
                    codeWriter.emit("\n");
                enumConstant.getValue().emit(codeWriter, enumConstant.getKey(), Collections.emptySet());
                firstMember = false;
                if (i.hasNext()) {
                    codeWriter.emit(",\n");
                } else if (!this.fieldSpecs.isEmpty() || !this.methodSpecs.isEmpty() || !this.typeSpecs.isEmpty()) {
                    codeWriter.emit(";\n");
                } else {
                    codeWriter.emit("\n");
                }
            }

            // Static fields.
            for (FieldSpec fieldSpec : this.fieldSpecs) {
                if (!fieldSpec.hasModifier(OpenModifier.STATIC))
                    continue;
                if (!firstMember)
                    codeWriter.emit("\n");
                fieldSpec.emit(codeWriter, this.kind.implicitFieldModifiers);
                firstMember = false;
            }

            if (!this.staticBlock.isEmpty()) {
                if (!firstMember)
                    codeWriter.emit("\n");
                codeWriter.emit(this.staticBlock);
                firstMember = false;
            }

            // Non-static fields.
            for (FieldSpec fieldSpec : this.fieldSpecs) {
                if (fieldSpec.hasModifier(OpenModifier.STATIC))
                    continue;
                if (!firstMember)
                    codeWriter.emit("\n");
                fieldSpec.emit(codeWriter, this.kind.implicitFieldModifiers);
                firstMember = false;
            }

            // Initializer block.
            if (!this.initializerBlock.isEmpty()) {
                if (!firstMember)
                    codeWriter.emit("\n");
                codeWriter.emit(this.initializerBlock);
                firstMember = false;
            }

            // Constructors.
            for (MethodSpec methodSpec : this.methodSpecs) {
                if (!methodSpec.isConstructor())
                    continue;
                if (!firstMember)
                    codeWriter.emit("\n");
                methodSpec.emit(codeWriter, this.name, this.kind.implicitMethodModifiers);
                firstMember = false;
            }

            // Methods (static and non-static).
            for (MethodSpec methodSpec : this.methodSpecs) {
                if (methodSpec.isConstructor())
                    continue;
                if (!firstMember)
                    codeWriter.emit("\n");
                methodSpec.emit(codeWriter, this.name, this.kind.implicitMethodModifiers);
                firstMember = false;
            }

            // Types.
            for (TypeSpec typeSpec : this.typeSpecs) {
                if (!firstMember)
                    codeWriter.emit("\n");
                typeSpec.emit(codeWriter, null, this.kind.implicitTypeModifiers);
                firstMember = false;
            }

            codeWriter.unindent();

            codeWriter.emit("}");
            if (enumName == null && this.anonymousTypeArguments == null) {
                codeWriter.emit("\n"); // If this type isn't also a value,
                                       // include a trailing newline.
            }
        } finally {
            codeWriter.popType();
            codeWriter.statementLine = previousStatementLine;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (getClass() != o.getClass())
            return false;
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        StringWriter out = new StringWriter();
        try {
            CodeWriter codeWriter = new CodeWriter(out);
            emit(codeWriter, null, Collections.emptySet());
            return out.toString();
        } catch (IOException e) {
            throw new AssertionError();
        }
    }

    public enum Kind {
        CLASS(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet()),

        INTERFACE(Util.immutableSet(Arrays.asList(OpenModifier.PUBLIC, OpenModifier.STATIC, OpenModifier.FINAL)),
                Util.immutableSet(Arrays.asList(OpenModifier.PUBLIC, OpenModifier.ABSTRACT)),
                Util.immutableSet(Arrays.asList(OpenModifier.PUBLIC, OpenModifier.STATIC)),
                Util.immutableSet(Arrays.asList(OpenModifier.STATIC))),

        ENUM(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                Collections.singleton(OpenModifier.STATIC)),

        ANNOTATION(Util.immutableSet(Arrays.asList(OpenModifier.PUBLIC, OpenModifier.STATIC, OpenModifier.FINAL)),
                Util.immutableSet(Arrays.asList(OpenModifier.PUBLIC, OpenModifier.ABSTRACT)),
                Util.immutableSet(Arrays.asList(OpenModifier.PUBLIC, OpenModifier.STATIC)),
                Util.immutableSet(Arrays.asList(OpenModifier.STATIC)));

        private final Set<OpenModifier> implicitFieldModifiers;
        private final Set<OpenModifier> implicitMethodModifiers;
        private final Set<OpenModifier> implicitTypeModifiers;
        private final Set<OpenModifier> asMemberModifiers;

        Kind(Set<OpenModifier> implicitFieldModifiers, Set<OpenModifier> implicitMethodModifiers,
                Set<OpenModifier> implicitTypeModifiers, Set<OpenModifier> asMemberModifiers) {
            this.implicitFieldModifiers = implicitFieldModifiers;
            this.implicitMethodModifiers = implicitMethodModifiers;
            this.implicitTypeModifiers = implicitTypeModifiers;
            this.asMemberModifiers = asMemberModifiers;
        }
    }

    public static final class Builder {

        private final Kind kind;
        private final String name;
        private final CodeBlock anonymousTypeArguments;

        private final CodeBlock.Builder javadoc = CodeBlock.builder();
        private final List<AnnotationSpec> annotations = new ArrayList<>();
        private final List<OpenModifier> modifiers = new ArrayList<>();
        private final List<TypeVariableName> typeVariables = new ArrayList<>();
        private TypeName superclass = ClassName.OBJECT;
        private final List<TypeName> superinterfaces = new ArrayList<>();
        private final Map<String, TypeSpec> enumConstants = new LinkedHashMap<>();
        private final List<FieldSpec> fieldSpecs = new ArrayList<>();
        private final CodeBlock.Builder staticBlock = CodeBlock.builder();
        private final CodeBlock.Builder initializerBlock = CodeBlock.builder();
        private final List<MethodSpec> methodSpecs = new ArrayList<>();
        private final List<TypeSpec> typeSpecs = new ArrayList<>();
        private final List<Element> originatingElements = new ArrayList<>();

        private Builder(Kind kind, String name, CodeBlock anonymousTypeArguments) {
            checkArgument(name == null || SourceVersion.isName(name), "not a valid name: %s", name);
            this.kind = kind;
            this.name = name;
            this.anonymousTypeArguments = anonymousTypeArguments;
        }

        public Builder addJavadoc(String format, Object... args) {
            this.javadoc.add(format, args);
            return this;
        }

        public Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
            checkArgument(annotationSpecs != null, "annotationSpecs == null");
            for (AnnotationSpec annotationSpec : annotationSpecs) {
                this.annotations.add(annotationSpec);
            }
            return this;
        }

        public Builder addAnnotation(AnnotationSpec annotationSpec) {
            this.annotations.add(annotationSpec);
            return this;
        }

        public Builder addAnnotation(ClassName annotation) {
            return addAnnotation(AnnotationSpec.builder(annotation).build());
        }

        public Builder addAnnotation(Class<?> annotation) {
            return addAnnotation(ClassName.get(annotation));
        }

        public Builder addModifiers(OpenModifier... modifiers) {
            checkState(this.anonymousTypeArguments == null, "forbidden on anonymous types.");
            Collections.addAll(this.modifiers, modifiers);
            return this;
        }

        public Builder addTypeVariables(Iterable<TypeVariableName> typeVariables) {
            checkState(this.anonymousTypeArguments == null, "forbidden on anonymous types.");
            checkArgument(typeVariables != null, "typeVariables == null");
            for (TypeVariableName typeVariable : typeVariables) {
                this.typeVariables.add(typeVariable);
            }
            return this;
        }

        public Builder addTypeVariable(TypeVariableName typeVariable) {
            checkState(this.anonymousTypeArguments == null, "forbidden on anonymous types.");
            this.typeVariables.add(typeVariable);
            return this;
        }

        public Builder superclass(TypeName superclass) {
            checkState(this.superclass == ClassName.OBJECT, "superclass already set to " + this.superclass);
            checkArgument(!superclass.isPrimitive(), "superclass may not be a primitive");
            this.superclass = superclass;
            return this;
        }

        public Builder superclass(Type superclass) {
            return superclass(TypeName.get(superclass));
        }

        public Builder addSuperinterfaces(Iterable<? extends TypeName> superinterfaces) {
            checkArgument(superinterfaces != null, "superinterfaces == null");
            for (TypeName superinterface : superinterfaces) {
                this.superinterfaces.add(superinterface);
            }
            return this;
        }

        public Builder addSuperinterface(TypeName superinterface) {
            this.superinterfaces.add(superinterface);
            return this;
        }

        public Builder addSuperinterface(Type superinterface) {
            return addSuperinterface(TypeName.get(superinterface));
        }

        public Builder addEnumConstant(String name) {
            return addEnumConstant(name, anonymousClassBuilder("").build());
        }

        public Builder addEnumConstant(String name, TypeSpec typeSpec) {
            checkState(this.kind == Kind.ENUM, "%s is not enum", this.name);
            checkArgument(typeSpec.anonymousTypeArguments != null, "enum constants must have anonymous type arguments");
            checkArgument(SourceVersion.isName(name), "not a valid enum constant: %s", name);
            this.enumConstants.put(name, typeSpec);
            return this;
        }

        public Builder addFields(Iterable<FieldSpec> fieldSpecs) {
            checkArgument(fieldSpecs != null, "fieldSpecs == null");
            for (FieldSpec fieldSpec : fieldSpecs) {
                addField(fieldSpec);
            }
            return this;
        }

        public Builder addField(FieldSpec fieldSpec) {
            if (this.kind == Kind.INTERFACE || this.kind == Kind.ANNOTATION) {
                requireExactlyOneOf(fieldSpec.modifiers, OpenModifier.PUBLIC, OpenModifier.PRIVATE);
                Set<OpenModifier> check = Util.openModifierSet(OpenModifier.STATIC, OpenModifier.FINAL);
                checkState(fieldSpec.modifiers.containsAll(check), "%s %s.%s requires modifiers %s", this.kind,
                        this.name, fieldSpec.name, check);
            }
            this.fieldSpecs.add(fieldSpec);
            return this;
        }

        public Builder addField(TypeName type, String name, OpenModifier... modifiers) {
            return addField(FieldSpec.builder(type, name, modifiers).build());
        }

        public Builder addField(Type type, String name, OpenModifier... modifiers) {
            return addField(TypeName.get(type), name, modifiers);
        }

        public Builder addStaticBlock(CodeBlock block) {
            this.staticBlock.beginControlFlow("static").add(block).endControlFlow();
            return this;
        }

        public Builder addInitializerBlock(CodeBlock block) {
            if ((this.kind != Kind.CLASS && this.kind != Kind.ENUM)) {
                throw new UnsupportedOperationException(this.kind + " can't have initializer blocks");
            }
            this.initializerBlock.add("{\n").indent().add(block).unindent().add("}\n");
            return this;
        }

        public Builder addMethods(Iterable<MethodSpec> methodSpecs) {
            checkArgument(methodSpecs != null, "methodSpecs == null");
            for (MethodSpec methodSpec : methodSpecs) {
                addMethod(methodSpec);
            }
            return this;
        }

        public Builder addMethod(MethodSpec methodSpec) {
            if (this.kind == Kind.INTERFACE) {
                requireExactlyOneOf(methodSpec.modifiers, OpenModifier.ABSTRACT, OpenModifier.STATIC,
                        OpenModifier.DEFAULT);
                requireExactlyOneOf(methodSpec.modifiers, OpenModifier.PUBLIC, OpenModifier.PRIVATE);
            } else if (this.kind == Kind.ANNOTATION) {
                checkState(methodSpec.modifiers.equals(this.kind.implicitMethodModifiers),
                        "%s %s.%s requires modifiers %s", this.kind, this.name, methodSpec.name,
                        this.kind.implicitMethodModifiers);
            }
            if (this.kind != Kind.ANNOTATION) {
                checkState(methodSpec.defaultValue == null, "%s %s.%s cannot have a default value", this.kind,
                        this.name, methodSpec.name);
            }
            if (this.kind != Kind.INTERFACE) {
                checkState(!methodSpec.modifiers.contains(OpenModifier.DEFAULT), "%s %s.%s cannot be default",
                        this.kind, this.name, methodSpec.name);
            }
            this.methodSpecs.add(methodSpec);
            return this;
        }

        public Builder addTypes(Iterable<TypeSpec> typeSpecs) {
            checkArgument(typeSpecs != null, "typeSpecs == null");
            for (TypeSpec typeSpec : typeSpecs) {
                addType(typeSpec);
            }
            return this;
        }

        public Builder addType(TypeSpec typeSpec) {
            checkArgument(typeSpec.modifiers.containsAll(this.kind.implicitTypeModifiers),
                    "%s %s.%s requires modifiers %s", this.kind, this.name, typeSpec.name,
                    this.kind.implicitTypeModifiers);
            this.typeSpecs.add(typeSpec);
            return this;
        }

        public Builder addOriginatingElement(Element originatingElement) {
            this.originatingElements.add(originatingElement);
            return this;
        }

        public TypeSpec build() {
            checkArgument(this.kind != Kind.ENUM || !this.enumConstants.isEmpty(),
                    "at least one enum constant is required for %s", this.name);

            boolean isAbstract = this.modifiers.contains(OpenModifier.ABSTRACT) || this.kind != Kind.CLASS;
            for (MethodSpec methodSpec : this.methodSpecs) {
                checkArgument(isAbstract || !methodSpec.hasModifier(OpenModifier.ABSTRACT),
                        "non-abstract type %s cannot declare abstract method %s", this.name, methodSpec.name);
            }

            boolean superclassIsObject = this.superclass.equals(ClassName.OBJECT);
            int interestingSupertypeCount = (superclassIsObject ? 0 : 1) + this.superinterfaces.size();
            checkArgument(this.anonymousTypeArguments == null || interestingSupertypeCount <= 1,
                    "anonymous type has too many supertypes");

            return new TypeSpec(this);
        }
    }
}
