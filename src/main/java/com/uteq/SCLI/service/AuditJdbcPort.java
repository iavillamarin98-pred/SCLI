package com.uteq.SCLI.service;

import com.uteq.SCLI.dto.UserSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditJdbcPort {
    private final JdbcTemplate jdbc;
    public AuditJdbcPort(JdbcTemplate jdbc){ this.jdbc = jdbc; }

    public record LoginResult(boolean ok, Integer idUsuario, Integer idPersona,
                              String nombreRol, String dbRole, String sessionId) {}

    public LoginResult loginAudit(String user, String pass, String ip, String ua){
        return jdbc.queryForObject(
            "select ok, id_usuario, id_persona, nombre_rol, db_role, session_id " +
            "from app.fn_login_audit(?, ?, CAST(? as inet), ?)",
            (rs,i)-> new LoginResult(
                rs.getBoolean("ok"),
                (Integer) rs.getObject("id_usuario"),
                (Integer) rs.getObject("id_persona"),
                rs.getString("nombre_rol"),
                rs.getString("db_role"),
                rs.getString("session_id")
            ),
            user, pass, ip, ua
        );
    }

    public void heartbeat(UserSession us, String ip, String ua){
        if (us==null || us.getSessionId()==null) return;
        jdbc.queryForList(
            "select audit.heartbeat(CAST(? as uuid), ?, ?, ?, ?, ?, CAST(? as inet), ?)",
            us.getSessionId(), us.getIdUsuario(), us.getIdPersona(), us.getUsername(),
            us.getNombreRol(), us.getDbRole(), ip, ua
        );
        jdbc.queryForList(
            "select app.set_audit_context(?, ?, ?, ?, ?, CAST(? as inet), ?)",
            us.getIdUsuario(), us.getUsername(), us.getNombreRol(), us.getDbRole(),
            us.getIdPersona(), ip, ua
        );
    }

    public void endSession(String sessionId, String how){
        jdbc.queryForList("select audit.end_session(CAST(? as uuid), ?)", sessionId, how);
    }
}
