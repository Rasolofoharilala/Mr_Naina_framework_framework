package scan;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import annotation.ClasseAnnotation;
import annotation.MethodeAnnotation;
import javax.servlet.ServletContext;

/**
 * Scanner qui parcourt les entrées du classpath (dossiers et jars)
 * et affiche les classes annotées @ClasseAnnotation.
 */
public class ClassPathScanner {

    public static void scanClassPathAndPrint(String... packageFilters) throws Exception {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isEmpty()) return;

        String[] entries = classpath.split(File.pathSeparator);
        Set<Class<?>> classes = new HashSet<>();

        for (String entry : entries) {
            File file = new File(entry);
            if (!file.exists()) continue;

            if (file.isDirectory()) {
                Path base = Paths.get(file.getAbsolutePath());
                try (Stream<Path> stream = Files.walk(base)) {
                    stream.filter(p -> p.toString().endsWith(".class"))
                          .forEach(p -> {
                        try {
                            String rel = base.relativize(p).toString().replace(File.separatorChar, '/');
                            if (rel.contains("$")) return;
                            String className = rel.replace('/', '.').substring(0, rel.length() - 6);
                            if (matchesFilter(className, packageFilters)) {
                                try { classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader())); } catch (Throwable e) { /* ignore */ }
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    });
                } catch (IOException e) {
                    // ignore
                }
            } else if (entry.toLowerCase().endsWith(".jar")) {
                try (JarFile jar = new JarFile(file)) {
                    Enumeration<JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        JarEntry je = en.nextElement();
                        String name = je.getName();
                        if (name.endsWith(".class") && !name.contains("$")) {
                            String className = name.replace('/', '.').substring(0, name.length() - 6);
                            if (matchesFilter(className, packageFilters)) {
                                try { classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader())); } catch (Throwable e) { /* ignore */ }
                            }
                        }
                    }
                } catch (IOException e) {
                    // ignore unreadable jars
                }
            }
        }

        printAnnotatedClasses(classes);
    }

    /**
     * Parcourt le classpath comme scanClassPathAndPrint mais retourne
     * l'ensemble des classes annotées par @ClasseAnnotation (sans afficher).
     * Utilisez cette méthode si vous voulez conserver les résultats en mémoire.
     */
    public static Set<Class<?>> scanClassPath(String... packageFilters) throws Exception {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isEmpty()) return java.util.Collections.emptySet();

        String[] entries = classpath.split(File.pathSeparator);
        Set<Class<?>> classes = new HashSet<>();

        for (String entry : entries) {
            File file = new File(entry);
            if (!file.exists()) continue;

            if (file.isDirectory()) {
                Path base = Paths.get(file.getAbsolutePath());
                try (Stream<Path> stream = Files.walk(base)) {
                    stream.filter(p -> p.toString().endsWith(".class"))
                          .forEach(p -> {
                        try {
                            String rel = base.relativize(p).toString().replace(File.separatorChar, '/');
                            if (rel.contains("$")) return;
                            String className = rel.replace('/', '.').substring(0, rel.length() - 6);
                            if (matchesFilter(className, packageFilters)) {
                                try { classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader())); } catch (Throwable e) { /* ignore */ }
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    });
                } catch (IOException e) {
                    // ignore
                }
            } else if (entry.toLowerCase().endsWith(".jar")) {
                try (JarFile jar = new JarFile(file)) {
                    Enumeration<JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        JarEntry je = en.nextElement();
                        String name = je.getName();
                        if (name.endsWith(".class") && !name.contains("$")) {
                            String className = name.replace('/', '.').substring(0, name.length() - 6);
                            if (matchesFilter(className, packageFilters)) {
                                try { classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader())); } catch (Throwable e) { /* ignore */ }
                            }
                        }
                    }
                } catch (IOException e) {
                    // ignore unreadable jars
                }
            }
        }

        // Filtrer les classes pour ne conserver que celles annotées @ClasseAnnotation
        Set<Class<?>> annotated = new HashSet<>();
        for (Class<?> cls : classes) {
            try {
                if (cls.isAnnotationPresent(ClasseAnnotation.class)) annotated.add(cls);
            } catch (Throwable t) {
                // ignore classes problématiques
            }
        }
        return annotated;
    }

    /**
     * Scanne le répertoire WEB-INF/classes et les jars de WEB-INF/lib de l'application
     * en utilisant les chemins réels fournis par le ServletContext et retourne
     * les classes annotées @ClasseAnnotation.
     */
    public static Set<Class<?>> scanWebApp(ServletContext ctx, String... packageFilters) throws Exception {
        if (ctx == null) return java.util.Collections.emptySet();

        String classesPath = ctx.getRealPath("/WEB-INF/classes");
        String libPath = ctx.getRealPath("/WEB-INF/lib");

        Set<Class<?>> classes = new HashSet<>();

        if (classesPath != null) {
            Path base = Paths.get(classesPath);
            if (Files.exists(base)) {
                try (Stream<Path> stream = Files.walk(base)) {
                    stream.filter(p -> p.toString().endsWith(".class"))
                          .forEach(p -> {
                        try {
                            String rel = base.relativize(p).toString().replace(File.separatorChar, '/');
                            if (rel.contains("$")) return;
                            String className = rel.replace('/', '.').substring(0, rel.length() - 6);
                            if (matchesFilter(className, packageFilters)) {
                                try { classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader())); } catch (Throwable e) { /* ignore */ }
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    });
                }
            }
        }

        if (libPath != null) {
            File libDir = new File(libPath);
            if (libDir.exists() && libDir.isDirectory()) {
                File[] jars = libDir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
                if (jars != null) {
                    for (File jarFile : jars) {
                        try (JarFile jar = new JarFile(jarFile)) {
                            Enumeration<JarEntry> en = jar.entries();
                            while (en.hasMoreElements()) {
                                JarEntry je = en.nextElement();
                                String name = je.getName();
                                if (name.endsWith(".class") && !name.contains("$")) {
                                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                                    if (matchesFilter(className, packageFilters)) {
                                        try { classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader())); } catch (Throwable e) { /* ignore */ }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }

        // Filtrer pour ne garder que les classes annotées
        Set<Class<?>> annotated = new HashSet<>();
        for (Class<?> cls : classes) {
            try {
                if (cls.isAnnotationPresent(ClasseAnnotation.class)) annotated.add(cls);
            } catch (Throwable t) {
                // ignore
            }
        }
        return annotated;
    }

    /**
     * Debug helper: scan webapp and print the annotated classes to stdout (and return them).
     */
    public static Set<Class<?>> scanWebAppDebug(ServletContext ctx, String... packageFilters) throws Exception {
        Set<Class<?>> annotated = scanWebApp(ctx, packageFilters);
        System.out.println("[scanner-debug] annotated classes found: " + annotated.size());
        for (Class<?> cls : annotated) {
            try {
                System.out.println("[scanner-debug] class: " + cls.getName());
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(MethodeAnnotation.class)) {
                        System.out.println("[scanner-debug]   method: " + m.getName() + " -> " + m.getAnnotation(MethodeAnnotation.class).value());
                    }
                }
            } catch (Throwable t) {
                System.out.println("[scanner-debug] failed to inspect class " + cls + " -> " + t);
            }
        }
        return annotated;
    }

    private static boolean matchesFilter(String className, String[] filters) {
        if (filters == null || filters.length == 0) return true;
        for (String f : filters) {
            if (f == null || f.isEmpty()) continue;
            if (className.equals(f) || className.startsWith(f + ".")) return true;
        }
        return false;
    }

    private static void printAnnotatedClasses(Set<Class<?>> classes) {
        for (Class<?> cls : classes) {
            if (cls.isAnnotationPresent(ClasseAnnotation.class)) {
                ClasseAnnotation ca = cls.getAnnotation(ClasseAnnotation.class);
                System.out.println("Classe annotée : " + cls.getName());
                System.out.println(" → description : " + ca.value());

                for (Method m : cls.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(MethodeAnnotation.class)) {
                        MethodeAnnotation ma = m.getAnnotation(MethodeAnnotation.class);
                        System.out.println("  Méthode associée : " + m.getName());
                        System.out.println("   → URL : " + ma.value());
                    }
                }
                System.out.println("--------------------------");
            }
        }
    }
}
