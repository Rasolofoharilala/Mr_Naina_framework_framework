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
        List<RouteInfo> exactRoutes = new ArrayList<>();
        List<RouteInfo> dynamicRoutes = new ArrayList<>();
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
                            List<RouteInfo> routesForMethod = new ArrayList<>();
                            routesForMethod.add(new RouteInfo(cls, m, url, "ALL"));
                            if (m.isAnnotationPresent(annotation.GetMapping.class)) {
                                routesForMethod.add(new RouteInfo(cls, m, url, "GET"));
                            }
                            if (m.isAnnotationPresent(annotation.PostMapping.class)) {
                                routesForMethod.add(new RouteInfo(cls, m, url, "POST"));
                            }
                            for (RouteInfo route : routesForMethod) {
                                if (!route.urlPattern.contains("{")) {
                                    exactRoutes.add(route);
                                } else {
                                    dynamicRoutes.add(route);
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore problematic classes/methods
                }
            }
        }

        // D'abord, chercher une route exacte
        List<RouteInfo> matchingRoutes = new ArrayList<>();
        for (RouteInfo route : exactRoutes) {
            if (route.urlPattern.equals(path)) {
                matchingRoutes.add(route);
                urlParams = new HashMap<>();
            }
        }
        // Si aucune route exacte, chercher une route dynamique
        if (matchingRoutes.isEmpty()) {
            for (RouteInfo route : dynamicRoutes) {
                UrlMatcher matcher = new UrlMatcher(route.urlPattern);
                Map<String, String> params = matcher.extractParams(path);
                if (params != null) {
                    matchingRoutes.add(route);
                    urlParams = params;
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
                            // Si Map<String, Object> attendu, construire à partir des paramètres du formulaire
                            if (Map.class.isAssignableFrom(paramType)) {
                                Map<String, Object> paramMap = new HashMap<>();
                                // Ajoute tous les paramètres du formulaire (name/value)
                                Map<String, String[]> paramValues = req.getParameterMap();
                                for (Map.Entry<String, String[]> entry : paramValues.entrySet()) {
                                    String key = entry.getKey();
                                    String[] vals = entry.getValue();
                                    if (vals != null && vals.length == 1) {
                                        paramMap.put(key, vals[0]);
                                    } else {
                                        paramMap.put(key, vals);
                                    }
                                }
                                args[i] = paramMap;
                                continue;
                            }
                            if (List.class.isAssignableFrom(paramType) || paramType.isArray()) {
                                continue;
                            }
                            String paramName;
                            if (parameter.isAnnotationPresent(RequestParam.class)) {
                                RequestParam reqParam = parameter.getAnnotation(RequestParam.class);
                                paramName = reqParam.value();
                            } else {
                                paramName = parameter.getName();
                            }
                            String value = null;
                            if (urlParams != null && urlParams.containsKey(paramName)) {
                                value = urlParams.get(paramName);
                            }
                            if (value == null || value.isEmpty()) {
                                value = req.getParameter(paramName);
                            }
                            if (value == null || value.isEmpty()) {
                                throw new IllegalArgumentException("Paramètre obligatoire manquant: " + paramName);
                            }
                            try {
                                args[i] = buildObjectFromRequest(paramType, parameter, req, "");
                            } catch (Exception e) {
                                throw new IllegalArgumentException("Impossible de construire l'objet " + paramType.getSimpleName() + " : " + e.getMessage(), e);
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
    
    /**
     * Construit un objet complexe à partir des paramètres du formulaire (récursif, supporte héritage, @RequestParam, champs imbriqués)
     */
    private Object buildObjectFromRequest(Class<?> clazz, java.lang.reflect.Parameter parameter, HttpServletRequest req, String prefix) throws Exception {
        // Recherche le constructeur avec le plus de paramètres
        java.lang.reflect.Constructor<?>[] constructors = clazz.getConstructors();
        java.lang.reflect.Constructor<?> bestCtor = null;
        int maxParams = -1;
        for (java.lang.reflect.Constructor<?> ctor : constructors) {
            if (ctor.getParameterCount() > maxParams) {
                bestCtor = ctor;
                maxParams = ctor.getParameterCount();
            }
        }
        if (bestCtor == null) throw new IllegalArgumentException("Aucun constructeur public trouvé pour " + clazz.getName());
        java.lang.reflect.Parameter[] ctorParams = bestCtor.getParameters();
        Object[] args = new Object[ctorParams.length];
        for (int i = 0; i < ctorParams.length; i++) {
            java.lang.reflect.Parameter ctorParam = ctorParams[i];
            Class<?> paramType = ctorParam.getType();
            String paramName;
            if (ctorParam.isAnnotationPresent(annotation.RequestParam.class)) {
                paramName = ctorParam.getAnnotation(annotation.RequestParam.class).value();
            } else {
                paramName = ctorParam.getName();
            }
            String fullName = (prefix != null && !prefix.isEmpty()) ? (prefix + "." + paramName) : paramName;
            if (isSimpleType(paramType)) {
                String value = req.getParameter(fullName);
                if (value == null || value.isEmpty()) {
                    throw new IllegalArgumentException("Paramètre obligatoire manquant: " + fullName);
                }
                args[i] = convertParameter(value, paramType);
            } else {
                // Objet imbriqué
                args[i] = buildObjectFromRequest(paramType, ctorParam, req, fullName);
            }
        }
        return bestCtor.newInstance(args);
    }

    /**
     * Détermine si le type est simple (String, primitive, wrapper)
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                type == String.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Double.class ||
                type == Float.class ||
                type == Boolean.class ||
                type == Short.class ||
                type == Byte.class ||
                type == Character.class;
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