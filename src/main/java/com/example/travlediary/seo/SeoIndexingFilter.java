package com.example.travlediary.seo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SeoIndexingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        boolean filteredSearch = "/travel-info".equals(request.getRequestURI())
                && request.getParameter("keyword") != null
                && !request.getParameter("keyword").isBlank();
        if (SeoRobotsPolicy.isNoindex(request.getRequestURI()) || filteredSearch
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setHeader("X-Robots-Tag", SeoRobotsPolicy.NOINDEX);
        }
        filterChain.doFilter(request, response);
    }
}
