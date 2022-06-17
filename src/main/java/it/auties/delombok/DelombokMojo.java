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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toUnmodifiableList;

@Mojo(name = "delombok")
public class DelombokMojo extends AbstractMojo {
    @Parameter(required = true)
    private File rootDirectory, outputDirectory;

    @Parameter
    private final Set<String> excludedFiles = Set.of("module-info.java");

    @Parameter
    private final Map<String, String> parameters = Map.of();

    @SneakyThrows
    @Override
    public void execute() {
        if(!rootDirectory.exists()){
            getLog().error("Root directory doesn't exist");
            return;
        }

        var start = System.currentTimeMillis();
        getLog().info("Starting delombok process...");

        var sources = findJavaSources();
        getLog().info(String.format("Detected %s files", sources.size()));

        var transform = generateDelombokInput(sources);
        if(!runDelombok(transform)){
            return;
        }

        fixPath(sources);
        getLog().info(String.format("Finished delombok process, took %s ms", System.currentTimeMillis() - start));
    }

    @SneakyThrows
    private List<Path> findJavaSources() {
        try(var walker = Files.walk(rootDirectory.toPath())){
            return walker.filter(Files::isRegularFile)
                    .collect(toUnmodifiableList());
        }
    }

    @SneakyThrows
    private void fixPath(List<Path> sources) {
        try(var walker = Files.walk(outputDirectory.toPath())){
            walker.filter(Files::isRegularFile)
                    .forEach(path -> fixPath(sources, path));
        }
    }

    private void fixPath(List<Path> sources, Path path) {
        var fileName = path.getFileName().toString();
        var outputFileName = findMatchingSourceFile(sources, fileName);
        var outputPath = Path.of(outputDirectory.getPath(), outputFileName);
        createDirectories(outputPath);
        moveFile(path, outputPath);
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
                .filter(this::shouldDelombok)
                .map(rootDirectory.toPath()::relativize)
                .map(Path::toString)
                .collect(Collectors.joining(" "));
    }

    @SneakyThrows
    private boolean runDelombok(String input) {
        var lombok = findLombokJar();
        var output = outputDirectory.toPath().toAbsolutePath();
        var command = String.format("java -jar %s delombok %s -d %s %s",
                lombok, input, output, createParameters());
        getLog().info(String.format("Using command: %s", command));
        var process = new ProcessBuilder()
                .directory(rootDirectory)
                .command(command.split(" "))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
        return process.waitFor() == 0;
    }

    private String createParameters() {
        return parameters.entrySet()
                .stream()
                .map(DelombokMojo::createParameter)
                .collect(Collectors.joining(" "));
    }

    private static String createParameter(Entry<String, String> entry) {
        var key = entry.getKey();
        var value = entry.getValue() == null ? ""
                : String.format("=%s", entry.getValue());
        return String.format("--%s%s", key, value);
    }

    @SneakyThrows
    private static Path findLombokJar() {
        var lombok = Lombok.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI();
        return Path.of(lombok)
                .toRealPath();
    }

    @SneakyThrows
    private void moveFile(Path path, Path outputPath) {
        Files.move(path, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @SneakyThrows
    private boolean shouldDelombok(Path input) {
        if (excludedFiles.stream().noneMatch(input.getFileName().toString()::equals)) {
            return true;
        }

        var output = Path.of(outputDirectory.getPath(),
                input.getFileName().toString());
        createDirectories(output);
        Files.write(output, Files.readAllBytes(input),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return false;
    }

    @SneakyThrows
    private void createDirectories(Path outputPath) {
        Files.createDirectories(outputPath.getParent());
    }
}
