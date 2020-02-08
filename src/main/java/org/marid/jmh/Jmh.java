package org.marid.jmh;

/*-
 * #%L
 * maven-jmh
 * %%
 * Copyright (C) 2020 MARID software development group
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.openjdk.jmh.generators.core.BenchmarkGenerator;
import org.openjdk.jmh.generators.core.FileSystemDestination;
import org.openjdk.jmh.generators.reflection.RFGeneratorSource;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.CompilerHints;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.format.OutputFormat;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.VerboseMode;
import org.openjdk.jmh.util.UnCloseablePrintStream;
import sun.reflect.ReflectionFactory;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static org.openjdk.jmh.runner.BenchmarkList.BENCHMARK_LIST;
import static org.openjdk.jmh.runner.CompilerHints.LIST;
import static org.openjdk.jmh.runner.format.OutputFormatFactory.createFormatInstance;

public class Jmh {

  public static Collection<RunResult> start(Options options, OutputFormat outputFormat, Class<?>... classes) {
    final var jmhClassesDir = createJmhClassesDirectory();
    final var classpath = System.getProperty("java.class.path");
    try {
      final var jmhSource = new RFGeneratorSource();
      jmhSource.processClasses(classes);
      final var jmhDest = new FileSystemDestination(jmhClassesDir.toFile(), jmhClassesDir.toFile());
      final var jmhSourceGenerator = new BenchmarkGenerator();
      jmhSourceGenerator.generate(jmhSource, jmhDest);
      jmhSourceGenerator.complete(jmhSource, jmhDest);
      if (jmhDest.hasErrors()) {
        throw jmhDest.getErrors().stream().collect(
          () -> new IllegalStateException("Generation error"),
          (e, err) -> e.addSuppressed(new IllegalStateException(err.getMessage())),
          Throwable::addSuppressed
        );
      }
      final var compiler = ToolProvider.getSystemJavaCompiler();
      final var diagnostics = new DiagnosticCollector<JavaFileObject>();
      final var compilerOutput = new StringWriter();
      final var fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), UTF_8);
      fileManager.setLocationFromPaths(CLASS_OUTPUT, Collections.singleton(jmhClassesDir));
      fileManager.setLocationFromPaths(SOURCE_PATH, Collections.singleton(jmhClassesDir));
      final var javaFiles = javaFiles(jmhClassesDir);
      final var compileTask = compiler.getTask(compilerOutput, fileManager, diagnostics, null, null, javaFiles);
      if (!compileTask.call()) {
        throw new IllegalStateException(compilerOutput.toString().trim());
      }
      System.setProperty("java.class.path", classpath + File.pathSeparator + jmhClassesDir);
      patchCompilerHints(jmhClassesDir);
      final var runner = runner(options, outputFormat, jmhClassesDir);
      return runner.run();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (ReflectiveOperationException | RunnerException e) {
      throw new IllegalStateException(e);
    } finally {
      System.setProperty("java.class.path", classpath);
      deleteJmhClassesDirectory(jmhClassesDir);
    }
  }

  public static Collection<RunResult> start(Options options, Class<?>... classes) {
    return start(options, defaultOutputFormat(options), classes);
  }

  private static void deleteJmhClassesDirectory(Path path) {
    try {
      Files.walkFileTree(path, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.deleteIfExists(file);
          return super.visitFile(file, attrs);
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.deleteIfExists(dir);
          return super.postVisitDirectory(dir, exc);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path createJmhClassesDirectory() {
    try {
      return Files.createTempDirectory("jmh");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<JavaSourceFile> javaFiles(Path path) throws IOException {
    return Files.find(path, Integer.MAX_VALUE, (p, a) -> p.getFileName().toString().endsWith(".java"))
      .map(JavaSourceFile::new)
      .collect(Collectors.toUnmodifiableList());
  }

  private static void patchCompilerHints(Path dir) throws ReflectiveOperationException {
    final var field = CompilerHints.class.getDeclaredField("defaultList");
    field.setAccessible(true);
    field.set(null, CompilerHints.fromFile(dir.resolve(LIST.substring(1)).toString()));
  }

  private static Runner runner(Options options, OutputFormat format, Path dir) throws ReflectiveOperationException {
    final var constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(
      Runner.class,
      Runner.class.getSuperclass().getConstructor(Options.class, OutputFormat.class)
    );
    final var runner = (Runner) constructor.newInstance(options, format);
    final var benchmarkListField = Runner.class.getDeclaredField("list");
    benchmarkListField.setAccessible(true);
    benchmarkListField.set(runner, BenchmarkList.fromFile(dir.resolve(BENCHMARK_LIST.substring(1)).toString()));
    return runner;
  }

  private static OutputFormat defaultOutputFormat(Options options) {
    try {
      final PrintStream printStream;
      if (options.getOutput().hasValue()) {
        printStream = new PrintStream(options.getOutput().get());
      } else {
        printStream = new UnCloseablePrintStream(System.out, Charset.defaultCharset());
      }
      return createFormatInstance(printStream, options.verbosity().orElse(VerboseMode.NORMAL));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class JavaSourceFile extends SimpleJavaFileObject {

    private final Path path;

    protected JavaSourceFile(Path path) {
      super(path.toUri(), Kind.SOURCE);
      this.path = path;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      return Files.readString(path, UTF_8);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      return Files.newOutputStream(path);
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
      return Files.newBufferedReader(path, UTF_8);
    }
  }
}
