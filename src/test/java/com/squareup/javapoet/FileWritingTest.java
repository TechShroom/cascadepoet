/*
 * Copyright (C) 2014 Square, Inc.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import javax.lang.model.element.Element;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

@RunWith(JUnit4.class)
public final class FileWritingTest {

    // Used for testing java.io File behavior.
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    // Used for testing java.nio.file Path behavior.
    private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    private final Path fsRoot = this.fs.getRootDirectories().iterator().next();

    // Used for testing annotation processor Filer behavior.
    private final TestFiler filer = new TestFiler(this.fs, this.fsRoot);

    @Test
    public void pathNotDirectory() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile javaFile = JavaFile.builder("example", type).build();
        Path path = this.fs.getPath("/foo/bar");
        Files.createDirectories(path.getParent());
        Files.createFile(path);
        try {
            javaFile.writeTo(path);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("path /foo/bar exists but is not a directory.");
        }
    }

    @Test
    public void fileNotDirectory() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile javaFile = JavaFile.builder("example", type).build();
        File file = new File(this.tmp.newFolder("foo"), "bar");
        file.createNewFile();
        try {
            javaFile.writeTo(file);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("path " + file.getPath() + " exists but is not a directory.");
        }
    }

    @Test
    public void pathDefaultPackage() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile.builder("", type).build().writeTo(this.fsRoot);

        Path testPath = this.fsRoot.resolve("Test.java");
        assertThat(Files.exists(testPath)).isTrue();
    }

    @Test
    public void fileDefaultPackage() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile.builder("", type).build().writeTo(this.tmp.getRoot());

        File testFile = new File(this.tmp.getRoot(), "Test.java");
        assertThat(testFile.exists()).isTrue();
    }

    @Test
    public void filerDefaultPackage() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile.builder("", type).build().writeTo(this.filer);

        Path testPath = this.fsRoot.resolve("Test.java");
        assertThat(Files.exists(testPath)).isTrue();
    }

    @Test
    public void pathNestedClasses() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile.builder("foo", type).build().writeTo(this.fsRoot);
        JavaFile.builder("foo.bar", type).build().writeTo(this.fsRoot);
        JavaFile.builder("foo.bar.baz", type).build().writeTo(this.fsRoot);

        Path fooPath = this.fsRoot.resolve(this.fs.getPath("foo", "Test.java"));
        Path barPath = this.fsRoot.resolve(this.fs.getPath("foo", "bar", "Test.java"));
        Path bazPath = this.fsRoot.resolve(this.fs.getPath("foo", "bar", "baz", "Test.java"));
        assertThat(Files.exists(fooPath)).isTrue();
        assertThat(Files.exists(barPath)).isTrue();
        assertThat(Files.exists(bazPath)).isTrue();
    }

    @Test
    public void fileNestedClasses() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile.builder("foo", type).build().writeTo(this.tmp.getRoot());
        JavaFile.builder("foo.bar", type).build().writeTo(this.tmp.getRoot());
        JavaFile.builder("foo.bar.baz", type).build().writeTo(this.tmp.getRoot());

        File fooDir = new File(this.tmp.getRoot(), "foo");
        File fooFile = new File(fooDir, "Test.java");
        File barDir = new File(fooDir, "bar");
        File barFile = new File(barDir, "Test.java");
        File bazDir = new File(barDir, "baz");
        File bazFile = new File(bazDir, "Test.java");
        assertThat(fooFile.exists()).isTrue();
        assertThat(barFile.exists()).isTrue();
        assertThat(bazFile.exists()).isTrue();
    }

    @Test
    public void filerNestedClasses() throws IOException {
        TypeSpec type = TypeSpec.classBuilder("Test").build();
        JavaFile.builder("foo", type).build().writeTo(this.filer);
        JavaFile.builder("foo.bar", type).build().writeTo(this.filer);
        JavaFile.builder("foo.bar.baz", type).build().writeTo(this.filer);

        Path fooPath = this.fsRoot.resolve(this.fs.getPath("foo", "Test.java"));
        Path barPath = this.fsRoot.resolve(this.fs.getPath("foo", "bar", "Test.java"));
        Path bazPath = this.fsRoot.resolve(this.fs.getPath("foo", "bar", "baz", "Test.java"));
        assertThat(Files.exists(fooPath)).isTrue();
        assertThat(Files.exists(barPath)).isTrue();
        assertThat(Files.exists(bazPath)).isTrue();
    }

    @Test
    public void filerPassesOriginatingElements() throws IOException {
        Element element1_1 = Mockito.mock(Element.class);
        TypeSpec test1 = TypeSpec.classBuilder("Test1").addOriginatingElement(element1_1).build();

        Element element2_1 = Mockito.mock(Element.class);
        Element element2_2 = Mockito.mock(Element.class);
        TypeSpec test2 = TypeSpec.classBuilder("Test2").addOriginatingElement(element2_1)
                .addOriginatingElement(element2_2).build();

        JavaFile.builder("example", test1).build().writeTo(this.filer);
        JavaFile.builder("example", test2).build().writeTo(this.filer);

        Path testPath1 = this.fsRoot.resolve(this.fs.getPath("example", "Test1.java"));
        assertThat(this.filer.getOriginatingElements(testPath1)).containsExactly(element1_1);
        Path testPath2 = this.fsRoot.resolve(this.fs.getPath("example", "Test2.java"));
        assertThat(this.filer.getOriginatingElements(testPath2)).containsExactly(element2_1, element2_2);
    }

    @Test
    public void filerClassesWithTabIndent() throws IOException {
        TypeSpec test = TypeSpec.classBuilder("Test").addField(Date.class, "madeFreshDate")
                .addMethod(MethodSpec.methodBuilder("main").addModifiers(OpenModifier.PUBLIC, OpenModifier.STATIC)
                        .addParameter(String[].class, "args")
                        .addCode("$T.out.println($S);\n", System.class, "Hello World!").build())
                .build();
        JavaFile.builder("foo", test).indent("\t").build().writeTo(this.filer);

        Path fooPath = this.fsRoot.resolve(this.fs.getPath("foo", "Test.java"));
        assertThat(Files.exists(fooPath)).isTrue();
        String source = new String(Files.readAllBytes(fooPath));

        assertThat(source).isEqualTo("" + "package foo;\n" + "\n" + "import java.lang.String;\n"
                + "import java.lang.System;\n" + "import java.util.Date;\n" + "\n" + "class Test {\n"
                + "\tDate madeFreshDate;\n" + "\n" + "\tpublic static void main(String[] args) {\n"
                + "\t\tSystem.out.println(\"Hello World!\");\n" + "\t}\n" + "}\n");
    }
}
