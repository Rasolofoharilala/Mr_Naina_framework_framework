package servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

public class FrontServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        String path = request.getRequestURI(); // ex: /home

        // Simple routing logic
            response.getWriter().println("<html><body><h1>URL actuel : "+path+"</h1></body></html>");
    }
}