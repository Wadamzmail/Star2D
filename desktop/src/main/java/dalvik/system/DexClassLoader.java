package dalvik.system;

import com.star4droid.desktop.GameCode;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Desktop shim of Android's DexClassLoader. StageImp.getFromDex(...) (shared runtime source, unchanged) calls
 * {@code new DexClassLoader(project.getDex(), optimizedDir, null, parent)} to load the compiled scene classes
 * (com.star4droid.Game.&lt;scene&gt;). On the desktop those classes are in a JAR (java bytecode, not dex), so this
 * loader resolves the jar through {@link GameCode#resolveJar(String)}:
 * the jar given with --game-jar / found next to the launcher, or a "scenes.jar" next to the project's dex path,
 * or the path itself when it already is a zip.
 */
public class DexClassLoader extends URLClassLoader {
    public DexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(urls(dexPath), parent);
    }

    private static URL[] urls(String dexPath) {
        File jar = GameCode.resolveJar(dexPath);
        if (jar == null) throw new IllegalStateException("No game.jar found for '" + dexPath + "'. " + GameCode.help());
        try {
            return new URL[]{jar.toURI().toURL()};
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
