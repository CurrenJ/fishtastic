package grill24.fishtastic.compat;

import java.lang.reflect.Method;

/**
 * Utility class for handling optional integrations via reflection.
 */
public final class CompatUtil {
    public static String GELATIN_UI_CLASS = "io.github.currenj.gelatinui.GelatinUi";

    private CompatUtil() {}

    /**
     * Safely checks if a class exists, and if so, invokes a static method on another class.
     *
     * @param dependencyClassPath The classpath of the required dependency to check.
     * @param targetClassPath The classpath of the target class containing the method to invoke.
     * @param methodName The name of the static method to invoke on the target class.
     * @param params Optional parameters to pass to the method.
     * @return true if the method was successfully invoked, false otherwise.
     */
    public static boolean invokeIfDependencyPresent(String dependencyClassPath, String targetClassPath, String methodName, Object... params) {
        try {
            // Check if the dependency class exists
            Class.forName(dependencyClassPath);

            // Load the target class and method
            Class<?> targetClass = Class.forName(targetClassPath);
            Method method = targetClass.getDeclaredMethod(methodName, toClassArray(params));

            // Invoke the method
            method.invoke(null, params);
            return true;
        } catch (ClassNotFoundException e) {
            // Dependency or target class not found
            return false;
        } catch (ReflectiveOperationException e) {
            // Method not found or invocation failed
            return false;
        }
    }

    /**
     * Converts an array of objects to an array of their respective classes.
     *
     * @param params The objects to convert.
     * @return An array of classes corresponding to the objects.
     */
    private static Class<?>[] toClassArray(Object... params) {
        if (params == null) return new Class<?>[0];
        Class<?>[] classes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            classes[i] = params[i].getClass();
        }
        return classes;
    }

    public static boolean isDependencyPresent(String dependencyClassPath) {
        try {
            Class.forName(dependencyClassPath);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isGelatinUiPresent() {
        return isDependencyPresent(GELATIN_UI_CLASS);
    }
}
