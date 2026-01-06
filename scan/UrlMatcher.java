package scan;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire pour matcher des URLs avec des paramètres dynamiques
 * Exemple: /students/{id} match /students/123
 */
public class UrlMatcher {
    
    private String pattern;
    private Pattern regex;
    private boolean hasParams;
    
    public UrlMatcher(String pattern) {
        this.pattern = pattern;
        this.hasParams = pattern.contains("{");
        
        if (hasParams) {
            // Convertir /students/{id}/courses/{courseId} en regex
            // Échapper les caractères spéciaux sauf les accolades
            String regexPattern = pattern
                .replace("/", "\\/")
                .replaceAll("\\{[^}]+\\}", "([^/]+)");
            this.regex = Pattern.compile("^" + regexPattern + "$");
        }
    }
    
    /**
     * Vérifie si l'URL correspond au pattern (sans extraction)
     */
    public boolean matches(String url) {
        if (!hasParams) {
            return pattern.equals(url);
        }
        return regex.matcher(url).matches();
    }
    
    /**
     * Extrait les paramètres de l'URL si elle correspond au pattern
     * Retourne null si pas de correspondance
     */
    public Map<String, String> extractParams(String url) {
        if (!hasParams) {
            return pattern.equals(url) ? new HashMap<>() : null;
        }
        
        Matcher matcher = regex.matcher(url);
        if (!matcher.matches()) {
            return null;
        }
        
        // Extraire les noms des paramètres du pattern
        Map<String, String> params = new HashMap<>();
        Pattern paramNamePattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher paramNameMatcher = paramNamePattern.matcher(pattern);
        
        int groupIndex = 1;
        while (paramNameMatcher.find()) {
            String paramName = paramNameMatcher.group(1);
            String paramValue = matcher.group(groupIndex++);
            params.put(paramName, paramValue);
        }
        
        return params;
    }
    
    public boolean hasParams() {
        return hasParams;
    }
    
    public String getPattern() {
        return pattern;
    }
}
