package com.uteq.SCLI.service;

import com.uteq.SCLI.dto.CrearSolicitudRequest;
import com.uteq.SCLI.dto.SolicitudItemDTO;
import com.uteq.SCLI.model.SolicitudAsignacion;
import com.uteq.SCLI.repository.SolicitudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SolicitudReservaService {

    private final SolicitudRepository solicitudRepository;
    private final JdbcTemplate jdbc;

    public SolicitudReservaService(SolicitudRepository solicitudRepository, JdbcTemplate jdbc) {
        this.solicitudRepository = solicitudRepository;
        this.jdbc = jdbc;
    }

    /** Fija la GUC para RLS y evita errores usando queryForObject. */
    private void setDocenteGUC(Integer idDocente) {
        Objects.requireNonNull(idDocente, "idDocente no puede ser null");
        jdbc.queryForObject(
            "SELECT set_config('app.current_docente_id', ?, true)",
            String.class,
            String.valueOf(idDocente)
        );
    }

    /** Intenta hallar el admin de piso asociado al horario (o un fallback razonable). */
    private Integer resolverAdminPisoPorHorario(Integer idHorario) {
        try {
            return jdbc.queryForObject(
                """
                SELECT ap.id_admin_piso
                  FROM public.asignacion_laboratorio al
                  JOIN public.laboratorio l  ON l.id_laboratorio = al.id_laboratorio
                  JOIN public.piso p         ON p.id_piso        = l.id_piso
                  JOIN public.administradorpiso ap ON ap.id_piso = p.id_piso
                 WHERE al.id_horario = ?
                 ORDER BY ap.id_admin_piso
                 LIMIT 1
                """,
                Integer.class, idHorario
            );
        } catch (Exception ignore) {}

        try {
            return jdbc.queryForObject(
                """
                SELECT ap.id_admin_piso
                  FROM public.administradorpiso ap
                  JOIN public.laboratorio l ON l.id_piso = ap.id_piso
                 WHERE EXISTS (SELECT 1 FROM public.horario h WHERE h.id_horario = ?)
                 ORDER BY ap.id_admin_piso
                 LIMIT 1
                """,
                Integer.class, idHorario
            );
        } catch (Exception ignore) {}

        return null;
    }

    @Transactional(readOnly = true)
    public List<SolicitudItemDTO> misSolicitudes(Integer idDocente) {
        setDocenteGUC(idDocente);

        List<Object[]> rows = solicitudRepository.findMisSolicitudes(idDocente);
        List<SolicitudItemDTO> out = new ArrayList<>();
        for (Object[] r : rows) {
            SolicitudItemDTO dto = new SolicitudItemDTO();
            int i = 0;
            dto.setIdSolicitud((Integer) r[i++]);
            dto.setEstado((String) r[i++]);
            dto.setEstadoRedireccion((String) r[i++]);
            dto.setFechaSolicitud((r[i] != null) ? ((java.sql.Date) r[i]).toLocalDate() : null); i++;
            dto.setIdHorario((Integer) r[i++]);
            dto.setDiaSemana((String) r[i++]);
            dto.setHoraInicio(((java.sql.Time) r[i++]).toLocalTime());
            dto.setHoraFin(((java.sql.Time) r[i++]).toLocalTime());
            dto.setJornada((String) r[i++]);
            dto.setMateria((String) r[i++]);
            dto.setTipoSolicitud((String) r[i++]);
            out.add(dto);
        }
        return out;
    }

    @Transactional
    public Integer crearSolicitud(Integer idDocente, CrearSolicitudRequest req) {
        setDocenteGUC(idDocente);

        if (req.getIdHorario() == null) throw new IllegalArgumentException("idHorario es requerido");
        if (req.getMateria() == null || req.getMateria().isBlank()) throw new IllegalArgumentException("materia es requerida");
        if (req.getTipoSolicitud() == null || req.getTipoSolicitud().isBlank()) req.setTipoSolicitud("Nueva");

        Integer idAdminPiso = req.getIdAdminPiso();
        if (idAdminPiso == null) {
            idAdminPiso = resolverAdminPisoPorHorario(req.getIdHorario());
            if (idAdminPiso == null) {
                throw new IllegalStateException("No se pudo determinar el Administrador de Piso para el horario " + req.getIdHorario());
            }
        }

        SolicitudAsignacion s = new SolicitudAsignacion();
        s.setIdDocente(idDocente);
        s.setIdHorario(req.getIdHorario());
        s.setMateria(req.getMateria());
        s.setTipoSolicitud(req.getTipoSolicitud());
        s.setEstado("Pendiente");
        s.setEstadoRedireccion("Sin redirección");
        s.setFechaSolicitud(LocalDate.now());
        s.setIdAdminPiso(idAdminPiso);

        solicitudRepository.save(s);
        return s.getIdSolicitud();
    }

    /* ===== Aceptar/Rechazar propuesta (Docente) ===== */

    /** Acepta la propuesta del admin: Revisión -> Pendiente + marca redirección. */
    @Transactional
public int docenteAceptarPropuesta(Integer idDocente, Integer idSolicitud) {
    setDocenteGUC(idDocente);
    String sql = """
        UPDATE public.solicitudasignacion
           SET estado = 'Pendiente',
               estado_redireccion = 'Prop. aceptada'  -- <= <= corto (<= 30)
         WHERE id_solicitud = ? AND id_docente = ? AND estado = 'Revisión'
    """;
    return jdbc.update(sql, idSolicitud, idDocente);
}

    /** Rechaza la propuesta del admin: vuelve a Pendiente y deja constancia. */
   @Transactional
public int docenteRechazarPropuesta(Integer idDocente, Integer idSolicitud) {
    setDocenteGUC(idDocente);
    String sql = """
        UPDATE public.solicitudasignacion
           SET estado = 'Pendiente',
               estado_redireccion = 'Prop. rechazada' -- <= <= corto (<= 30)
         WHERE id_solicitud = ? AND id_docente = ? AND estado = 'Revisión'
    """;
    return jdbc.update(sql, idSolicitud, idDocente);
}
}
