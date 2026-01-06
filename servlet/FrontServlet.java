package servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.ServerException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import annotation.MethodeAnnotation;
import java.util.Set;
import scan.ClassPathScanner;
 

 

public class FrontServlet extends HttpServlet {
    // clé de contexte pour stocker les classes annotées
    public static final String ATTR_ANNOTATED_CLASSES = "annotatedClasses";

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            // Use the debug variant to print what the scanner actually finds at startup.
            Set<Class<?>> classes = ClassPathScanner.scanWebAppDebug(getServletContext());
            getServletContext().setAttribute(ATTR_ANNOTATED_CLASSES, classes);
            System.out.println("FrontServlet init: cached " + classes.size() + " annotated classes.");
        } catch (Exception e) {
            throw new ServletException("Failed to scan classpath for annotated classes", e);
        }
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServerException, IOException, ServletException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        // Si la requête pointe vers la racine (ex: "" ou "/"), servir la ressource par défaut
        if (path == null || path.isEmpty() || "/".equals(path)) {
            defaultServe(req, resp);
            return;
        }

        boolean ressourceExists = getServletContext().getResourceAsStream(path) != null;

        if (ressourceExists) {
            defaultServe(req, resp);
        } else {
            customServe(req, resp);
        }

    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServerException, IOException, ServletException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        // Si la requête pointe vers la racine (ex: "" ou "/"), servir la ressource par défaut
        if (path == null || path.isEmpty() || "/".equals(path)) {
            defaultServe(req, resp);
            return;
        }

        boolean ressourceExists = getServletContext().getResourceAsStream(path) != null;

        if (ressourceExists) {
            defaultServe(req, resp);
        } else {
            customServe(req, resp);
        }

    }

    private void customServe(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // For non-static resources, decide here whether the requested path
        // matches a @MethodeAnnotation and write the HTML response directly.
        String checkParam = req.getParameter("check");
        String path = (checkParam != null && !checkParam.isEmpty()) ? checkParam : req.getRequestURI().substring(req.getContextPath().length());

        Object o = getServletContext().getAttribute(ATTR_ANNOTATED_CLASSES);
        boolean found = false;
        Class<?> foundClassRef = null;
        Method foundMethodRef = null;

        if (o instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<Class<?>> annotated = (Set<Class<?>>) o;
            for (Class<?> cls : annotated) {
                try {
                    for (Method m : cls.getDeclaredMethods()) {
                        if (m.isAnnotationPresent(MethodeAnnotation.class)) {
                            MethodeAnnotation ma = m.getAnnotation(MethodeAnnotation.class);
                            String url = ma.value();
                            if (url != null && url.equals(path)) {
                                found = true;
                                foundClassRef = cls;
                                foundMethodRef = m;
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore problematic classes/methods
                }
                if (found) break;
            }
        }

        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><head><title>Test</title></head><body><h1>Check d'url </h1>");
            if (found && foundClassRef != null && foundMethodRef != null) {
                // Tenter d'invoquer la méthode trouvée
                try {
                    foundMethodRef.setAccessible(true);
                    Object target = null;
                    if (!Modifier.isStatic(foundMethodRef.getModifiers())) {
                        // créer une instance via constructeur sans argument
                        target = foundClassRef.getDeclaredConstructor().newInstance();
                    }

                    Object result = null;
                    if (foundMethodRef.getParameterCount() == 0) {
                        result = foundMethodRef.invoke(target);
                    } else {
                        // Pas de support des paramètres pour l'instant
                        // Vous pouvez étendre ici pour passer req/resp si souhaité
                    }

                    // Afficher le résultat si c'est une String
                    if (result instanceof String) {
                        out.println("<h2>Résultat</h2>");
                        out.println("<p>" + (String) result + "</p>");
                    } else {
                        // fallback: infos de la méthode
                        out.println("<h2>Route trouvée</h2>");
                        out.println("<p>Classe: " + foundClassRef.getName() + "</p>");
                        out.println("<p>Méthode: " + foundMethodRef.getName() + "</p>");
                    }
                } catch (Throwable invokeError) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.println("<h2>500 - Erreur invocation</h2>");
                    out.println("<pre>" + invokeError + "</pre>");
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.println("<h2>404 - Not found</h2>");
            }
            out.println("</body></html>");
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        RequestDispatcher defaultDispatcher = getServletContext().getNamedDispatcher("default");
        defaultDispatcher.forward(req, resp);

    }

}