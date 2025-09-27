package com.uteq.SCLI.config;

import com.uteq.SCLI.dto.UserSession;
import com.uteq.SCLI.service.AuditJdbcPort;   // <-- puerto JDBC (abajo)
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionAuthInterceptor implements HandlerInterceptor {

    private final AuditJdbcPort auditPort;

    public SessionAuthInterceptor(AuditJdbcPort auditPort) {
        this.auditPort = auditPort;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String path = req.getRequestURI();

        // Rutas públicas: login, logout, error, estáticos
        if (path.startsWith("/login")
                || path.equals("/logout")
                || path.startsWith("/error")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/images/")
                || path.startsWith("/assets/")
                || path.startsWith("/webjars/")
                || path.equals("/favicon.ico")
                || path.equals("/LABU.png")) {
            return true;
        }

        HttpSession session = req.getSession(false);
        UserSession us = (session == null) ? null : (UserSession) session.getAttribute("userSession");

        if (us == null) {
            res.sendRedirect("/login?expired");
            return false;
        }

        // === Usuario autenticado: refrescar actividad y setear contexto en la conexión ===
        try {
            // Calcula IP real (considera proxy/reverso)
            String ip = realIp(req);
            String ua = req.getHeader("User-Agent");

            // 1) Refresca last_seen (audit.heartbeat) y
            // 2) Setea el contexto (app.set_audit_context) para los triggers/vistas
            auditPort.heartbeat(us, ip, ua);
        } catch (Exception e) {
            // No bloquear la navegación por un fallo de auditoría
            // Puedes loguearlo con tu logger si lo deseas
            // log.warn("Audit heartbeat failed", e);
        }

        return true; // autenticado: continuar
    }

    private String realIp(HttpServletRequest r){
        String xff = r.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String ip = r.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return r.getRemoteAddr();
    }
}


















/*package com.uteq.SCLI.config;

import com.uteq.SCLI.dto.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String path = req.getRequestURI();

        // Rutas públicas: login, logout, error, estáticos
        if (path.startsWith("/login")
                || path.equals("/logout")
                || path.startsWith("/error")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/assets/")
                || path.startsWith("/webjars/")) {
            return true;
        }

        HttpSession session = req.getSession(false);
        UserSession us = (session == null) ? null : (UserSession) session.getAttribute("userSession");

        if (us == null) {
            res.sendRedirect("/login?expired");
            return false;
        }
        return true; // autenticado: continuar
    }
}*/
