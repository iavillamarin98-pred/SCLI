package com.uteq.SCLI.controller;

import com.uteq.SCLI.dto.UserSession;
import com.uteq.SCLI.exception.CredencialesInvalidasException;
import com.uteq.SCLI.service.AuthService;
import com.uteq.SCLI.session.SessionTracker;
import com.uteq.SCLI.service.AuditJdbcPort;   
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private AuthService authService;
    

     @Autowired
    private SessionTracker sessionTracker;

     @Autowired private AuditJdbcPort auditPort;

    @GetMapping("/login")
    public String mostrarLogin(HttpSession session) {
        // Si ya hay sesión, manda directo a su dashboard
        UserSession us = (UserSession) session.getAttribute("userSession");
        if (us != null && us.getNombreRol() != null) {

            // Rellenar atributos por si faltan (server restart, etc.)
            seedBasicSession(session, us);

            log.debug("Login existente, rol={}", us.getNombreRol());
            switch (us.getNombreRol()) {
                case "admin_master":
                case "admin":
                case "administrador":
                case "admin_piso":
                    return "redirect:/dashboard/admin";
                case "docente":
                    return "redirect:/dashboard/docente";
                case "estudiante":
                    return "redirect:/dashboard/estudiante";
                    // ✅ NUEVO
                 case "coordinador":
                    return "redirect:/dashboard/coordinador";
            }
        }
        return "login/login";
    }

     @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            try {
                // ⬇️  NEW: cierra sesión en BD si la app tiene sessionId
                UserSession us = (UserSession) session.getAttribute("userSession");
                if (us != null && us.getSessionId() != null) {
                    auditPort.endSession(us.getSessionId(), "logout");
                }
            } catch (Exception e) {
                log.warn("No se pudo cerrar la sesión en auditoría: {}", e.getMessage());
            }

            try { sessionTracker.unregister(session.getId()); } catch (Exception ignored) {}
            session.invalidate();
        }
        return "redirect:/login?logout";
    }

    @GetMapping("/whoami")
    public @ResponseBody UserSession whoami(HttpSession session) {
        return (UserSession) session.getAttribute("userSession");
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String nombreUsuario,
                                @RequestParam String clave,
                                HttpServletRequest request,
                                HttpSession session) {
        try {
            String ip = resolveClientIp(request);
            String ua = request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "";
            log.info("Intento de login user={} ip={}", nombreUsuario, ip);

            // usar overload con ip/ua
            UserSession sessionInfo = authService.autenticar(
                    nombreUsuario.trim(),
                    clave.trim(),
                    ip,
                    ua
            );

             // 2) Asegura que tengamos sessionId (si tu AuthService ya lo setea, perfecto)
            if (sessionInfo.getSessionId() == null || sessionInfo.getSessionId().isBlank()) {
                try {
                    // Fallback: pide a la BD el login auditado (devuelve session_id)
                    var r = auditPort.loginAudit(nombreUsuario.trim(), clave.trim(), ip, ua);
                    if (r.ok()) {
                        sessionInfo.setSessionId(r.sessionId());   // guarda UUID en la sesión
                        // Nota: el interceptor hará heartbeat y set_audit_context en cada request
                    } else {
                        // si aquí no es OK, mantenemos el flujo de tu AuthService
                        log.warn("fn_login_audit reportó ok=false para {}", nombreUsuario);
                    }
                } catch (Exception e) {
                    log.warn("Fallback fn_login_audit falló: {}", e.getMessage());
                }
            }

            // Guarda el objeto completo
            session.setAttribute("userSession", sessionInfo);

            // Llena TODOS los atributos que se usan en controllers/vistas
            seedBasicSession(session, sessionInfo);

             // ⭐ REGISTRAR EN EL TRACKER (aquí es la “magia”)
            try {
                sessionTracker.register(
                    session.getId(),
                    sessionInfo.getIdUsuario(),                  // ajusta si fuese Long
                    sessionInfo.getUsername(),
                    sessionInfo.getNombreRol(),
                    ip,
                    ua
                );
            } catch (Exception e) {
                log.warn("No se pudo registrar sesión activa: {}", e.getMessage());
            }

               // 2) Adjuntar la HttpSession para que /kick pueda invalidarla
            sessionTracker.attach(session);

            String rol = sessionInfo.getNombreRol();
            log.info("Login OK user={} rol={}", sessionInfo.getUsername(), rol);

            switch (rol == null ? "" : rol.trim().toLowerCase()) {
                case "admin_master":
                case "admin":
                case "administrador":
                case "admin_piso":
                    return "redirect:/dashboard/admin";
                case "docente":
                    return "redirect:/dashboard/docente";
                case "estudiante":
                    return "redirect:/dashboard/estudiante";
                 // ✅ NUEVO
                case "coordinador":
                    return "redirect:/dashboard/coordinador";
                default:
                    return "redirect:/login?error=rol";
            }
        } catch (CredencialesInvalidasException ex) {
            log.warn("Login inválido para user={}", nombreUsuario);
            return "redirect:/login?error=credenciales";
        }
    }

     private static String resolveClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String xr = req.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr.trim();
        return req.getRemoteAddr();
    }

    /**
     * Coloca en sesión los atributos que esperan DocenteProfileController y las vistas.
     * Usa SOLO getters existentes en tu UserSession: idUsuario, idPersona, username, nombreRol.
     */
    private static void seedBasicSession(HttpSession session, UserSession us) {
        // IDs (+ alias por compatibilidad)
        if (session.getAttribute("id_usuario") == null && us.getIdUsuario() != null)
            session.setAttribute("id_usuario", us.getIdUsuario());
        if (session.getAttribute("idUsuario") == null && us.getIdUsuario() != null)
            session.setAttribute("idUsuario", us.getIdUsuario());

        if (session.getAttribute("id_persona") == null && us.getIdPersona() != null)
            session.setAttribute("id_persona", us.getIdPersona());
        if (session.getAttribute("idPersona") == null && us.getIdPersona() != null)
            session.setAttribute("idPersona", us.getIdPersona());

        // username “técnico” (para fallbacks)
        if (session.getAttribute("username") == null && us.getUsername() != null)
            session.setAttribute("username", us.getUsername());

        // nombre que muestra la UI (si no tienes nombres/apellidos, usamos username)
        if (session.getAttribute("nombreUsuario") == null) {
            String display = (us.getUsername() != null && !us.getUsername().isBlank())
                    ? us.getUsername()
                    : "Usuario";
            session.setAttribute("nombreUsuario", display);
        }

        // rol
        if (session.getAttribute("rol") == null && us.getNombreRol() != null)
            session.setAttribute("rol", us.getNombreRol());
    }
}



