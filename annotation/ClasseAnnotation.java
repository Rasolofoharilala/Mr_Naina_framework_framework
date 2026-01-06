package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

// Garder l'annotation à l'exécution pour pouvoir la lire via reflection
@Retention(RetentionPolicy.RUNTIME)
// Cibler les classes / interfaces / enums
@Target(ElementType.TYPE)
public @interface ClasseAnnotation {
    String value() default "";
}