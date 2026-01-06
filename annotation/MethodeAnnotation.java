package annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

// L’annotation sera visible à l’exécution et applicable aux méthodes
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public interface MethodeAnnotation {
    String value();        // ex: "/home"
}