/*package com.uteq.SCLI.controller;

import com.uteq.SCLI.dto.UserSession;
import com.uteq.SCLI.exception.CredencialesInvalidasException;
import com.uteq.SCLI.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private AuthService authService;

    @GetMapping("/login")
    public String mostrarLogin(HttpSession session) {
        // Si ya hay sesión, manda directo a su dashboard
        UserSession us = (UserSession) session.getAttribute("userSession");
        if (us != null && us.getNombreRol() != null) {
            switch (us.getNombreRol()) {
                case "admin_master":
                case "admin":
                case "administrador":
                    return "redirect:/dashboard/admin";
                case "admin_piso":
                    return "redirect:/dashboard/admin";
                case "docente":
                    return "redirect:/dashboard/docente";
                case "estudiante":
                    return "redirect:/dashboard/estudiante";
            }
        }
        return "login/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
    if (session != null) session.invalidate();
    return "redirect:/login?logout";
}

    @GetMapping("/whoami")
    public @ResponseBody UserSession whoami(HttpSession session) {
        return (UserSession) session.getAttribute("userSession");
    }

    @PostMapping("/login")
public String procesarLogin(@RequestParam String nombreUsuario,
                            @RequestParam String clave,
                            HttpSession session) {
    try {
        UserSession sessionInfo = authService.autenticar(nombreUsuario.trim(), clave.trim());

        session.setAttribute("userSession", sessionInfo);

        // 👇 Añade estas dos líneas:
      session.setAttribute("nombreUsuario", sessionInfo.getUsername());
        session.setAttribute("rol", sessionInfo.getNombreRol());

        String rol = sessionInfo.getNombreRol();
        switch (rol == null ? "" : rol.trim().toLowerCase()) {
            case "admin_master":
            case "admin":
            case "administrador":
                return "redirect:/dashboard/admin";
            case "admin_piso":
                return "redirect:/dashboard/admin";
            case "docente":
                return "redirect:/dashboard/docente";
            case "estudiante":
                return "redirect:/dashboard/estudiante";
            default:
                return "redirect:/login?error=rol";
        }
    } catch (CredencialesInvalidasException ex) {
        return "redirect:/login?error=credenciales";
    }
}
}*/
