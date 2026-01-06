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
                                try { classes.add(Class.forName(className)); } catch (Throwable e) { /* ignore */ }
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
                                try { classes.add(Class.forName(className)); } catch (Throwable e) { /* ignore */ }
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
