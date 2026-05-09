package com.utilityfinder.app;

/**
 * Fat-JAR entry point. A class that does not extend Application is required as
 * the manifest Main-Class so the JavaFX runtime can bootstrap correctly when
 * loaded from the classpath rather than the module path.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
