package carpet.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import org.apache.commons.io.IOUtils;

import carpet.CarpetServer;
import carpet.script.argument.FileArgument;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.LevelResource;

public record Module(String name, String code, boolean library)
{
    public Module
    {
        Objects.requireNonNull(name);
        Objects.requireNonNull(code);
    }

    public static Module fromPath(final Path path)
    {
        final boolean library = path.getFileName().toString().endsWith(".scl");
        try
        {
            final String name = path.getFileName().toString().replaceFirst("\\.scl?", "").toLowerCase(Locale.ROOT);
            final String code = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return new Module(name, code, library);
        }
        catch (final IOException e)
        {
            throw new IllegalArgumentException("Failed to load scarpet module", e);
        }
    }

    /**
     * Creates a new {@link Module} with an app located in Carpet's JAR.
     *
     * @param scriptName A {@link String} being the name of the script. The extension will be autocompleted
     * @param isLibrary  A {@link boolean} indicating whether or not the script is a library
     * @return The created {@link BundledModule}
     */
    public static Module carpetNative(final String scriptName, final boolean isLibrary)
    {
        return fromJarPath("assets/carpet/scripts/", scriptName, isLibrary);
    }

    /**
     * Creates a new {@link Module} with an app located at a specified path inside some mod's JAR.
     *
     * @param path       A {@link String} being the path to the directory where the app is located.
     * @param scriptName A {@link String} being the name of the script. The extension will be autocompleted
     * @param isLibrary  A {@link boolean} indicating whether or not the script is a library
     * @return The created {@link BundledModule}
     * @see #fromJarPathWithCustomName(String, String, boolean)
     */
    public static Module fromJarPath(final String path, final String scriptName, final boolean isLibrary)
    {
        return fromJarPathWithCustomName(path + scriptName + (isLibrary ? ".scl" : ".sc"), scriptName, isLibrary);
    }

    /**
     * Creates a new {@link Module} with an app located at the specified fullPath (inside a mod jar)with a custom name.
     *
     * @param fullPath   A {@link String} being the full path to the app's code, including file and extension.
     * @param customName A {@link String} being the custom name for the script.
     * @param isLibrary  A {@link boolean} indicating whether or not the script is a library
     * @return The created {@link Module}
     * @see #fromJarPath(String, String, boolean)
     */
    public static Module fromJarPathWithCustomName(final String fullPath, final String customName, final boolean isLibrary)
    {
        try
        {
            final String name = customName.toLowerCase(Locale.ROOT);
            final String code = IOUtils.toString(
                    Module.class.getClassLoader().getResourceAsStream(fullPath),
                    StandardCharsets.UTF_8
            );
            return new Module(name, code, isLibrary);
        }
        catch (final IOException e)
        {
            throw new IllegalArgumentException("Failed to load bundled module", e);
        }
    }

    public static Tag getData(final Module module)
    {
        final Path dataFile = resolveResource(module);
        if (dataFile == null || !Files.exists(dataFile) || !(Files.isRegularFile(dataFile)))
        {
            return null;
        }
        synchronized (FileArgument.writeIOSync)
        {
            return FileArgument.readTag(dataFile);
        }
    }

    public static void saveData(final Module module, final Tag globalState)
    {
        final Path dataFile = resolveResource(module);
        if (dataFile == null)
        {
            return;
        }
        if (!Files.exists(dataFile.getParent()))
        {
            try
            {
                Files.createDirectories(dataFile.getParent());
            }
            catch (final IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        synchronized (FileArgument.writeIOSync)
        {
            FileArgument.writeTagDisk(globalState, dataFile, false);
        }
    }

    private static Path resolveResource(final Module module)
    {
        return module == null ? null : CarpetServer.minecraft_server.getWorldPath(LevelResource.ROOT).resolve("scripts/" + module.name() + ".data.nbt"); // commandline app
    }

}
