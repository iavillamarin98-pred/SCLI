// src/main/java/com/uteq/SCLI/session/SessionTracker.java
package com.uteq.SCLI.session;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionTracker implements HttpSessionListener {

  private final Map<String, ActiveSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, HttpSession> httpSessions = new ConcurrentHashMap<>(); // ⭐

  // Llamar al hacer login correcto
  public void register(String sessionId, Integer userId, String username, String rol,
                       String ip, String userAgent) {
    sessions.put(sessionId, new ActiveSession(sessionId, userId, username, rol, ip, userAgent));
  }

  // ⭐ Llamar en login (y/o en el filtro) para tener la HttpSession disponible
  public void attach(HttpSession httpSession) {
    if (httpSession != null) {
      httpSessions.put(httpSession.getId(), httpSession);
    }
  }

  // Llamar periódicamente (filtro) o en cada request
  public void touch(String sessionId) {
    ActiveSession s = sessions.get(sessionId);
    if (s != null) s.touch();
  }

  // Llamar al hacer logout manual
  public void unregister(String sessionId) {
    sessions.remove(sessionId);
    httpSessions.remove(sessionId); // ⭐ limpia el cache de HttpSession
  }

  // Listar para el admin
  public Collection<ActiveSession> listAll() {
    return sessions.values();
  }

  // ⭐ Cerrar/kick: invalida la HttpSession si existe
    public void kick(String sessionId) {
    try {
      HttpSession hs = httpSessions.remove(sessionId);
      if (hs != null) hs.invalidate();
    } catch (IllegalStateException ignored) {
      // ya estaba invalidada/expirada
    } finally {
      sessions.remove(sessionId);
    }
  }

  // Se dispara cuando la sesión expira o se invalida desde el contenedor
  @Override
  public void sessionDestroyed(HttpSessionEvent se) {
    String id = se.getSession().getId();
    sessions.remove(id);
    httpSessions.remove(id); // ⭐
  }

  // Helpers (por si los necesitas en otros lugares)
  public static String ipFrom(jakarta.servlet.http.HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
  }
  public static String uaFrom(jakarta.servlet.http.HttpServletRequest req) {
    String ua = req.getHeader("User-Agent");
    return ua == null ? "-" : ua;
  }
}
