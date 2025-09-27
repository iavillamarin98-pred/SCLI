 package com.uteq.SCLI.config;
import jakarta.servlet.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import com.uteq.SCLI.dto.UserSession;
import com.uteq.SCLI.repository.DocenteRepository;

@Component
@RequiredArgsConstructor
public class PgAppContextFilter implements Filter {

  private final JdbcTemplate jdbc;
  private final UserSession userSession;
  private final DocenteRepository docenteRepo;

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws java.io.IOException, ServletException {

    try {
      Integer idPersona = userSession.getIdPersona();
      if (idPersona != null && "docente".equalsIgnoreCase(userSession.getNombreRol())) {
        Integer idDocente = docenteRepo.findIdByPersona(idPersona);
        if (idDocente != null) {
          jdbc.execute("select set_config('app.current_docente_id', '" + idDocente + "', true)");
        } else {
          // limpia si no hay mapeo
          jdbc.execute("select set_config('app.current_docente_id', '', true)");
        }
      } else {
        // no-docente: limpia
        jdbc.execute("select set_config('app.current_docente_id', '', true)");
      }
    } catch (Exception e) {
      // no rompas la request por esto
    }
    chain.doFilter(req, res);
  }
}
