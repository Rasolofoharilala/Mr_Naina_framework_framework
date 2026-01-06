package servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.ServerException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import modelview.ModelView;
import annotation.MethodeAnnotation;
import annotation.RequestParam;
import java.util.Set;
import scan.ClassPathScanner;
import scan.UrlMatcher;
 

 

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
        String checkParam = req.getParameter("check");
        String path = (checkParam != null && !checkParam.isEmpty()) ? checkParam : req.getRequestURI().substring(req.getContextPath().length());

        Object o = getServletContext().getAttribute(ATTR_ANNOTATED_CLASSES);
        List<RouteInfo> matchingRoutes = new ArrayList<>();
        Map<String, String> urlParams = null;

        if (o instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<Class<?>> annotated = (Set<Class<?>>) o;
            for (Class<?> cls : annotated) {
                try {
                    for (Method m : cls.getDeclaredMethods()) {
                        if (m.isAnnotationPresent(annotation.MethodeAnnotation.class)) {
                            annotation.MethodeAnnotation ma = m.getAnnotation(annotation.MethodeAnnotation.class);
                            String url = ma.value();
                            // Par défaut, route ALL
                            List<RouteInfo> routesForMethod = new ArrayList<>();
                            routesForMethod.add(new RouteInfo(cls, m, url, "ALL"));
                            // Si GetMapping, ajoute GET
                            if (m.isAnnotationPresent(annotation.GetMapping.class)) {
                                routesForMethod.add(new RouteInfo(cls, m, url, "GET"));
                            }
                            // Si PostMapping, ajoute POST
                            if (m.isAnnotationPresent(annotation.PostMapping.class)) {
                                routesForMethod.add(new RouteInfo(cls, m, url, "POST"));
                            }
                            // Ajoute toutes les routes pour cette méthode
                            for (RouteInfo route : routesForMethod) {
                                UrlMatcher matcher = new UrlMatcher(route.urlPattern);
                                Map<String, String> params = matcher.extractParams(path);
                                if (params != null) {
                                    matchingRoutes.add(new RouteInfo(route.cls, route.method, route.urlPattern, route.httpMethod));
                                    urlParams = params;
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore problematic classes/methods
                }
            }
        }

        // Sélectionne la bonne méthode selon le type de requête
        RouteInfo selectedRoute = null;
        String reqMethod = req.getMethod();
        // Priorité : GET/POST > ALL
        for (RouteInfo route : matchingRoutes) {
            if (route.httpMethod.equalsIgnoreCase(reqMethod)) {
                selectedRoute = route;
                break;
            }
        }
        if (selectedRoute == null) {
            for (RouteInfo route : matchingRoutes) {
                if ("ALL".equals(route.httpMethod)) {
                    selectedRoute = route;
                    break;
                }
            }
        }

        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><head><title>Test</title></head><body><h1>Check d'url </h1>");
            if (selectedRoute != null) {
                try {
                    Method foundMethodRef = selectedRoute.method;
                    Class<?> foundClassRef = selectedRoute.cls;
                    foundMethodRef.setAccessible(true);
                    Object target = null;
                    if (!Modifier.isStatic(foundMethodRef.getModifiers())) {
                        target = foundClassRef.getDeclaredConstructor().newInstance();
                    }

                    Object result = null;
                    if (foundMethodRef.getParameterCount() == 0) {
                        result = foundMethodRef.invoke(target);
                    } else {
                        Class<?>[] paramTypes = foundMethodRef.getParameterTypes();
                        java.lang.reflect.Parameter[] parameters = foundMethodRef.getParameters();
                        Object[] args = new Object[paramTypes.length];
                        for (int i = 0; i < paramTypes.length; i++) {
                            Class<?> paramType = paramTypes[i];
                            java.lang.reflect.Parameter parameter = parameters[i];
                            
                            // Ignorer Map, List, tableaux - traités séparément
                            if (Map.class.isAssignableFrom(paramType) || 
                                List.class.isAssignableFrom(paramType) || 
                                paramType.isArray()) {
                                if (Map.class.isAssignableFrom(paramType)) {
                                    args[i] = urlParams;
                                }
                                continue;
                            }
                            
                            // Déterminer le nom du paramètre : @RequestParam.value() ou nom du paramètre
                            String paramName;
                            if (parameter.isAnnotationPresent(RequestParam.class)) {
                                RequestParam reqParam = parameter.getAnnotation(RequestParam.class);
                                paramName = reqParam.value();
                            } else {
                                paramName = parameter.getName();
                            }
                            
                            // Chercher la valeur : d'abord dans urlParams, puis dans request params
                            String value = null;
                            if (urlParams != null && urlParams.containsKey(paramName)) {
                                // Paramètre extrait de l'URL (ex: /etudiant/{id} -> id=5)
                                value = urlParams.get(paramName);
                            }
                            
                            if (value == null || value.isEmpty()) {
                                // Sinon, chercher dans les paramètres de la requête (POST/GET)
                                value = req.getParameter(paramName);
                            }
                            if (value == null || value.isEmpty()) {
                                throw new IllegalArgumentException("Paramètre obligatoire manquant: " + paramName);
                            }
                            try {
                                args[i] = convertParameter(value, paramType);
                            } catch (Exception e) {
                                throw new IllegalArgumentException("Impossible de convertir le paramètre '" + paramName + "' (valeur: '" + value + "') en type " + paramType.getSimpleName(), e);
                            }
                        }
                        result = foundMethodRef.invoke(target, args);
                    }
                    if (result instanceof ModelView) {
                        ModelView modelView = (ModelView) result;
                        String view = modelView.getView();
                        if (view != null && !view.isEmpty()) {
                            Map<String, Object> data = modelView.getData();
                            if (data != null) {
                                for (Map.Entry<String, Object> entry : data.entrySet()) {
                                    req.setAttribute(entry.getKey(), entry.getValue());
                                }
                            }
                            RequestDispatcher rd = req.getRequestDispatcher(view);
                            rd.forward(req, resp);
                            return;
                        }
                    } else if (result instanceof String) {
                        out.println("<h2>Résultat</h2>");
                        out.println("<p>" + (String) result + "</p>");
                    } else {
                        out.println("<h2>Route trouvée</h2>");
                        out.println("<p>Classe: " + foundClassRef.getName() + "</p>");
                        out.println("<p>Méthode: " + foundMethodRef.getName() + "</p>");
                    }
                } catch (IllegalArgumentException argError) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("<h2>400 - Paramètre invalide</h2>");
                    out.println("<p>" + argError.getMessage() + "</p>");
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
    
    /**
     * Convertit une valeur String vers le type cible
     */
    private Object convertParameter(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(value);
        } else if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(value);
        }
        throw new IllegalArgumentException("Type non supporté: " + targetType.getName());
    }
    
    // Classe interne pour stocker les informations de route
    private static class RouteInfo {
        Class<?> cls;
        Method method;
        String urlPattern;
        String httpMethod; // "ALL", "GET", "POST"

        RouteInfo(Class<?> cls, Method method, String urlPattern, String httpMethod) {
            this.cls = cls;
            this.method = method;
            this.urlPattern = urlPattern;
            this.httpMethod = httpMethod;
        }
    }

}