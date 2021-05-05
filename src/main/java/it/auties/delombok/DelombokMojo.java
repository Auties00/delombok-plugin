package it.auties.delombok;

import lombok.Lombok;
import lombok.SneakyThrows;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(name = "delombok")
public class DelombokMojo extends AbstractMojo {
    private static final String JAVA_COMMAND = "java -jar %s delombok %s -d %s";

    @Parameter(required = true)
    private File rootDirectory, outputDirectory;

    @Parameter
    private final Set<String> excludedFiles = Set.of("module-info.java");

    @SneakyThrows
    @Override
    public void execute() {
        var start = System.currentTimeMillis();
        getLog().info("Starting delombok process...");

        var sources = findJavaSources();
        getLog().info(String.format("Detected %s files", sources.size()));

        var transform = generateDelombokInput(sources);
        runDelombok(transform);

        moveOutputToCorrectDir(sources);
        getLog().info(String.format("Finished delombok process, took %s ms", System.currentTimeMillis() - start));
    }

    @SneakyThrows
    private void moveOutputToCorrectDir(List<Path> sources) {
        Files.walk(outputDirectory.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    var fileName = path.getFileName().toString();
                    var outputFileName = findMatchingSourceFile(sources, fileName);
                    var outputPath = Path.of(outputDirectory.getPath(), outputFileName);
                    createDirectories(outputPath);
                    moveFile(path, outputPath);
                });
    }

    private String findMatchingSourceFile(List<Path> sources, String fileName) {
        return sources.stream()
                .filter(originalFile -> originalFile.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow()
                .toString()
                .replace(rootDirectory.getPath(), "");
    }

    private String generateDelombokInput(List<Path> sources) {
        return sources.stream()
                .filter(this::filterPath)
                .map(Path::toString)
                .collect(Collectors.joining(" "));
    }

    @SneakyThrows
    private void runDelombok(String transform) {
        new ProcessBuilder(String.format(JAVA_COMMAND, Path.of(Lombok.class.getProtectionDomain().getCodeSource().getLocation().toURI()), transform, outputDirectory.getPath()).split(" "))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
    }

    @SneakyThrows
    private List<Path> findJavaSources() {
        return Files
                .walk(rootDirectory.toPath())
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void createDirectories(Path outputPath) {
        Files.createDirectories(outputPath.getParent());
    }

    @SneakyThrows
    private void moveFile(Path path, Path outputPath) {
        Files.move(path, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @SneakyThrows
    private boolean filterPath(Path path) {
        if(excludedFiles.stream().anyMatch(path.getFileName().toString()::equals)){
            var outputPath = Path.of(outputDirectory.getPath(), path.getFileName().toString());
            createDirectories(outputPath);
            Files.write(outputPath, Files.readAllBytes(path), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return false;
        }

        return true;
    }
}
